package com.fundagent.server.tool.mcp;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "agent.tools.mcp")
public class McpToolProperties {
    private boolean enabled;
    private String baseUrl;
    private String apiKey;
    private int timeoutMs = 5000;
}
