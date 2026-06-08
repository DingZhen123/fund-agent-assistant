package com.fundagent.server.tool.mcp;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.fundagent.core.tool.ToolResult;
import lombok.extern.slf4j.Slf4j;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class McpToolClient {
    private static final MediaType JSON_TYPE = MediaType.parse("application/json");
    private final McpToolProperties properties;
    private final OkHttpClient httpClient;

    public McpToolClient(McpToolProperties properties) {
        this.properties = properties;
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(properties.getTimeoutMs(), TimeUnit.MILLISECONDS)
                .readTimeout(properties.getTimeoutMs(), TimeUnit.MILLISECONDS)
                .writeTimeout(properties.getTimeoutMs(), TimeUnit.MILLISECONDS)
                .protocols(java.util.List.of(okhttp3.Protocol.HTTP_1_1))
                .build();
    }

    public ToolResult callTool(String toolName, Map<String, Object> args) {
        if (!properties.isEnabled()) {
            return ToolResult.businessError("MCP_DISABLED", "MCP工具调用未启用");
        }
        if (properties.getBaseUrl() == null || properties.getBaseUrl().isBlank()) {
            return ToolResult.systemError("MCP_BASE_URL_EMPTY", "MCP服务地址未配置", false);
        }

        JSONObject requestBody = new JSONObject();
        requestBody.put("jsonrpc", "2.0");
        requestBody.put("id", UUID.randomUUID().toString());
        requestBody.put("method", "tools/call");
        requestBody.put("params", Map.of(
                "name", toolName,
                "arguments", args != null ? args : Map.of()
        ));

        Request.Builder requestBuilder = new Request.Builder()
                .url(properties.getBaseUrl())
                .header("Content-Type", "application/json")
                .post(RequestBody.create(requestBody.toJSONString(), JSON_TYPE));
        if (properties.getApiKey() != null && !properties.getApiKey().isBlank()) {
            requestBuilder.header("X-MCP-API-Key", properties.getApiKey());
        }

        try (Response response = httpClient.newCall(requestBuilder.build()).execute()) {
            String body = response.body() != null ? response.body().string() : "";
            if (!response.isSuccessful()) {
                log.error("MCP tool call HTTP failed: toolName={}, code={}, body={}",
                        toolName, response.code(), body);
                return ToolResult.transientError("MCP_HTTP_ERROR",
                        "MCP工具调用HTTP失败: " + response.code());
            }
            return parseToolResult(toolName, body);
        } catch (IOException e) {
            log.error("MCP tool call IO failed: toolName={}", toolName, e);
            return ToolResult.transientError("MCP_IO_ERROR", "MCP工具调用网络异常: " + e.getMessage());
        } catch (Exception e) {
            log.error("MCP tool call failed: toolName={}", toolName, e);
            return ToolResult.systemError("MCP_CALL_ERROR", "MCP工具调用异常: " + e.getMessage(), false);
        }
    }

    private ToolResult parseToolResult(String toolName, String body) {
        JSONObject response = JSON.parseObject(body);
        JSONObject error = response.getJSONObject("error");
        if (error != null) {
            String message = error.getString("message");
            log.error("MCP tool call protocol error: toolName={}, error={}", toolName, error);
            return ToolResult.businessError("MCP_PROTOCOL_ERROR", message != null ? message : error.toJSONString());
        }

        JSONObject result = response.getJSONObject("result");
        if (result == null) {
            return ToolResult.systemError("MCP_RESULT_EMPTY", "MCP响应缺少result", false);
        }
        Boolean success = result.getBoolean("success");
        if (Boolean.FALSE.equals(success)) {
            String errorCode = result.getString("errorCode");
            String errorMessage = result.getString("errorMessage");
            return ToolResult.businessError(
                    errorCode != null ? errorCode : "MCP_TOOL_ERROR",
                    errorMessage != null ? errorMessage : "MCP工具执行失败"
            );
        }
        return ToolResult.success(result.get("content"));
    }
}
