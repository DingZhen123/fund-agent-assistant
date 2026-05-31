package com.fundagent.core.tool;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ToolResult {
    private boolean success;
    private Object data;
    private String error;
    private String errorCode;
    private ToolErrorType errorType;
    private boolean retryable;

    public static ToolResult success(Object data) {
        return new ToolResult(true, data, null, null, null, false);
    }

    public static ToolResult error(String error) {
        return businessError("BUSINESS_ERROR", error);
    }

    public static ToolResult validationError(String errorCode, String error) {
        return new ToolResult(false, null, error, errorCode, ToolErrorType.VALIDATION_ERROR, false);
    }

    public static ToolResult unknownTool(String error) {
        return new ToolResult(false, null, error, "UNKNOWN_TOOL", ToolErrorType.UNKNOWN_TOOL, false);
    }

    public static ToolResult businessError(String errorCode, String error) {
        return new ToolResult(false, null, error, errorCode, ToolErrorType.BUSINESS_ERROR, false);
    }

    public static ToolResult transientError(String errorCode, String error) {
        return new ToolResult(false, null, error, errorCode, ToolErrorType.TRANSIENT_ERROR, true);
    }

    public static ToolResult systemError(String errorCode, String error, boolean retryable) {
        return new ToolResult(false, null, error, errorCode, ToolErrorType.SYSTEM_ERROR, retryable);
    }
}
