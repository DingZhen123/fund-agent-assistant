package com.fundagent.mcpserver.auth;

import java.util.Map;

public interface McpAuthPolicy {
    boolean canListTools(McpAuthContext context);

    boolean canCallTool(McpAuthContext context, String toolName, Map<String, Object> args);
}
