package com.fundagent.server.capability;

import com.fundagent.core.capability.CapabilityCatalogProvider;
import com.fundagent.core.capability.CapabilityDefinition;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class LocalCapabilityCatalogProvider implements CapabilityCatalogProvider {

    @Override
    public String providerType() {
        return "LOCAL";
    }

    @Override
    public List<CapabilityDefinition> loadCapabilities() {
        Map<String, Map<String, Object>> metadata = loadCapabilityMetadata();
        List<CapabilityDefinition> capabilities = metadata.entrySet().stream()
                .map(entry -> toCapability(entry.getKey(), entry.getValue()))
                .toList();
        log.info("LocalCapabilityCatalogProvider loaded capabilities={}", capabilities.size());
        return capabilities;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Map<String, Object>> loadCapabilityMetadata() {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream("capabilities.yaml")) {
            if (is == null) {
                log.info("capabilities.yaml not found");
                return Map.of();
            }
            Map<String, Object> root = new Yaml().load(is);
            Object capabilities = root != null ? root.get("capabilities") : null;
            if (capabilities instanceof Map<?, ?> map) {
                return (Map<String, Map<String, Object>>) map;
            }
        } catch (Exception e) {
            log.warn("Failed to load capabilities.yaml", e);
        }
        return Map.of();
    }

    private CapabilityDefinition toCapability(String name, Map<String, Object> metadata) {
        return CapabilityDefinition.builder()
                .name(name)
                .nodeType(asString(metadata.get("nodeType")))
                .domain(asString(metadata.get("domain")))
                .intents(asStringList(metadata.get("intents")))
                .description(asString(metadata.get("description")))
                .bindableTools(asStringList(metadata.get("bindableTools")))
                .enabled(asBoolean(metadata.get("enabled"), true))
                .metadata(new HashMap<>(metadata))
                .build();
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
