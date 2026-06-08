package com.fundagent.mcpserver.controller;

import com.fundagent.mcpserver.auth.McpAuthContext;
import com.fundagent.mcpserver.auth.McpAuthPolicy;
import com.fundagent.mcpserver.protocol.McpRequest;
import com.fundagent.mcpserver.protocol.McpResponse;
import com.fundagent.mcpserver.tool.McpToolCallResult;
import com.fundagent.mcpserver.tool.McpToolRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/mcp")
public class McpController {
    private static final String METHOD_TOOLS_LIST = "tools/list";
    private static final String METHOD_TOOLS_CALL = "tools/call";

    private final McpToolRegistry toolRegistry;
    private final McpAuthPolicy authPolicy;

    public McpController(McpToolRegistry toolRegistry, McpAuthPolicy authPolicy) {
        this.toolRegistry = toolRegistry;
        this.authPolicy = authPolicy;
    }

    @PostMapping
    public McpResponse handle(@RequestBody McpRequest request,
                              @RequestHeader(value = "X-MCP-API-Key", required = false) String apiKey,
                              HttpServletRequest servletRequest) {
        if (request == null || request.getMethod() == null || request.getMethod().isBlank()) {
            return McpResponse.error(null, -32600, "Invalid MCP request");
        }
        McpAuthContext authContext = McpAuthContext.builder()
                .apiKey(apiKey)
                .remoteAddress(servletRequest.getRemoteAddr())
                .build();
        return switch (request.getMethod()) {
            case METHOD_TOOLS_LIST -> listTools(request, authContext);
            case METHOD_TOOLS_CALL -> callTool(request, authContext);
            default -> McpResponse.error(request.getId(), -32601, "Method not found: " + request.getMethod());
        };
    }

    private McpResponse listTools(McpRequest request, McpAuthContext authContext) {
        if (!authPolicy.canListTools(authContext)) {
            return McpResponse.error(request.getId(), 403, "Forbidden");
        }
        log.info("MCP tools/list: remoteAddress={}, tools={}",
                authContext.getRemoteAddress(), toolRegistry.toolNames());
        return McpResponse.ok(request.getId(), Map.of("tools", toolRegistry.listTools()));
    }

    @SuppressWarnings("unchecked")
    private McpResponse callTool(McpRequest request, McpAuthContext authContext) {
        Map<String, Object> params = request.getParams() != null ? request.getParams() : Map.of();
        String toolName = params.get("name") instanceof String ? (String) params.get("name") : null;
        Map<String, Object> arguments = params.get("arguments") instanceof Map
                ? (Map<String, Object>) params.get("arguments")
                : Map.of();
        if (toolName == null || toolName.isBlank()) {
            return McpResponse.error(request.getId(), -32602, "Missing tool name");
        }
        if (!authPolicy.canCallTool(authContext, toolName, arguments)) {
            return McpResponse.error(request.getId(), 403, "Forbidden");
        }
        log.info("MCP tools/call started: remoteAddress={}, toolName={}, args={}",
                authContext.getRemoteAddress(), toolName, arguments);
        McpToolCallResult result = toolRegistry.call(toolName, arguments);
        log.info("MCP tools/call finished: toolName={}, success={}, errorCode={}",
                toolName, result.isSuccess(), result.getErrorCode());
        return McpResponse.ok(request.getId(), result);
    }
}
