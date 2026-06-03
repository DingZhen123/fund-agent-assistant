package com.fundagent.core.capability;

import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

public class CapabilityCatalog {
    private final Map<String, CapabilityDefinition> capabilities;

    public CapabilityCatalog(Collection<CapabilityDefinition> definitions) {
        this.capabilities = definitions.stream()
                .sorted(Comparator.comparing(CapabilityDefinition::getName))
                .collect(Collectors.toMap(
                        CapabilityDefinition::getName,
                        capability -> capability,
                        (left, right) -> right,
                        LinkedHashMap::new));
    }

    public static CapabilityCatalog fromProviders(Collection<CapabilityCatalogProvider> providers) {
        return new CapabilityCatalog(providers.stream()
                .flatMap(provider -> provider.loadCapabilities().stream())
                .toList());
    }

    public Optional<CapabilityDefinition> getCapability(String name) {
        return Optional.ofNullable(capabilities.get(name));
    }

    public boolean hasCapability(String name) {
        return capabilities.containsKey(name);
    }

    public List<CapabilityDefinition> getAllCapabilities() {
        return List.copyOf(capabilities.values());
    }

    public List<CapabilityDefinition> getEnabledCapabilities() {
        return capabilities.values().stream()
                .filter(CapabilityDefinition::isEnabled)
                .toList();
    }

    public List<CapabilityDefinition> findByDomain(String domain) {
        if (isBlank(domain)) {
            return List.of();
        }
        String normalized = normalize(domain);
        return getEnabledCapabilities().stream()
                .filter(capability -> normalized.equals(normalize(capability.getDomain())))
                .toList();
    }

    public List<CapabilityDefinition> findByNodeType(String nodeType) {
        if (isBlank(nodeType)) {
            return List.of();
        }
        String normalized = normalize(nodeType);
        return getEnabledCapabilities().stream()
                .filter(capability -> normalized.equals(normalize(capability.getNodeType())))
                .toList();
    }

    public List<CapabilityDefinition> findByIntent(String intent) {
        if (isBlank(intent)) {
            return List.of();
        }
        String normalized = normalize(intent);
        return getEnabledCapabilities().stream()
                .filter(capability -> capability.getIntents() != null
                        && capability.getIntents().stream().anyMatch(item -> normalized.equals(normalize(item))))
                .toList();
    }

    public String getCapabilitiesDescription() {
        return getEnabledCapabilities().stream()
                .map(capability -> "- " + capability.getName()
                        + ": " + capability.getDescription()
                        + (capability.getNodeType() != null ? ", nodeType: " + capability.getNodeType() : "")
                        + (capability.getDomain() != null ? ", domain: " + capability.getDomain() : "")
                        + (capability.getIntents() != null && !capability.getIntents().isEmpty()
                        ? ", intents: " + String.join("/", capability.getIntents()) : ""))
                .collect(Collectors.joining("\n"));
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
