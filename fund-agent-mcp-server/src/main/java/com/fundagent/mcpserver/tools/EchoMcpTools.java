package com.fundagent.mcpserver.tools;

import com.fundagent.mcpserver.tool.McpTool;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class EchoMcpTools {

    @McpTool(
            name = "echo",
            description = "返回输入文本，用于验证MCP工具服务连通性",
            inputSchemaJson = """
                    {
                      "type": "object",
                      "properties": {
                        "text": {
                          "type": "string",
                          "description": "需要原样返回的文本"
                        }
                      },
                      "required": ["text"],
                      "additionalProperties": false
                    }
                    """
    )
    public Map<String, Object> echo(Map<String, Object> args) {
        return Map.of("text", args.getOrDefault("text", ""));
    }
}
