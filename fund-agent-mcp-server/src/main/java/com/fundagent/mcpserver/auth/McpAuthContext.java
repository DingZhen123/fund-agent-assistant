package com.fundagent.mcpserver.auth;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class McpAuthContext {
    private String apiKey;
    private String remoteAddress;
}
