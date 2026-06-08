package com.fundagent.mcpserver.tool;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class McpRegisteredTool {
    private McpToolDefinition definition;
    private McpToolExecutor executor;
}
