package com.fundagent.mcpserver.protocol;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class McpResponseError {
    private int code;
    private String message;
}
