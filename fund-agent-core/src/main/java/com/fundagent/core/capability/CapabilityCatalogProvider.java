package com.fundagent.core.capability;

import java.util.List;

public interface CapabilityCatalogProvider {
    String providerType();

    List<CapabilityDefinition> loadCapabilities();
}
