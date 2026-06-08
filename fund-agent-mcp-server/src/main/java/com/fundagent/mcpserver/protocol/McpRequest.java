package com.fundagent.mcpserver.protocol;

import lombok.Data;

import java.util.Map;

@Data
public class McpRequest {
    private String jsonrpc = "2.0";
    private Object id;
    private String method;
    private Map<String, Object> params;
}
