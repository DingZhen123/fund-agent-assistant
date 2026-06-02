package com.fundagent.server.tool.provider;

import com.alibaba.fastjson2.JSON;
import com.fundagent.core.tool.Tool;
import com.fundagent.core.tool.ToolDefinition;
import com.fundagent.core.tool.ToolResult;
import com.fundagent.core.tool.provider.ToolProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class LocalToolProvider implements ToolProvider {
    private final Map<String, ToolDefinition> definitions = new ConcurrentHashMap<>();
    private final Map<String, Object> instances = new ConcurrentHashMap<>();

    public LocalToolProvider(ConfigurableListableBeanFactory beanFactory) {
        loadLocalTools(beanFactory);
    }

    @Override
    public String providerType() {
        return "LOCAL";
    }

    @Override
    public boolean supports(String toolName) {
        return definitions.containsKey(toolName);
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
        ToolDefinition definition = definitions.get(toolName);
        if (definition == null) {
            return ToolResult.unknownTool("未知本地工具: " + toolName);
        }
        Object instance = instances.get(toolName);
        if (instance == null) {
            return ToolResult.systemError("TOOL_INSTANCE_NOT_REGISTERED", "工具实例未注册: " + toolName, false);
        }
        try {
            Object result = definition.invoke(instance, args);
            if (result instanceof ToolResult toolResult) {
                return toolResult;
            }
            return ToolResult.success(result);
        } catch (Exception e) {
            return ToolResult.systemError("TOOL_EXECUTION_EXCEPTION", "工具执行异常: " + e.getMessage(), true);
        }
    }

    private void loadLocalTools(ConfigurableListableBeanFactory beanFactory) {
        Map<String, Map<String, Object>> metadata = loadToolMetadata();
        for (String beanName : beanFactory.getBeanDefinitionNames()) {
            Class<?> beanType = beanFactory.getType(beanName, false);
            if (beanType == null || !hasToolMethod(beanType)) {
                continue;
            }
            Object bean = beanFactory.getBean(beanName);
            for (Method method : beanType.getDeclaredMethods()) {
                Tool tool = method.getAnnotation(Tool.class);
                if (tool == null) {
                    continue;
                }
                Map<String, Object> toolMetadata = metadata.getOrDefault(tool.name(), Map.of());
                ToolDefinition definition = ToolDefinition.builder()
                        .name(tool.name())
                        .description(tool.description())
                        .domain(asString(toolMetadata.get("domain")))
                        .version(asString(toolMetadata.get("version")))
                        .riskLevel(asString(toolMetadata.get("riskLevel")))
                        .intents(asStringList(toolMetadata.get("intents")))
                        .providerType(asString(toolMetadata.getOrDefault("providerType", providerType())))
                        .requiresAuth(asBoolean(toolMetadata.get("requiresAuth"), false))
                        .requiresConfirmation(asBoolean(toolMetadata.get("requiresConfirmation"), false))
                        .enabled(asBoolean(toolMetadata.get("enabled"), true))
                        .params(Arrays.asList(tool.params()))
                        .paramsSchemaJson(toSchemaJson(toolMetadata.get("paramsSchema")))
                        .metadata(new HashMap<>(toolMetadata))
                        .method(method)
                        .build();
                definitions.put(definition.getName(), definition);
                instances.put(definition.getName(), bean);
                log.info("Local tool loaded: {}", definition.getName());
            }
        }
    }

    private boolean hasToolMethod(Class<?> beanType) {
        for (Method method : beanType.getDeclaredMethods()) {
            if (method.getAnnotation(Tool.class) != null) {
                return true;
            }
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Map<String, Object>> loadToolMetadata() {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream("tools.yaml")) {
            if (is == null) {
                log.info("tools.yaml not found, use @Tool annotation only");
                return Map.of();
            }
            Map<String, Object> root = new Yaml().load(is);
            Object tools = root != null ? root.get("tools") : null;
            if (tools instanceof Map<?, ?> map) {
                return (Map<String, Map<String, Object>>) map;
            }
        } catch (Exception e) {
            log.warn("Failed to load tools.yaml, use @Tool annotation only", e);
        }
        return Map.of();
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
