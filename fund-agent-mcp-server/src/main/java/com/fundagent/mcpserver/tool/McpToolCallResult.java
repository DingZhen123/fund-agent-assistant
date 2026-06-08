package com.fundagent.mcpserver.tool;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class McpToolCallResult {
    private boolean success;
    private Object content;
    private String errorCode;
    private String errorMessage;

    public static McpToolCallResult ok(Object content) {
        return McpToolCallResult.builder()
                .success(true)
                .content(content)
                .build();
    }

    public static McpToolCallResult error(String errorCode, String errorMessage) {
        return McpToolCallResult.builder()
                .success(false)
                .errorCode(errorCode)
                .errorMessage(errorMessage)
                .build();
    }
}
