package com.fundagent.core.tool;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ToolParamValidationResult {
    private boolean valid;
    private String errorCode;
    private String message;

    public static ToolParamValidationResult ok() {
        return new ToolParamValidationResult(true, null, null);
    }

    public static ToolParamValidationResult error(String errorCode, String message) {
        return new ToolParamValidationResult(false, errorCode, message);
    }
}
