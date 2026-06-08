package com.fundagent.server.tool.mcp;

import com.alibaba.fastjson2.JSON;
import com.fundagent.core.tool.ToolDefinition;
import com.fundagent.core.tool.ToolResult;
import com.fundagent.core.tool.provider.ToolProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class McpToolProvider implements ToolProvider {
    private static final String PROVIDER_TYPE = "MCP";

    private final McpToolClient mcpToolClient;
    private final McpToolProperties properties;
    private final Map<String, ToolDefinition> definitions = new ConcurrentHashMap<>();

    public McpToolProvider(McpToolClient mcpToolClient, McpToolProperties properties) {
        this.mcpToolClient = mcpToolClient;
        this.properties = properties;
        loadMcpTools();
    }

    @Override
    public String providerType() {
        return PROVIDER_TYPE;
    }

    @Override
    public boolean supports(String toolName) {
        return properties.isEnabled() && definitions.containsKey(toolName);
    }

    @Override
    public ToolDefinition resolveDefinition(String toolName) {
        return definitions.get(toolName);
    }

    @Override
    public List<ToolDefinition> listDefinitions() {
        return List.copyOf(definitions.values());
    }

    @Override
    public ToolResult execute(String toolName, Map<String, Object> args) {
        if (!supports(toolName)) {
            return ToolResult.unknownTool("未知MCP工具: " + toolName);
        }
        log.info("MCP tool execute: toolName={}, args={}", toolName, args);
        return mcpToolClient.callTool(toolName, args);
    }

    @SuppressWarnings("unchecked")
    private void loadMcpTools() {
        if (!properties.isEnabled()) {
            log.info("McpToolProvider disabled");
            return;
        }
        try (InputStream is = getClass().getClassLoader().getResourceAsStream("tools.yaml")) {
            if (is == null) {
                log.info("tools.yaml not found, no MCP tools loaded");
                return;
            }
            Map<String, Object> root = new Yaml().load(is);
            Object tools = root != null ? root.get("tools") : null;
            if (!(tools instanceof Map<?, ?> toolMap)) {
                return;
            }
            for (Map.Entry<?, ?> entry : toolMap.entrySet()) {
                String name = String.valueOf(entry.getKey());
                if (entry.getValue() instanceof Map<?, ?> metadata) {
                    loadMcpTool(name, (Map<String, Object>) metadata);
                }
            }
            log.info("McpToolProvider loaded tools={}", definitions.keySet());
        } catch (Exception e) {
            log.warn("Failed to load MCP tools from tools.yaml", e);
        }
    }

    private void loadMcpTool(String name, Map<String, Object> metadata) {
        String providerType = asString(metadata.get("providerType"));
        if (!PROVIDER_TYPE.equalsIgnoreCase(providerType)) {
            return;
        }
        boolean enabled = asBoolean(metadata.get("enabled"), true);
        ToolDefinition definition = ToolDefinition.builder()
                .name(name)
                .description(asString(metadata.getOrDefault("description", name)))
                .domain(asString(metadata.get("domain")))
                .version(asString(metadata.get("version")))
                .riskLevel(asString(metadata.get("riskLevel")))
                .intents(asStringList(metadata.get("intents")))
                .providerType(PROVIDER_TYPE)
                .requiresAuth(asBoolean(metadata.get("requiresAuth"), false))
                .requiresConfirmation(asBoolean(metadata.get("requiresConfirmation"), false))
                .enabled(enabled)
                .params(resolveParams(metadata.get("paramsSchema")))
                .paramsSchemaJson(toSchemaJson(metadata.get("paramsSchema")))
                .metadata(new HashMap<>(metadata))
                .build();
        definitions.put(definition.getName(), definition);
        log.info("MCP tool loaded: name={}, enabled={}", definition.getName(), definition.isEnabled());
    }

    @SuppressWarnings("unchecked")
    private List<String> resolveParams(Object schema) {
        if (schema instanceof Map<?, ?> map) {
            Object required = map.get("required");
            if (required instanceof List<?> list) {
                return list.stream().map(String::valueOf).toList();
            }
            Object properties = map.get("properties");
            if (properties instanceof Map<?, ?> propertiesMap) {
                return propertiesMap.keySet().stream().map(String::valueOf).toList();
            }
        }
        return List.of();
    }

    private String toSchemaJson(Object schema) {
        return schema != null ? JSON.toJSONString(schema) : null;
    }

    private String asString(Object value) {
        return value != null ? String.valueOf(value) : null;
    }

    private List<String> asStringList(Object value) {
        if (value instanceof List<?> list) {
            return list.stream().map(String::valueOf).toList();
        }
        if (value == null) {
            return List.of();
        }
        return List.of(String.valueOf(value));
    }

    private boolean asBoolean(Object value, boolean defaultValue) {
        return value != null ? Boolean.parseBoolean(String.valueOf(value)) : defaultValue;
    }
}
