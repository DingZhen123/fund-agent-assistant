package com.fundagent.mcpserver.protocol;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class McpResponse {
    private String jsonrpc;
    private Object id;
    private Object result;
    private McpResponseError error;

    public static McpResponse ok(Object id, Object result) {
        return McpResponse.builder()
                .jsonrpc("2.0")
                .id(id)
                .result(result)
                .build();
    }

    public static McpResponse error(Object id, int code, String message) {
        return McpResponse.builder()
                .jsonrpc("2.0")
                .id(id)
                .error(new McpResponseError(code, message))
                .build();
    }
}
