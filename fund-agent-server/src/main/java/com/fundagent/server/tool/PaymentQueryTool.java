package com.fundagent.server.tool;

import com.fundagent.core.tool.Tool;
import com.fundagent.core.tool.ToolResult;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class PaymentQueryTool {

    @Tool(name = "queryPaymentDocuments",
          description = "查询支付单据状态和回单信息",
          params = {"noteCode"})
    public ToolResult queryPayment(Map<String, Object> args) {
        String noteCode = (String) args.get("noteCode");
        return ToolResult.success(Map.of(
                "noteCode", noteCode,
                "status", "已付款",
                "hasReceipt", true,
                "amount", "10000.00",
                "payTime", "2026-05-20 15:30:00",
                "payer", "安克创新科技股份有限公司",
                "payee", "供应商A"
        ));
    }

    @Tool(name = "sendReceiptFiles",
          description = "发送支付回单文件",
          params = {"noteCode"})
    public ToolResult sendReceipt(Map<String, Object> args) {
        String noteCode = (String) args.get("noteCode");
        return ToolResult.success(Map.of(
                "success", true,
                "fileUrl", "https://example.com/receipts/" + noteCode + ".pdf",
                "fileName", noteCode + "_银行回单.pdf"
        ));
    }
}
