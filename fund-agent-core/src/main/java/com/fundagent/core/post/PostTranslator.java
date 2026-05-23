package com.fundagent.core.post;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.fundagent.core.agent.AgentRegistry;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class PostTranslator {

    private final AgentRegistry agentRegistry;
    private final int maxRetries;

    public PostTranslator(AgentRegistry agentRegistry, int maxRetries) {
        this.agentRegistry = agentRegistry;
        this.maxRetries = maxRetries;
    }

    public Post parse(String agentName, String rawText) {
        int retryCount = 0;

        while (retryCount <= maxRetries) {
            try {
                String jsonStr = extractJson(rawText);
                JSONObject json = JSON.parseObject(jsonStr);

                String sendTo = json.getString("send_to");
                String message = json.getString("message");

                if (sendTo == null || sendTo.isEmpty()) {
                    throw new IllegalArgumentException("send_to is required");
                }
                if (!agentRegistry.hasAgent(sendTo) && !"User".equals(sendTo)) {
                    throw new IllegalArgumentException("Unknown agent: " + sendTo);
                }

                Post post = Post.create(agentName, sendTo, message != null ? message : "");
                post.setTimestamp(System.currentTimeMillis());

                if (json.containsKey("plan_reasoning")) {
                    post.addAttachment("plan", json.getString("plan_reasoning"));
                }
                if (json.containsKey("tool_name") && json.getString("tool_name") != null) {
                    post.addAttachment("tool_name", json.getString("tool_name"));
                }
                if (json.containsKey("tool_params")) {
                    post.addAttachment("tool_params", json.getJSONObject("tool_params").toString());
                }

                return post;
            } catch (Exception e) {
                retryCount++;
                log.warn("PostTranslator parse error (retry {}/{}): {}",
                        retryCount, maxRetries, e.getMessage());
                if (retryCount > maxRetries) {
                    return Post.create(agentName, "User", "系统内部错误：无法解析Agent响应。");
                }
            }
        }
        return Post.create(agentName, "User", "系统内部错误。");
    }

    private String extractJson(String text) {
        String trimmed = text.trim();
        int start = trimmed.indexOf('{');
        int end = trimmed.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return trimmed.substring(start, end + 1);
        }
        return trimmed;
    }
}
