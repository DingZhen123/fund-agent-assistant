package com.fundagent.mcpserver.tool;

import java.util.Map;

public interface McpToolExecutor {
    McpToolCallResult execute(Map<String, Object> args);
}
