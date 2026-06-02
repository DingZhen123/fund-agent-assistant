package com.fundagent.core.tool.catalog;

import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

public class ToolCatalog {
    private final Map<String, ToolMetadata> tools;

    public ToolCatalog(Collection<ToolMetadata> metadata) {
        this.tools = metadata.stream()
                .sorted(Comparator.comparing(ToolMetadata::getName))
                .collect(Collectors.toMap(
                        ToolMetadata::getName,
                        tool -> tool,
                        (left, right) -> right,
                        LinkedHashMap::new));
    }

    public static ToolCatalog fromProviders(Collection<ToolCatalogProvider> providers) {
        return new ToolCatalog(providers.stream()
                .flatMap(provider -> provider.loadTools().stream())
                .toList());
    }

    public Optional<ToolMetadata> getTool(String name) {
        return Optional.ofNullable(tools.get(name));
    }

    public List<ToolMetadata> getAllTools() {
        return List.copyOf(tools.values());
    }

    public List<ToolMetadata> getEnabledTools() {
        return tools.values().stream()
                .filter(ToolMetadata::isEnabled)
                .toList();
    }

    public List<ToolMetadata> findByDomain(String domain) {
        if (isBlank(domain)) {
            return List.of();
        }
        String normalized = normalize(domain);
        return getEnabledTools().stream()
                .filter(tool -> normalized.equals(normalize(tool.getDomain())))
                .toList();
    }

    public List<ToolMetadata> findByIntent(String intent) {
        if (isBlank(intent)) {
            return List.of();
        }
        String normalized = normalize(intent);
        return getEnabledTools().stream()
                .filter(tool -> tool.getIntents() != null
                        && tool.getIntents().stream().anyMatch(item -> normalized.equals(normalize(item))))
                .toList();
    }

    public List<ToolMetadata> findByProviderType(String providerType) {
        if (isBlank(providerType)) {
            return List.of();
        }
        String normalized = normalize(providerType);
        return getEnabledTools().stream()
                .filter(tool -> normalized.equals(normalize(tool.getProviderType())))
                .toList();
    }

    public List<String> getEnabledToolNames() {
        return getEnabledTools().stream()
                .map(ToolMetadata::getName)
                .toList();
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
