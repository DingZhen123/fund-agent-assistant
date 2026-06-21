package com.fundagent.server.controller;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.fundagent.server.longterm.LongTermMemory;
import com.fundagent.server.longterm.LongTermMemoryRememberResult;
import com.fundagent.server.longterm.LongTermMemoryService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/debug/long-term-memory")
@CrossOrigin("*")
public class LongTermMemoryDebugController {
    private final LongTermMemoryService longTermMemoryService;

    public LongTermMemoryDebugController(LongTermMemoryService longTermMemoryService) {
        this.longTermMemoryService = longTermMemoryService;
    }

    @PostMapping("/remember")
    public LongTermMemoryRememberResult remember(@RequestBody JSONObject request) {
        String userId = request.getString("userId");
        List<Map<String, String>> messages = parseMessages(request);
        Map<String, Object> metadata = parseMetadata(request);
        log.info("debug long-term memory remember: userId={}, messageCount={}", userId, messages.size());
        return longTermMemoryService.remember(userId, messages, metadata);
    }

    @PostMapping("/search")
    public List<LongTermMemory> search(@RequestBody JSONObject request) {
        String userId = request.getString("userId");
        String query = request.getString("query");
        Integer topK = request.getInteger("topK");
        log.info("debug long-term memory search: userId={}, query={}, topK={}", userId, query, topK);
        return longTermMemoryService.search(userId, query, topK != null ? topK : 5);
    }

    private List<Map<String, String>> parseMessages(JSONObject request) {
        JSONArray array = request.getJSONArray("messages");
        List<Map<String, String>> messages = new ArrayList<>();
        if (array != null && !array.isEmpty()) {
            for (int i = 0; i < array.size(); i++) {
                JSONObject item = array.getJSONObject(i);
                messages.add(Map.of(
                        "role", item.getString("role"),
                        "content", item.getString("content")));
            }
            return messages;
        }

        String memory = request.getString("memory");
        if (memory != null && !memory.isBlank()) {
            messages.add(Map.of("role", "user", "content", memory));
        }
        return messages;
    }

    private Map<String, Object> parseMetadata(JSONObject request) {
        JSONObject metadata = request.getJSONObject("metadata");
        return metadata != null ? new HashMap<>(metadata) : Map.of();
    }
}
