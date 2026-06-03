package com.fundagent.core.capability;

public class CapabilityValidator {
    private final CapabilityCatalog capabilityCatalog;

    public CapabilityValidator(CapabilityCatalog capabilityCatalog) {
        this.capabilityCatalog = capabilityCatalog;
    }

    public CapabilityValidationResult validate(String capabilityName) {
        if (capabilityName == null || capabilityName.trim().isEmpty()) {
            return CapabilityValidationResult.error("MISSING_CAPABILITY", "capability不能为空");
        }
        return capabilityCatalog.getCapability(capabilityName)
                .map(capability -> capability.isEnabled()
                        ? CapabilityValidationResult.ok()
                        : CapabilityValidationResult.error("CAPABILITY_DISABLED",
                        "能力已禁用: " + capabilityName))
                .orElseGet(() -> CapabilityValidationResult.error("UNKNOWN_CAPABILITY",
                        "未知能力: " + capabilityName));
    }
}
