package com.fundagent.core.tool;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class ToolRegistry {
    private final Map<String, ToolDefinition> definitions = new ConcurrentHashMap<>();
    private final Map<String, Object> instances = new ConcurrentHashMap<>();

    public void register(ToolDefinition definition, Object instance) {
        definitions.put(definition.getName(), definition);
        instances.put(definition.getName(), instance);
    }

    public ToolDefinition getTool(String name) {
        return definitions.get(name);
    }

    public boolean hasTool(String name) {
        return definitions.containsKey(name);
    }

    public ToolResult execute(String toolName, Map<String, Object> args) {
        ToolDefinition def = definitions.get(toolName);
        if (def == null) {
            return ToolResult.error("未知工具: " + toolName);
        }

        Object instance = instances.get(toolName);
        if (instance == null) {
            return ToolResult.error("工具实例未注册: " + toolName);
        }

        try {
            Object result = def.invoke(instance, args);
            if (result instanceof ToolResult) {
                return (ToolResult) result;
            }
            return ToolResult.success(result);
        } catch (Exception e) {
            return ToolResult.error("工具执行异常: " + e.getMessage());
        }
    }

    public String getToolsDescription() {
        return definitions.values().stream()
                .map(t -> "- " + t.getName() + ": " + t.getDescription()
                        + ", 参数: " + String.join(", ", t.getParams()))
                .collect(Collectors.joining("\n"));
    }

    public List<ToolDefinition> getAllTools() {
        return List.copyOf(definitions.values());
    }
}
