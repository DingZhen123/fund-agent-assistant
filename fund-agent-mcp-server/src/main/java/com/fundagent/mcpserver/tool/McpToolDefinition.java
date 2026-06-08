package com.fundagent.mcpserver.tool;

import com.alibaba.fastjson2.JSONObject;
import com.alibaba.fastjson2.annotation.JSONField;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class McpToolDefinition {
    private String name;
    private String description;

    @JSONField(name = "inputSchema")
    private JSONObject inputSchema;
}
