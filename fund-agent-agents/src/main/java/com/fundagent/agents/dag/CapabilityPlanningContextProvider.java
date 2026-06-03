package com.fundagent.agents.dag;

import com.fundagent.core.capability.CapabilityCatalog;
import com.fundagent.core.capability.CapabilityDefinition;
import com.fundagent.core.memory.Memory;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public class CapabilityPlanningContextProvider {
    private final CapabilityCatalog capabilityCatalog;
    private final DagPlanSchemaBuilder dagPlanSchemaBuilder;

    public CapabilityPlanningContextProvider(CapabilityCatalog capabilityCatalog,
                                             DagPlanSchemaBuilder dagPlanSchemaBuilder) {
        this.capabilityCatalog = capabilityCatalog;
        this.dagPlanSchemaBuilder = dagPlanSchemaBuilder;
    }

    public CapabilityPlanningContext build(Memory memory, String userMessage) {
        List<CapabilityDefinition> capabilities = capabilityCatalog.getEnabledCapabilities().stream()
                .sorted(Comparator.comparing(CapabilityDefinition::getName))
                .toList();
        return new CapabilityPlanningContext(
                capabilities,
                buildCapabilitiesDescription(capabilities),
                dagPlanSchemaBuilder.build(capabilities));
    }

    private String buildCapabilitiesDescription(List<CapabilityDefinition> capabilities) {
        if (capabilities == null || capabilities.isEmpty()) {
            return "当前没有可用能力。";
        }
        return capabilities.stream()
                .map(capability -> "- " + capability.getName()
                        + ": " + capability.getDescription()
                        + ", nodeType: " + capability.getNodeType()
                        + (capability.getDomain() != null ? ", domain: " + capability.getDomain() : "")
                        + (capability.getIntents() != null && !capability.getIntents().isEmpty()
                        ? ", intents: " + String.join("/", capability.getIntents()) : ""))
                .collect(Collectors.joining("\n"));
    }
}
