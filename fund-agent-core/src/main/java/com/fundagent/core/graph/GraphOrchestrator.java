package com.fundagent.core.graph;

import com.alibaba.fastjson2.JSON;
import com.fundagent.core.tool.ToolRegistry;
import com.fundagent.core.tool.ToolResult;
import lombok.extern.slf4j.Slf4j;

import java.util.UUID;
import java.util.function.Function;

@Slf4j
public class GraphOrchestrator {
    private static final int DEFAULT_MAX_STEPS = 20;

    private final ToolRegistry toolRegistry;
    private final TaskPlanValidator taskPlanValidator;
    private final int maxSteps;
    private Function<GraphState, String> answerGenerator;

    public GraphOrchestrator(ToolRegistry toolRegistry) {
        this(toolRegistry, DEFAULT_MAX_STEPS);
    }

    public GraphOrchestrator(ToolRegistry toolRegistry, int maxSteps) {
        this.toolRegistry = toolRegistry;
        this.taskPlanValidator = new TaskPlanValidator(toolRegistry);
        this.maxSteps = maxSteps;
    }

    public void setAnswerGenerator(Function<GraphState, String> answerGenerator) {
        this.answerGenerator = answerGenerator;
    }

    public GraphResult execute(TaskPlan taskPlan, String userId, String conversationId, String userMessage) {
        GraphState state = new GraphState();
        state.setTaskId(taskPlan != null && taskPlan.getTaskId() != null
                ? taskPlan.getTaskId()
                : UUID.randomUUID().toString());
        state.setUserId(userId);
        state.setConversationId(conversationId);
        state.setUserMessage(userMessage);
        state.setTaskPlan(taskPlan);

        TaskPlanValidationResult validation = taskPlanValidator.validate(taskPlan);
        if (!validation.isValid()) {
            log.warn("TaskPlan validation failed: code={}, message={}",
                    validation.getErrorCode(), validation.getMessage());
            return GraphResult.failed("任务计划不合法: " + validation.getMessage(), state);
        }

        for (TaskStep step : taskPlan.getSteps()) {
            if (state.getExecutedSteps() >= maxSteps) {
                return GraphResult.failed("任务步骤超过上限，请简化问题后重试", state);
            }

            GraphResult result = executeStep(step, state);
            if (result != null) {
                return result;
            }
        }

        String answer = buildObservationSummary(state);
        state.setFinalAnswer(answer);
        return GraphResult.completed(answer, state);
    }

    private GraphResult executeStep(TaskStep step, GraphState state) {
        long start = System.currentTimeMillis();
        state.setExecutedSteps(state.getExecutedSteps() + 1);
        state.getExecutedStepIds().add(step.getStepId());

        return switch (step.getType()) {
            case TOOL_CALL -> {
                StepExecutionResult executionResult = executeToolWithRetry(step);
                ToolResult toolResult = executionResult.toolResult();
                Observation observation = buildToolObservation(step, toolResult, executionResult.attempts(),
                        System.currentTimeMillis() - start);
                state.getObservations().put(step.getStepId(), observation);
                log.info("Graph step executed: stepId={}, tool={}, success={}, attempts={}",
                        step.getStepId(), step.getToolName(), toolResult.isSuccess(), executionResult.attempts());
                if (!toolResult.isSuccess()) {
                    yield GraphResult.failed("工具执行失败: " + toolResult.getError(), state);
                }
                yield null;
            }
            case ASK_USER -> {
                state.setWaitingUserInput(true);
                Observation observation = new Observation(
                        step.getStepId(),
                        "ASK_USER",
                        true,
                        step.getInstruction(),
                        null,
                        null,
                        null,
                        false,
                        1,
                        System.currentTimeMillis() - start);
                state.getObservations().put(step.getStepId(), observation);
                yield GraphResult.waiting(step.getInstruction(), state);
            }
            case FINAL_ANSWER -> {
                String answer = buildFinalAnswer(step, state);
                state.setFinalAnswer(answer);
                Observation observation = new Observation(
                        step.getStepId(),
                        "FINAL_ANSWER",
                        true,
                        answer,
                        null,
                        null,
                        null,
                        false,
                        1,
                        System.currentTimeMillis() - start);
                state.getObservations().put(step.getStepId(), observation);
                yield GraphResult.completed(answer, state);
            }
        };
    }

    private StepExecutionResult executeToolWithRetry(TaskStep step) {
        StepRetryPolicy policy = resolveRetryPolicy(step);
        ToolResult lastResult = null;
        long delayMs = policy.getInitialDelayMs();

        for (int attempt = 1; attempt <= policy.getMaxAttempts(); attempt++) {
            lastResult = toolRegistry.execute(step.getToolName(), step.getToolParams());
            if (lastResult.isSuccess()) {
                return new StepExecutionResult(lastResult, attempt);
            }

            boolean shouldRetry = lastResult.isRetryable() && attempt < policy.getMaxAttempts();
            log.warn("Graph step attempt failed: stepId={}, tool={}, attempt={}/{}, retryable={}, errorType={}, errorCode={}, error={}",
                    step.getStepId(), step.getToolName(), attempt, policy.getMaxAttempts(),
                    lastResult.isRetryable(), lastResult.getErrorType(), lastResult.getErrorCode(), lastResult.getError());

            if (!shouldRetry) {
                return new StepExecutionResult(lastResult, attempt);
            }
            sleep(delayMs);
            delayMs = nextDelay(delayMs, policy.getBackoffMultiplier());
        }

        return new StepExecutionResult(lastResult, policy.getMaxAttempts());
    }

    private StepRetryPolicy resolveRetryPolicy(TaskStep step) {
        return StepType.TOOL_CALL.equals(step.getType())
                ? StepRetryPolicy.defaultToolRetry()
                : StepRetryPolicy.noRetry();
    }

    private Observation buildToolObservation(TaskStep step, ToolResult toolResult, int attempts, long elapsedMs) {
        return new Observation(
                step.getStepId(),
                step.getToolName(),
                toolResult.isSuccess(),
                toolResult.getData(),
                toolResult.getError(),
                toolResult.getErrorCode(),
                toolResult.getErrorType() != null ? toolResult.getErrorType().name() : null,
                toolResult.isRetryable(),
                attempts,
                elapsedMs);
    }

    private void sleep(long delayMs) {
        if (delayMs <= 0) return;
        try {
            Thread.sleep(delayMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private long nextDelay(long currentDelayMs, double backoffMultiplier) {
        if (currentDelayMs <= 0) return 0;
        return Math.max(currentDelayMs, Math.round(currentDelayMs * backoffMultiplier));
    }

    private String buildFinalAnswer(TaskStep step, GraphState state) {
        if (answerGenerator != null) {
            return answerGenerator.apply(state);
        }
        String instruction = step.getInstruction() != null ? step.getInstruction() : "任务执行完成";
        return instruction + "\n\n执行结果:\n" + buildObservationSummary(state);
    }

    private String buildObservationSummary(GraphState state) {
        if (state.getObservations().isEmpty()) {
            return "暂无执行结果。";
        }
        StringBuilder sb = new StringBuilder();
        state.getObservations().values().forEach(observation -> {
            if ("FINAL_ANSWER".equals(observation.getSource())) return;
            sb.append("- ").append(observation.getStepId())
                    .append(" [").append(observation.getSource()).append("] ")
                    .append(observation.isSuccess() ? "成功" : "失败");
            if (observation.getData() != null) {
                sb.append(": ").append(JSON.toJSONString(observation.getData()));
            }
            if (observation.getError() != null) {
                sb.append(": ").append(observation.getError());
            }
            sb.append("\n");
        });
        return sb.toString().trim();
    }

    private record StepExecutionResult(ToolResult toolResult, int attempts) {
    }
}
