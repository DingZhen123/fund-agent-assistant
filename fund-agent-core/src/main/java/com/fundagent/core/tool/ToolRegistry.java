package com.fundagent.core.tool;

import com.fundagent.core.tool.provider.ToolProvider;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

public class ToolRegistry {
    private final List<ToolProvider> providers;
    private final ToolSchemaValidator schemaValidator = new ToolSchemaValidator();

    public ToolRegistry(List<ToolProvider> providers) {
        this.providers = List.copyOf(providers);
    }

    public ToolDefinition getTool(String name) {
        return findProvider(name)
                .map(provider -> provider.resolveDefinition(name))
                .orElse(null);
    }

    public boolean hasTool(String name) {
        return findProvider(name).isPresent();
    }

    public ToolResult execute(String toolName, Map<String, Object> args) {
        Optional<ToolProvider> provider = findProvider(toolName);
        if (provider.isEmpty()) {
            return ToolResult.unknownTool("未知工具: " + toolName);
        }

        ToolDefinition def = provider.get().resolveDefinition(toolName);
        if (def == null) {
            return ToolResult.unknownTool("未知工具: " + toolName);
        }
        if (!def.isEnabled()) {
            return ToolResult.businessError("TOOL_DISABLED", "工具已禁用: " + toolName);
        }

        ToolParamValidationResult validation = schemaValidator.validate(def, args);
        if (!validation.isValid()) {
            return ToolResult.validationError(validation.getErrorCode(), validation.getMessage());
        }

        return provider.get().execute(toolName, args);
    }

    public String getToolsDescription() {
        return getAllTools().stream()
                .filter(ToolDefinition::isEnabled)
                .map(t -> "- " + t.getName() + ": " + t.getDescription()
                        + (t.getDomain() != null ? ", domain: " + t.getDomain() : "")
                        + (t.getIntents() != null && !t.getIntents().isEmpty()
                        ? ", intents: " + String.join("/", t.getIntents()) : "")
                        + (t.getRiskLevel() != null ? ", risk: " + t.getRiskLevel() : "")
                        + ", 参数: " + String.join(", ", t.getParams()))
                .collect(Collectors.joining("\n"));
    }

    public List<ToolDefinition> getAllTools() {
        return providers.stream()
                .flatMap(provider -> provider.listDefinitions().stream())
                .sorted(Comparator.comparing(ToolDefinition::getName))
                .collect(Collectors.toList());
    }

    private Optional<ToolProvider> findProvider(String toolName) {
        return providers.stream()
                .filter(provider -> provider.supports(toolName))
                .findFirst();
    }
}
