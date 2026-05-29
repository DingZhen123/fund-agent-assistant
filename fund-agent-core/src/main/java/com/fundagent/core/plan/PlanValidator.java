package com.fundagent.core.plan;

import com.fundagent.core.tool.ToolDefinition;
import com.fundagent.core.tool.ToolRegistry;

import java.util.Map;

public class PlanValidator {
    private static final String TARGET_EXECUTOR = "Executor";
    private static final String TARGET_USER = "User";

    private final ToolRegistry toolRegistry;

    public PlanValidator(ToolRegistry toolRegistry) {
        this.toolRegistry = toolRegistry;
    }

    public PlanValidationResult validate(Plan plan) {
        if (plan == null) {
            return PlanValidationResult.error("PLAN_NULL", "Planner输出为空", false);
        }

        String sendTo = trim(plan.getSendTo());
        String toolName = trim(plan.getToolName());
        Map<String, Object> toolParams = plan.getToolParams();

        if (!TARGET_EXECUTOR.equals(sendTo) && !TARGET_USER.equals(sendTo)) {
            return PlanValidationResult.error("INVALID_TARGET", "send_to只能是Executor或User", false);
        }

        if (TARGET_USER.equals(sendTo)) {
            if (!plan.isStop()) {
                return PlanValidationResult.error("INVALID_STOP_STATE", "send_to=User时stop必须为true", false);
            }
            if (!toolName.isEmpty()) {
                return PlanValidationResult.error("UNEXPECTED_TOOL", "send_to=User时tool_name必须为空", false);
            }
            if (toolParams != null && !toolParams.isEmpty()) {
                return PlanValidationResult.error("UNEXPECTED_TOOL_PARAMS", "send_to=User时tool_params必须为空", false);
            }
            return PlanValidationResult.ok();
        }

        if (plan.isStop()) {
            return PlanValidationResult.error("INVALID_STOP_STATE", "send_to=Executor时stop必须为false", false);
        }
        if (toolName.isEmpty()) {
            return PlanValidationResult.error("MISSING_TOOL", "需要调用工具时tool_name不能为空", false);
        }

        ToolDefinition tool = toolRegistry.getTool(toolName);
        if (tool == null) {
            return PlanValidationResult.error("UNKNOWN_TOOL", "未知工具: " + toolName, false);
        }
        if (toolParams == null) {
            return PlanValidationResult.error("MISSING_TOOL_PARAMS", "tool_params不能为空", true);
        }

        for (String paramName : tool.getParams()) {
            Object value = toolParams.get(paramName);
            if (value == null || value.toString().trim().isEmpty()) {
                return PlanValidationResult.error("MISSING_REQUIRED_PARAM",
                        "缺少必需参数: " + paramName, true);
            }
        }

        for (String paramName : toolParams.keySet()) {
            if (!tool.getParams().contains(paramName)) {
                return PlanValidationResult.error("UNKNOWN_TOOL_PARAM",
                        "工具" + toolName + "不支持参数: " + paramName, false);
            }
        }

        return PlanValidationResult.ok();
    }

    private String trim(String value) {
        return value != null ? value.trim() : "";
    }
}
