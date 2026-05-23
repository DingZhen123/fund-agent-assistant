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

    public static ToolResult success(Object data) {
        return new ToolResult(true, data, null);
    }

    public static ToolResult error(String error) {
        return new ToolResult(false, null, error);
    }
}
