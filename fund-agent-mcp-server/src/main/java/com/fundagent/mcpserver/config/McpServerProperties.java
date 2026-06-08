package com.fundagent.mcpserver.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "mcp")
public class McpServerProperties {
    private Auth auth = new Auth();

    @Data
    public static class Auth {
        private boolean enabled;
        private String apiKey;
    }
}
