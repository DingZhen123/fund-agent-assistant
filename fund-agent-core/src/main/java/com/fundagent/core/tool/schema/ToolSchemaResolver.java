package com.fundagent.core.tool.schema;

import com.fundagent.core.tool.ToolDefinition;
import com.fundagent.core.tool.ToolRegistry;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public class ToolSchemaResolver {
    private final ToolRegistry toolRegistry;

    public ToolSchemaResolver(ToolRegistry toolRegistry) {
        this.toolRegistry = toolRegistry;
    }

    public List<ToolDefinition> resolve(List<String> toolNames) {
        if (toolNames == null || toolNames.isEmpty()) {
            return List.of();
        }
        Set<String> distinctNames = new LinkedHashSet<>(toolNames);
        return distinctNames.stream()
                .map(toolRegistry::getTool)
                .filter(Objects::nonNull)
                .filter(ToolDefinition::isEnabled)
                .toList();
    }
}
