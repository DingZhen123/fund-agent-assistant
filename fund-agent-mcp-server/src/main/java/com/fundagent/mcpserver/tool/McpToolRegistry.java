package com.fundagent.mcpserver.tool;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Component
public class McpToolRegistry {
    private final Map<String, McpRegisteredTool> tools = new LinkedHashMap<>();

    public void register(McpRegisteredTool tool) {
        if (tool == null || tool.getDefinition() == null || tool.getDefinition().getName() == null) {
            throw new IllegalArgumentException("MCP tool definition name is required");
        }
        tools.put(tool.getDefinition().getName(), tool);
        log.info("MCP tool registered: name={}, description={}",
                tool.getDefinition().getName(), tool.getDefinition().getDescription());
    }

    public List<McpToolDefinition> listTools() {
        return tools.values().stream()
                .map(McpRegisteredTool::getDefinition)
                .sorted(Comparator.comparing(McpToolDefinition::getName))
                .toList();
    }

    public Optional<McpRegisteredTool> getTool(String name) {
        return Optional.ofNullable(tools.get(name));
    }

    public McpToolCallResult call(String name, Map<String, Object> args) {
        McpRegisteredTool tool = tools.get(name);
        if (tool == null) {
            return McpToolCallResult.error("TOOL_NOT_FOUND", "工具不存在: " + name);
        }
        return tool.getExecutor().execute(args != null ? args : Map.of());
    }

    public List<String> toolNames() {
        return new ArrayList<>(tools.keySet());
    }
}
