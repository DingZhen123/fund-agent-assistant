package com.fundagent.core.plan;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class PlanValidationResult {
    private boolean valid;
    private String errorCode;
    private String message;
    private boolean recoverable;

    public static PlanValidationResult ok() {
        return new PlanValidationResult(true, null, null, false);
    }

    public static PlanValidationResult error(String errorCode, String message, boolean recoverable) {
        return new PlanValidationResult(false, errorCode, message, recoverable);
    }
}
