package com.fundagent.core.dag;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class DagPlanValidationResult {
    private boolean valid;
    private String errorCode;
    private String message;

    public static DagPlanValidationResult ok() {
        return new DagPlanValidationResult(true, null, null);
    }

    public static DagPlanValidationResult error(String errorCode, String message) {
        return new DagPlanValidationResult(false, errorCode, message);
    }
}
