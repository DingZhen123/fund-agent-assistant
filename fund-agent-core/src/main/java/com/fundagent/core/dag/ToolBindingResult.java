package com.fundagent.core.dag;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ToolBindingResult {
    private boolean success;
    private String errorCode;
    private String message;

    public static ToolBindingResult ok() {
        return new ToolBindingResult(true, null, null);
    }

    public static ToolBindingResult error(String errorCode, String message) {
        return new ToolBindingResult(false, errorCode, message);
    }
}
