package com.fundagent.server.longterm;

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
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class Mem0LongTermMemoryService implements LongTermMemoryService {
    private final boolean enabled;
    private final String apiBase;
    private final String apiKey;
    private final String provider;
    private final OkHttpClient httpClient;

    public Mem0LongTermMemoryService(
            @Value("${agent.memory.long-term.enabled:false}") boolean enabled,
            @Value("${agent.memory.long-term.provider:mem0}") String provider,
            @Value("${agent.memory.long-term.mem0.api-base:https://api.mem0.ai}") String apiBase,
            @Value("${agent.memory.long-term.mem0.api-key:}") String apiKey,
            @Value("${agent.memory.long-term.mem0.timeout-seconds:30}") int timeoutSeconds) {
        this.enabled = enabled;
        this.provider = provider;
        this.apiBase = trimTrailingSlash(apiBase);
        this.apiKey = apiKey;
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(timeoutSeconds, TimeUnit.SECONDS)
                .readTimeout(timeoutSeconds * 2L, TimeUnit.SECONDS)
                .protocols(java.util.List.of(okhttp3.Protocol.HTTP_1_1))
                .build();
    }

    @Override
    public List<LongTermMemory> search(String userId, String query, int topK) {
        if (!isAvailable() || isBlank(userId) || isBlank(query)) {
            log.info("Mem0 search skipped: enabled={}, hasApiKey={}, userIdPresent={}, queryPresent={}",
                    enabled, apiKey != null && !apiKey.isBlank(), !isBlank(userId), !isBlank(query));
            return List.of();
        }

        int safeTopK = Math.max(1, Math.min(20, topK));
        log.info("Mem0 search request: userId={}, query={}, topK={}", userId, query, safeTopK);

        JSONObject body = new JSONObject();
        body.put("query", query);
        body.put("top_k", safeTopK);

        JSONObject filters = new JSONObject();
        filters.put("user_id", userId);
        body.put("filters", filters);

        JSONObject response = executeJson("POST", "/v1/memories/search/", body);
        JSONArray results = extractResults(response);
        log.info("Mem0 search response: userId={}, resultCount={}, responseKeys={}",
                userId, results.size(), response.keySet());
        List<LongTermMemory> memories = new ArrayList<>();
        for (int i = 0; i < results.size(); i++) {
            JSONObject item = results.getJSONObject(i);
            memories.add(LongTermMemory.builder()
                    .id(firstString(item, "id", "memory_id"))
                    .memory(firstString(item, "memory", "text", "content"))
                    .score(firstDouble(item, "score", "similarity"))
                    .metadata(item.getJSONObject("metadata"))
                    .build());
        }
        return memories;
    }

    @Override
    public LongTermMemoryRememberResult remember(String userId, List<Map<String, String>> messages,
                                                 Map<String, Object> metadata) {
        if (!isAvailable()) {
            log.info("Mem0 remember skipped: enabled={}, hasApiKey={}",
                    enabled, apiKey != null && !apiKey.isBlank());
            return LongTermMemoryRememberResult.builder()
                    .success(false)
                    .provider(provider)
                    .message("长期记忆未启用或MEM0_API_KEY为空")
                    .raw(Map.of())
                    .build();
        }
        if (isBlank(userId) || messages == null || messages.isEmpty()) {
            log.info("Mem0 remember skipped: userIdPresent={}, messageCount={}",
                    !isBlank(userId), messages != null ? messages.size() : 0);
            return LongTermMemoryRememberResult.builder()
                    .success(false)
                    .provider(provider)
                    .message("userId和messages不能为空")
                    .raw(Map.of())
                    .build();
        }

        log.info("Mem0 remember request: userId={}, messageCount={}, metadataKeys={}",
                userId, messages.size(), metadata != null ? metadata.keySet() : List.of());

        JSONObject body = new JSONObject();
        body.put("messages", messages);
        body.put("user_id", userId);
        if (metadata != null && !metadata.isEmpty()) {
            body.put("metadata", metadata);
        }

        JSONObject response = executeJson("POST", "/v1/memories/", body);
        log.info("Mem0 remember response: userId={}, responseKeys={}", userId, response.keySet());
        return LongTermMemoryRememberResult.builder()
                .success(true)
                .provider(provider)
                .message("记忆写入请求已提交")
                .raw(response)
                .build();
    }

    private boolean isAvailable() {
        return enabled && apiKey != null && !apiKey.isBlank();
    }

    private JSONObject executeJson(String method, String path, JSONObject body) {
        RequestBody requestBody = RequestBody.create(body.toJSONString(), MediaType.parse("application/json"));
        Request.Builder builder = new Request.Builder()
                .url(apiBase + path)
                .header("Content-Type", "application/json")
                .header("Authorization", "Token " + apiKey);

        Request request = switch (method) {
            case "POST" -> builder.post(requestBody).build();
            default -> throw new IllegalArgumentException("Unsupported method: " + method);
        };

        try (Response response = httpClient.newCall(request).execute()) {
            String responseBody = response.body() != null ? response.body().string() : "";
            if (!response.isSuccessful()) {
                log.warn("Mem0 API error: method={}, path={}, code={}, body={}",
                        method, path, response.code(), responseBody);
                throw new RuntimeException("Mem0 API error: " + response.code());
            }
            return parseResponse(responseBody);
        } catch (IOException e) {
            log.warn("Mem0 request failed: method={}, path={}", method, path, e);
            throw new RuntimeException("Mem0 request failed: " + e.getMessage(), e);
        }
    }

    private JSONObject parseResponse(String responseBody) {
        if (responseBody == null || responseBody.isBlank()) {
            return new JSONObject();
        }
        String trimmed = responseBody.trim();
        if (trimmed.startsWith("[")) {
            JSONObject wrapper = new JSONObject();
            wrapper.put("data", JSON.parseArray(trimmed));
            return wrapper;
        }
        return JSON.parseObject(trimmed);
    }

    private JSONArray extractResults(JSONObject response) {
        JSONArray results = response.getJSONArray("results");
        if (results != null) {
            return results;
        }
        results = response.getJSONArray("memories");
        if (results != null) {
            return results;
        }
        results = response.getJSONArray("data");
        return results != null ? results : new JSONArray();
    }

    private String firstString(JSONObject item, String... keys) {
        for (String key : keys) {
            String value = item.getString(key);
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    private double firstDouble(JSONObject item, String... keys) {
        for (String key : keys) {
            if (item.containsKey(key)) {
                return item.getDoubleValue(key);
            }
        }
        return 0.0D;
    }

    private String trimTrailingSlash(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
