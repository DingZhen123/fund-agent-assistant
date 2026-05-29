package com.fundagent.core.llm;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.fundagent.common.model.Message;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

@Slf4j
public class OpenAIService implements LLMService {

    private final String apiBase;
    private final String apiKey;
    private final String model;
    private final double temperature;
    private final int maxTokens;
    private final OkHttpClient httpClient;

    public OpenAIService(LLMConfig config) {
        this.apiBase = config.getApiBase();
        this.apiKey = config.getApiKey();
        this.model = config.getModel();
        this.temperature = config.getTemperature();
        this.maxTokens = config.getMaxTokens();
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(config.getTimeoutSeconds(), TimeUnit.SECONDS)
                .readTimeout(config.getTimeoutSeconds() * 2L, TimeUnit.SECONDS)
                .protocols(java.util.List.of(okhttp3.Protocol.HTTP_1_1))
                .build();
    }

    @Override
    public String chat(String systemPrompt, List<Message> history, String currentMessage) {
        String body = buildBody(systemPrompt, history, currentMessage, false);
        Request request = buildRequest(body);
        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                String err = response.body() != null ? response.body().string() : "";
                log.error("LLM error: {} {}", response.code(), err);
                throw new RuntimeException("LLM API error: " + response.code());
            }
            JSONObject json = JSON.parseObject(response.body().string());
            return json.getJSONArray("choices").getJSONObject(0)
                    .getJSONObject("message").getString("content");
        } catch (IOException e) {
            log.error("LLM request failed", e);
            throw new RuntimeException("LLM request failed: " + e.getMessage(), e);
        }
    }

    @Override
    public String chatStream(String systemPrompt, List<Message> history,
                              String currentMessage, Consumer<String> onToken) {
        String body = buildBody(systemPrompt, history, currentMessage, true);
        Request request = buildRequest(body);

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                String err = response.body() != null ? response.body().string() : "";
                log.error("LLM stream error: {} {}", response.code(), err);
                throw new RuntimeException("LLM stream error: " + response.code());
            }

            StringBuilder full = new StringBuilder();
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(response.body().byteStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isEmpty() || !line.startsWith("data: ")) continue;
                String data = line.substring(6);
                if ("[DONE]".equals(data)) break;
                try {
                    JSONObject json = JSON.parseObject(data);
                    JSONArray choices = json.getJSONArray("choices");
                    if (choices != null && !choices.isEmpty()) {
                        JSONObject delta = choices.getJSONObject(0).getJSONObject("delta");
                        if (delta != null) {
                            String content = delta.getString("content");
                            if (content != null) {
                                full.append(content);
                                onToken.accept(content);
                            }
                        }
                    }
                } catch (Exception e) {
                    log.debug("Skip unparseable SSE line: {}", data);
                }
            }
            return full.toString();
        } catch (IOException e) {
            log.error("LLM stream failed", e);
            throw new RuntimeException("LLM stream failed: " + e.getMessage(), e);
        }
    }

    private String buildBody(String systemPrompt, List<Message> history,
                              String currentMessage, boolean stream) {
        JSONArray messages = new JSONArray();
        if (systemPrompt != null && !systemPrompt.isEmpty()) {
            JSONObject sys = new JSONObject();
            sys.put("role", "system");
            sys.put("content", systemPrompt);
            messages.add(sys);
        }
        if (history != null) {
            for (Message msg : history) {
                JSONObject m = new JSONObject();
                m.put("role", msg.getRole());
                m.put("content", msg.getContent());
                messages.add(m);
            }
        }
        JSONObject user = new JSONObject();
        user.put("role", "user");
        user.put("content", currentMessage);
        messages.add(user);

        JSONObject body = new JSONObject();
        body.put("model", model);
        body.put("messages", messages);
        body.put("temperature", temperature);
        body.put("max_tokens", maxTokens);
        body.put("stream", stream);
        return body.toJSONString();
    }

    private Request buildRequest(String body) {
        return new Request.Builder()
                .url(apiBase + "/chat/completions")
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .post(RequestBody.create(body, MediaType.parse("application/json")))
                .build();
    }
}
