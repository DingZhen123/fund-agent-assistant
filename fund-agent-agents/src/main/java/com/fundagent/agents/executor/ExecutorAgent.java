package com.fundagent.agents.executor;

import com.alibaba.fastjson2.JSON;
import com.fundagent.core.agent.Agent;
import com.fundagent.core.agent.AgentDefinition;
import com.fundagent.core.agent.AgentEntry;
import com.fundagent.core.memory.Memory;
import com.fundagent.core.post.Post;
import com.fundagent.core.tool.ToolRegistry;
import com.fundagent.core.tool.ToolResult;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;

@Slf4j
@AgentDefinition(name = "Executor", description = "工具执行器，根据Planner指定的工具和参数直接执行并返回结果")
public class ExecutorAgent extends Agent {
    private final int maxRetries;

    public ExecutorAgent(AgentEntry entry, ToolRegistry toolRegistry, int maxRetries) {
        super(entry);
        this.toolRegistry = toolRegistry;
        this.maxRetries = maxRetries;
    }

    @Override
    public Post reply(Memory memory, Post incoming) {
        String toolName = getAttachment(incoming, "tool_name");
        String paramsJson = getAttachment(incoming, "tool_params");

        if (toolName == null || toolName.isEmpty()) {
            log.warn("Executor received post without tool_name");
            return Post.create("Executor", "Planner", "错误: Planner未指定要调用的工具");
        }

        Map<String, Object> params = Map.of();
        if (paramsJson != null && !paramsJson.isEmpty()) {
            try {
                params = JSON.parseObject(paramsJson);
            } catch (Exception e) {
                log.warn("Failed to parse tool_params: {}", paramsJson);
            }
        }

        int retry = 0;
        while (retry <= maxRetries) {
            try {
                log.info("Executor calling tool: {}, params: {}", toolName, params);
                ToolResult result = toolRegistry.execute(toolName, params);

                if (result.isSuccess()) {
                    Post post = Post.create("Executor", "Planner", result.getData().toString());
                    post.addAttachment("tool_result", result.getData().toString());
                    post.addAttachment("tool_name", toolName);
                    return post;
                }

                retry++;
                log.warn("Tool failed (retry {}/{}): {}", retry, maxRetries, result.getError());
                if (retry > maxRetries) {
                    return Post.create("Executor", "Planner",
                            "工具执行失败: " + result.getError());
                }
            } catch (Exception e) {
                retry++;
                log.error("Executor error (retry {}/{}): {}", retry, maxRetries, e.getMessage());
                if (retry > maxRetries) {
                    return Post.create("Executor", "Planner",
                            "执行异常: " + e.getMessage());
                }
            }
        }
        return Post.create("Executor", "Planner", "已达最大重试次数");
    }

    private String getAttachment(Post post, String type) {
        if (post.getAttachments() == null) return null;
        return post.getAttachments().stream()
                .filter(a -> type.equals(a.getType()))
                .map(Post.Attachment::getContent)
                .findFirst()
                .orElse(null);
    }
}
