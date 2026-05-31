package com.fundagent.core.tool;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class ToolRegistry {
    private final Map<String, ToolDefinition> definitions = new ConcurrentHashMap<>();
    private final Map<String, Object> instances = new ConcurrentHashMap<>();
    private final ToolSchemaValidator schemaValidator = new ToolSchemaValidator();

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
            return ToolResult.unknownTool("未知工具: " + toolName);
        }

        Object instance = instances.get(toolName);
        if (instance == null) {
            return ToolResult.systemError("TOOL_INSTANCE_NOT_REGISTERED", "工具实例未注册: " + toolName, false);
        }
        if (!def.isEnabled()) {
            return ToolResult.businessError("TOOL_DISABLED", "工具已禁用: " + toolName);
        }

        ToolParamValidationResult validation = schemaValidator.validate(def, args);
        if (!validation.isValid()) {
            return ToolResult.validationError(validation.getErrorCode(), validation.getMessage());
        }

        try {
            Object result = def.invoke(instance, args);
            if (result instanceof ToolResult) {
                return (ToolResult) result;
            }
            return ToolResult.success(result);
        } catch (Exception e) {
            return ToolResult.systemError("TOOL_EXECUTION_EXCEPTION", "工具执行异常: " + e.getMessage(), true);
        }
    }

    public String getToolsDescription() {
        return definitions.values().stream()
                .filter(ToolDefinition::isEnabled)
                .map(t -> "- " + t.getName() + ": " + t.getDescription()
                        + (t.getDomain() != null ? ", domain: " + t.getDomain() : "")
                        + (t.getRiskLevel() != null ? ", risk: " + t.getRiskLevel() : "")
                        + ", 参数: " + String.join(", ", t.getParams()))
                .collect(Collectors.joining("\n"));
    }

    public List<ToolDefinition> getAllTools() {
        return List.copyOf(definitions.values());
    }
}
