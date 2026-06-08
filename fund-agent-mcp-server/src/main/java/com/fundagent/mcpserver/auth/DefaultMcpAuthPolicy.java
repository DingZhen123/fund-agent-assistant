package com.fundagent.mcpserver.auth;

import com.fundagent.mcpserver.config.McpServerProperties;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class DefaultMcpAuthPolicy implements McpAuthPolicy {
    private final McpServerProperties properties;

    public DefaultMcpAuthPolicy(McpServerProperties properties) {
        this.properties = properties;
    }

    @Override
    public boolean canListTools(McpAuthContext context) {
        return isAllowed(context);
    }

    @Override
    public boolean canCallTool(McpAuthContext context, String toolName, Map<String, Object> args) {
        return isAllowed(context);
    }

    private boolean isAllowed(McpAuthContext context) {
        if (!properties.getAuth().isEnabled()) {
            return true;
        }
        String expected = properties.getAuth().getApiKey();
        return expected != null && !expected.isBlank()
                && context != null
                && expected.equals(context.getApiKey());
    }
}
