package com.fundagent.core.tool.provider;

import com.fundagent.core.tool.ToolDefinition;
import com.fundagent.core.tool.ToolResult;

import java.util.List;
import java.util.Map;

public interface ToolProvider {
    String providerType();

    boolean supports(String toolName);

    ToolDefinition resolveDefinition(String toolName);

    List<ToolDefinition> listDefinitions();

    ToolResult execute(String toolName, Map<String, Object> args);
}
