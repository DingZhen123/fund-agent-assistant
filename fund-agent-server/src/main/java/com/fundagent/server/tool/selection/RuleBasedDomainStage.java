package com.fundagent.server.tool.selection;

import com.fundagent.core.tool.selection.ToolSelectionContext;
import com.fundagent.core.tool.selection.ToolSelectionStage;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.regex.Pattern;

@Component
@Order(100)
public class RuleBasedDomainStage implements ToolSelectionStage {
    private static final Pattern FINANCE_CODE_PATTERN =
            Pattern.compile("\\b(?:EC|LA|TR|FP|YFKD)[A-Za-z0-9_-]+\\b");
    private static final List<String> FINANCE_KEYWORDS =
            List.of("付款", "支付", "回单", "银行", "资金", "单据", "收款", "报销");
    private static final List<String> HR_KEYWORDS =
            List.of("员工", "部门", "工号", "入职", "离职", "组织");
    private static final List<String> PROCUREMENT_KEYWORDS =
            List.of("采购", "供应商", "采购单", "PO", "订单");
    private static final List<String> CONTRACT_KEYWORDS =
            List.of("合同", "协议", "法务");

    @Override
    public void apply(ToolSelectionContext context) {
        String message = context.getUserMessage();
        if (message.isEmpty()) {
            context.addMatchedRule("domain:empty_message");
            return;
        }
        if (FINANCE_CODE_PATTERN.matcher(message).find() || containsAny(message, FINANCE_KEYWORDS)) {
            context.setDomain("finance", 0.85, "命中资金领域规则");
            context.addMatchedRule("domain:finance");
            return;
        }
        if (containsAny(message, HR_KEYWORDS)) {
            context.setDomain("hr", 0.8, "命中人力领域规则");
            context.addMatchedRule("domain:hr");
            return;
        }
        if (containsAny(message, PROCUREMENT_KEYWORDS)) {
            context.setDomain("procurement", 0.8, "命中采购领域规则");
            context.addMatchedRule("domain:procurement");
            return;
        }
        if (containsAny(message, CONTRACT_KEYWORDS)) {
            context.setDomain("contract", 0.8, "命中合同领域规则");
            context.addMatchedRule("domain:contract");
            return;
        }
        context.addMatchedRule("domain:unknown");
    }

    private boolean containsAny(String message, List<String> keywords) {
        for (String keyword : keywords) {
            if (message.contains(keyword)) {
                return true;
            }
        }
        return false;
    }
}
