package com.fundagent.core.capability;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class CapabilityValidationResult {
    private boolean valid;
    private String errorCode;
    private String message;

    public static CapabilityValidationResult ok() {
        return new CapabilityValidationResult(true, null, null);
    }

    public static CapabilityValidationResult error(String errorCode, String message) {
        return new CapabilityValidationResult(false, errorCode, message);
    }
}
