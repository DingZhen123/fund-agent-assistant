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
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class QdrantVectorStore {
    private final String baseUrl;
    private final String apiKey;
    private final String collection;
    private final OkHttpClient httpClient;

    public QdrantVectorStore(@Value("${agent.rag.qdrant.base-url:http://101.200.131.156:6333}") String baseUrl,
                             @Value("${agent.rag.qdrant.api-key:}") String apiKey,
                             @Value("${agent.rag.qdrant.collection:fund_agent_knowledge}") String collection,
                             @Value("${agent.rag.timeout-seconds:60}") int timeoutSeconds) {
        this.baseUrl = trimTrailingSlash(baseUrl);
        this.apiKey = apiKey;
        this.collection = collection;
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(timeoutSeconds, TimeUnit.SECONDS)
                .readTimeout(timeoutSeconds * 2L, TimeUnit.SECONDS)
                .protocols(java.util.List.of(okhttp3.Protocol.HTTP_1_1))
                .build();
    }

    public void upsert(List<KnowledgeChunk> chunks, List<List<Double>> vectors) {
        if (chunks == null || chunks.isEmpty()) {
            return;
        }
        if (vectors == null || vectors.size() != chunks.size()) {
            throw new IllegalArgumentException("chunks和vectors数量不一致");
        }

        JSONArray points = new JSONArray();
        for (int i = 0; i < chunks.size(); i++) {
            KnowledgeChunk chunk = chunks.get(i);
            JSONObject point = new JSONObject();
            point.put("id", stablePointId(chunk.getChunkId()));
            point.put("vector", vectors.get(i));
            point.put("payload", payload(chunk));
            points.add(point);
        }

        JSONObject body = new JSONObject();
        body.put("points", points);
        executeJson("PUT", "/collections/" + collection + "/points?wait=true", body);
    }

    public List<KnowledgeSearchHit> search(List<Double> queryVector, String knowledgeBaseId, int topK) {
        JSONObject body = new JSONObject();
        body.put("vector", queryVector);
        body.put("limit", topK);
        body.put("with_payload", true);

        if (knowledgeBaseId != null && !knowledgeBaseId.isBlank()) {
            JSONObject match = new JSONObject();
            match.put("value", knowledgeBaseId);
            JSONObject condition = new JSONObject();
            condition.put("key", "knowledgeBaseId");
            condition.put("match", match);
            JSONObject filter = new JSONObject();
            filter.put("must", List.of(condition));
            body.put("filter", filter);
        }

        JSONObject response = executeJson("POST", "/collections/" + collection + "/points/search", body);
        JSONArray result = response.getJSONArray("result");
        List<KnowledgeSearchHit> hits = new ArrayList<>();
        if (result == null) {
            return hits;
        }
        for (int i = 0; i < result.size(); i++) {
            JSONObject item = result.getJSONObject(i);
            JSONObject payload = item.getJSONObject("payload");
            if (payload == null) {
                continue;
            }
            hits.add(KnowledgeSearchHit.builder()
                    .chunkId(payload.getString("chunkId"))
                    .docId(payload.getString("docId"))
                    .title(payload.getString("title"))
                    .sectionPath(payload.getString("sectionPath"))
                    .content(payload.getString("content"))
                    .source(payload.getString("source"))
                    .score(item.getDoubleValue("score"))
                    .build());
        }
        return hits;
    }

    private JSONObject payload(KnowledgeChunk chunk) {
        JSONObject payload = new JSONObject();
        payload.put("chunkId", chunk.getChunkId());
        payload.put("docId", chunk.getDocId());
        payload.put("knowledgeBaseId", chunk.getKnowledgeBaseId());
        payload.put("title", chunk.getTitle());
        payload.put("sectionPath", chunk.getSectionPath());
        payload.put("content", chunk.getContent());
        payload.put("source", chunk.getSource());
        payload.put("chunkIndex", chunk.getChunkIndex());
        payload.put("nodeType", "PARAGRAPH");
        return payload;
    }

    private JSONObject executeJson(String method, String path, JSONObject body) {
        RequestBody requestBody = RequestBody.create(body.toJSONString(), MediaType.parse("application/json"));
        Request.Builder builder = new Request.Builder()
                .url(baseUrl + path)
                .header("Content-Type", "application/json");
        if (apiKey != null && !apiKey.isBlank()) {
            builder.header("api-key", apiKey);
        }
        Request request = switch (method) {
            case "PUT" -> builder.put(requestBody).build();
            case "POST" -> builder.post(requestBody).build();
            default -> throw new IllegalArgumentException("Unsupported method: " + method);
        };

        try (Response response = httpClient.newCall(request).execute()) {
            String responseBody = response.body() != null ? response.body().string() : "";
            if (!response.isSuccessful()) {
                log.error("Qdrant error: method={}, path={}, code={}, body={}",
                        method, path, response.code(), responseBody);
                throw new RuntimeException("Qdrant API error: " + response.code());
            }
            return JSON.parseObject(responseBody);
        } catch (IOException e) {
            log.error("Qdrant request failed: method={}, path={}", method, path, e);
            throw new RuntimeException("Qdrant request failed: " + e.getMessage(), e);
        }
    }

    private String stablePointId(String chunkId) {
        return UUID.nameUUIDFromBytes(chunkId.getBytes(StandardCharsets.UTF_8)).toString();
    }

    private String trimTrailingSlash(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }
}
