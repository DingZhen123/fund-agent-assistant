package com.fundagent.core.graph;

import com.fundagent.core.tool.ToolDefinition;
import com.fundagent.core.tool.ToolParamValidationResult;
import com.fundagent.core.tool.ToolRegistry;
import com.fundagent.core.tool.ToolSchemaValidator;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class TaskPlanValidator {
    private final ToolRegistry toolRegistry;
    private final ToolSchemaValidator schemaValidator = new ToolSchemaValidator();

    public TaskPlanValidator(ToolRegistry toolRegistry) {
        this.toolRegistry = toolRegistry;
    }

    public TaskPlanValidationResult validate(TaskPlan plan) {
        if (plan == null) {
            return TaskPlanValidationResult.error("TASK_PLAN_NULL", "任务计划为空");
        }
        if (isBlank(plan.getGoal())) {
            return TaskPlanValidationResult.error("MISSING_GOAL", "任务目标不能为空");
        }
        List<TaskStep> steps = plan.getSteps();
        if (steps == null || steps.isEmpty()) {
            return TaskPlanValidationResult.error("EMPTY_STEPS", "任务步骤不能为空");
        }

        Set<String> stepIds = new HashSet<>();
        boolean hasFinalAnswer = false;
        for (int i = 0; i < steps.size(); i++) {
            TaskStep step = steps.get(i);
            TaskPlanValidationResult result = validateStep(step, stepIds, i);
            if (!result.isValid()) return result;
            if (StepType.FINAL_ANSWER.equals(step.getType())) {
                hasFinalAnswer = true;
            }
            stepIds.add(step.getStepId());
        }

        if (!hasFinalAnswer) {
            return TaskPlanValidationResult.error("MISSING_FINAL_ANSWER", "任务计划必须包含FINAL_ANSWER步骤");
        }
        return TaskPlanValidationResult.ok();
    }

    private TaskPlanValidationResult validateStep(TaskStep step, Set<String> seenStepIds, int index) {
        if (step == null) {
            return TaskPlanValidationResult.error("STEP_NULL", "任务步骤不能为空");
        }
        if (isBlank(step.getStepId())) {
            return TaskPlanValidationResult.error("MISSING_STEP_ID", "step_id不能为空");
        }
        if (seenStepIds.contains(step.getStepId())) {
            return TaskPlanValidationResult.error("DUPLICATE_STEP_ID", "重复的step_id: " + step.getStepId());
        }
        if (step.getType() == null) {
            return TaskPlanValidationResult.error("MISSING_STEP_TYPE", "步骤" + step.getStepId() + "缺少type");
        }
        if (step.getDependsOn() != null) {
            for (String dependency : step.getDependsOn()) {
                if (!seenStepIds.contains(dependency)) {
                    return TaskPlanValidationResult.error("UNSUPPORTED_DEPENDENCY",
                            "第一版仅支持顺序依赖，步骤" + step.getStepId() + "依赖未完成步骤: " + dependency);
                }
            }
        }

        return switch (step.getType()) {
            case TOOL_CALL -> validateToolStep(step);
            case ASK_USER -> validateInstructionStep(step, "ASK_USER");
            case FINAL_ANSWER -> validateFinalAnswerStep(step, index);
        };
    }

    private TaskPlanValidationResult validateToolStep(TaskStep step) {
        if (isBlank(step.getToolName())) {
            return TaskPlanValidationResult.error("MISSING_TOOL", "TOOL_CALL步骤必须指定tool_name");
        }
        ToolDefinition tool = toolRegistry.getTool(step.getToolName());
        if (tool == null) {
            return TaskPlanValidationResult.error("UNKNOWN_TOOL", "未知工具: " + step.getToolName());
        }
        ToolParamValidationResult paramResult = schemaValidator.validate(tool, step.getToolParams());
        if (!paramResult.isValid()) {
            return TaskPlanValidationResult.error(paramResult.getErrorCode(), paramResult.getMessage());
        }
        return TaskPlanValidationResult.ok();
    }

    private TaskPlanValidationResult validateInstructionStep(TaskStep step, String type) {
        if (isBlank(step.getInstruction())) {
            return TaskPlanValidationResult.error("MISSING_INSTRUCTION", type + "步骤必须包含instruction");
        }
        return TaskPlanValidationResult.ok();
    }

    private TaskPlanValidationResult validateFinalAnswerStep(TaskStep step, int index) {
        TaskPlanValidationResult result = validateInstructionStep(step, "FINAL_ANSWER");
        if (!result.isValid()) return result;
        if (index == 0) {
            return TaskPlanValidationResult.error("INVALID_FINAL_ANSWER_POSITION", "FINAL_ANSWER不能是第一个步骤");
        }
        return TaskPlanValidationResult.ok();
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
