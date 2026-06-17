package com.fundagent.server.rag;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import lombok.extern.slf4j.Slf4j;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class OpenAIEmbeddingService implements EmbeddingService {
    private final String apiBase;
    private final String apiKey;
    private final String model;
    private final OkHttpClient httpClient;

    public OpenAIEmbeddingService(@Value("${agent.llm.api-base}") String apiBase,
                                  @Value("${agent.llm.api-key}") String apiKey,
                                  @Value("${agent.rag.embedding-model:text-embedding-3-small}") String model,
                                  @Value("${agent.rag.timeout-seconds:60}") int timeoutSeconds) {
        this.apiBase = apiBase;
        this.apiKey = apiKey;
        this.model = model;
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(timeoutSeconds, TimeUnit.SECONDS)
                .readTimeout(timeoutSeconds * 2L, TimeUnit.SECONDS)
                .protocols(java.util.List.of(okhttp3.Protocol.HTTP_1_1))
                .build();
    }

    @Override
    public List<Double> embed(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }

        JSONObject body = new JSONObject();
        body.put("model", model);
        body.put("input", text);

        Request request = new Request.Builder()
                .url(apiBase + "/embeddings")
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .post(RequestBody.create(body.toJSONString(), MediaType.parse("application/json")))
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            String responseBody = response.body() != null ? response.body().string() : "";
            if (!response.isSuccessful()) {
                log.error("Embedding API error: code={}, body={}", response.code(), responseBody);
                throw new RuntimeException("Embedding API error: " + response.code());
            }
            JSONObject json = JSON.parseObject(responseBody);
            JSONArray embedding = json.getJSONArray("data").getJSONObject(0).getJSONArray("embedding");
            List<Double> vector = new ArrayList<>(embedding.size());
            for (int i = 0; i < embedding.size(); i++) {
                vector.add(embedding.getDouble(i));
            }
            return vector;
        } catch (IOException e) {
            log.error("Embedding request failed", e);
            throw new RuntimeException("Embedding request failed: " + e.getMessage(), e);
        }
    }
}
