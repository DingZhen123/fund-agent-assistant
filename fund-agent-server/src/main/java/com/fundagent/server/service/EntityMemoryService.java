package com.fundagent.server.service;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.fundagent.core.graph.Observation;
import com.fundagent.core.memory.Memory;
import com.fundagent.core.post.Post;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;

@Slf4j
@Service
public class EntityMemoryService {

    public void rememberObservation(Memory memory, Observation observation) {
        if (observation == null || !observation.isSuccess()) return;
        rememberToolData(memory, observation.getData());
    }

    public void rememberToolResultPost(Memory memory, Post post) {
        if (post == null || post.getAttachments() == null) return;
        for (Post.Attachment attachment : post.getAttachments()) {
            if (!"tool_result".equals(attachment.getType())) continue;
            try {
                rememberToolData(memory, JSON.parseObject(attachment.getContent()));
            } catch (Exception e) {
                log.debug("Skip unparseable tool_result attachment: {}", attachment.getContent());
            }
        }
    }

    @SuppressWarnings("unchecked")
    public void rememberToolData(Memory memory, Object data) {
        if (memory == null || memory.getSharedMemory() == null || data == null) return;

        JSONObject json;
        if (data instanceof JSONObject object) {
            json = object;
        } else if (data instanceof Map<?, ?> map) {
            json = new JSONObject((Map<String, Object>) map);
        } else {
            try {
                json = JSON.parseObject(JSON.toJSONString(data));
            } catch (Exception e) {
                return;
            }
        }

        rememberIfPresent(memory, json, "noteCode", "latest.noteCode");
        rememberIfPresent(memory, json, "status", "latest.paymentStatus");
        rememberIfPresent(memory, json, "hasReceipt", "latest.hasReceipt");
        rememberIfPresent(memory, json, "amount", "latest.amount");
        rememberIfPresent(memory, json, "payTime", "latest.payTime");
        rememberIfPresent(memory, json, "payer", "latest.payer");
        rememberIfPresent(memory, json, "payee", "latest.payee");
        rememberIfPresent(memory, json, "fileName", "latest.receiptFileName");
        rememberIfPresent(memory, json, "fileUrl", "latest.receiptFileUrl");
    }

    private void rememberIfPresent(Memory memory, JSONObject json, String sourceKey, String memoryKey) {
        Object value = json.get(sourceKey);
        if (value != null && !String.valueOf(value).isEmpty()) {
            memory.getSharedMemory().put(memoryKey, value);
        }
    }
}
