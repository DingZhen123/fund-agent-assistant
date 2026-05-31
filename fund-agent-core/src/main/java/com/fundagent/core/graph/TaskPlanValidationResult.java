package com.fundagent.core.graph;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class TaskPlanValidationResult {
    private boolean valid;
    private String errorCode;
    private String message;

    public static TaskPlanValidationResult ok() {
        return new TaskPlanValidationResult(true, null, null);
    }

    public static TaskPlanValidationResult error(String errorCode, String message) {
        return new TaskPlanValidationResult(false, errorCode, message);
    }
}
