package com.fundagent.server.tool.catalog;

import com.fundagent.core.tool.Tool;
import com.fundagent.core.tool.catalog.ToolCatalogProvider;
import com.fundagent.core.tool.catalog.ToolMetadata;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class LocalToolCatalogProvider implements ToolCatalogProvider {
    private final ConfigurableListableBeanFactory beanFactory;

    public LocalToolCatalogProvider(ConfigurableListableBeanFactory beanFactory) {
        this.beanFactory = beanFactory;
    }

    @Override
    public String providerType() {
        return "LOCAL";
    }

    @Override
    public List<ToolMetadata> loadTools() {
        Map<String, Map<String, Object>> metadata = loadToolMetadata();
        List<ToolMetadata> catalogTools = new ArrayList<>();
        for (String beanName : beanFactory.getBeanDefinitionNames()) {
            Class<?> beanType = beanFactory.getType(beanName, false);
            if (beanType == null || !hasToolMethod(beanType)) {
                continue;
            }
            for (Method method : beanType.getDeclaredMethods()) {
                Tool tool = method.getAnnotation(Tool.class);
                if (tool == null) {
                    continue;
                }
                Map<String, Object> toolMetadata = metadata.getOrDefault(tool.name(), Map.of());
                catalogTools.add(ToolMetadata.builder()
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
                        .metadata(toCatalogMetadata(toolMetadata))
                        .build());
            }
        }
        log.info("LocalToolCatalogProvider loaded tools={}", catalogTools.size());
        return catalogTools;
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

    private Map<String, Object> toCatalogMetadata(Map<String, Object> toolMetadata) {
        Map<String, Object> catalogMetadata = new HashMap<>(toolMetadata);
        catalogMetadata.remove("paramsSchema");
        return catalogMetadata;
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
