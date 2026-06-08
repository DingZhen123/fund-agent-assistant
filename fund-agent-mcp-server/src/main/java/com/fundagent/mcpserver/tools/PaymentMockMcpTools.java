package com.fundagent.mcpserver.tools;

import com.fundagent.mcpserver.tool.McpTool;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class PaymentMockMcpTools {

    @McpTool(
            name = "payment_status_query_mock",
            description = "模拟查询付款状态，用于验证MCP工具与finance.payment.query能力绑定",
            inputSchemaJson = """
                    {
                      "type": "object",
                      "properties": {
                        "noteCode": {
                          "type": "string",
                          "description": "付款单据编号"
                        }
                      },
                      "required": ["noteCode"],
                      "additionalProperties": false
                    }
                    """
    )
    public Map<String, Object> queryPaymentStatus(Map<String, Object> args) {
        String noteCode = String.valueOf(args.getOrDefault("noteCode", ""));
        boolean paid = noteCode.toUpperCase().startsWith("EC");
        return Map.of(
                "noteCode", noteCode,
                "paid", paid,
                "status", paid ? "PAID" : "UNPAID",
                "source", "mcp_mock"
        );
    }
}
