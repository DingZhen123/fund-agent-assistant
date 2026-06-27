package com.fundagent.core.dag;

import com.alibaba.fastjson2.JSON;
import com.fundagent.core.trace.AppendTraceEventCommand;
import com.fundagent.core.trace.TraceAppendResult;
import com.fundagent.core.trace.TraceContext;
import com.fundagent.core.trace.TraceEventStatus;
import com.fundagent.core.trace.TraceEventType;
import com.fundagent.core.trace.TraceStage;
import com.fundagent.core.trace.TraceStore;
import lombok.extern.slf4j.Slf4j;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
public class DagRuntime {
    private final NodeRouter nodeRouter;
    private final NodeCompletionChecker nodeCompletionChecker;
    private final FinalDagVerifier finalDagVerifier;
    private final TraceStore traceStore;

    public DagRuntime(NodeRouter nodeRouter, NodeCompletionChecker nodeCompletionChecker,
                      FinalDagVerifier finalDagVerifier) {
        this(nodeRouter, nodeCompletionChecker, finalDagVerifier, null);
    }

    public DagRuntime(NodeRouter nodeRouter, NodeCompletionChecker nodeCompletionChecker,
                      FinalDagVerifier finalDagVerifier, TraceStore traceStore) {
        this.nodeRouter = nodeRouter;
        this.nodeCompletionChecker = nodeCompletionChecker;
        this.finalDagVerifier = finalDagVerifier;
        this.traceStore = traceStore;
    }

    public DagRunResult run(BoundDagPlan plan, DagExecutionContext context) {
        DagGraphState state = initializeState(plan, context);
        return run(plan, context, state, new LinkedHashMap<>(), true);
    }

    public DagRunResult continueRun(BoundDagPlan plan, DagExecutionContext context, DagGraphState existingState,
                                    Map<String, NodeCompletionResult> existingCompletionResults) {
        return continueRun(plan, context, existingState, existingCompletionResults, false);
    }

    public DagRunResult continueRun(BoundDagPlan plan, DagExecutionContext context, DagGraphState existingState,
                                    Map<String, NodeCompletionResult> existingCompletionResults,
                                    boolean verifyFinalDag) {
        DagGraphState state = existingState != null ? existingState : initializeState(plan, context);
        Map<String, NodeCompletionResult> completionResults = existingCompletionResults != null
                ? new LinkedHashMap<>(existingCompletionResults)
                : new LinkedHashMap<>();
        return run(plan, context, state, completionResults, verifyFinalDag);
    }

    private DagRunResult run(BoundDagPlan plan, DagExecutionContext context, DagGraphState state,
                             Map<String, NodeCompletionResult> completionResults,
                             boolean verifyFinalDag) {
        if (plan == null) {
            return DagRunResult.failed(state, null, "BOUND_DAG_NULL", "绑定后的DAG为空",
                    completionResults, null, null, DagFailureStage.SCHEDULER);
        }
        if (plan.getNodes() == null || plan.getNodes().isEmpty()) {
            return DagRunResult.failed(state, null, "EMPTY_BOUND_NODES", "绑定后的DAG节点不能为空",
                    completionResults, null, null, DagFailureStage.SCHEDULER);
        }

        NodeExecutionResult lastResult = null;
        for (BoundDagNode node : plan.getNodes()) {
            log.info("DAG node preparing: dagId={}, nodeId={}, name={}, type={}, capability={}, dependsOn={}, bindingStatus={}, boundTools={}",
                    state.getDagId(),
                    node.getNodeId(),
                    node.getName(),
                    node.getNodeType(),
                    node.getCapability(),
                    node.getDependsOn(),
                    node.getBindingStatus(),
                    boundToolNames(node));
            if (!state.dependenciesCompleted(node.getDependsOn())) {
                return DagRunResult.failed(state, lastResult, "DEPENDENCY_NOT_COMPLETED",
                        "节点依赖尚未成功完成: " + node.getNodeId(), completionResults, null,
                        node.getNodeId(), DagFailureStage.SCHEDULER);
            }

            NodeExecutor executor;
            try {
                executor = nodeRouter.route(node);
            } catch (Exception e) {
                return DagRunResult.failed(state, lastResult, "NODE_EXECUTOR_NOT_FOUND", e.getMessage(),
                        completionResults, null, node.getNodeId(), DagFailureStage.SCHEDULER);
            }

            log.info("DAG node started: dagId={}, nodeId={}, executor={}, capability={}, boundTools={}",
                    state.getDagId(),
                    node.getNodeId(),
                    executor.getClass().getSimpleName(),
                    node.getCapability(),
                    boundToolNames(node));
            appendNodeTrace(context, node, TraceEventType.NODE_STARTED, TraceEventStatus.STARTED,
                    "DAG节点开始执行", Map.of(
                            "dagId", safe(state.getDagId()),
                            "nodeType", node.getNodeType() != null ? node.getNodeType().name() : "",
                            "executor", executor.getClass().getSimpleName()));
            lastResult = executor.execute(node, state, context);
            state.addObservation(lastResult.getObservation());
            NodeCompletionResult completion = nodeCompletionChecker.check(node, lastResult, state);
            completionResults.put(node.getNodeId(), completion);
            appendNodeTrace(context, node, nodeEventType(lastResult, completion), nodeStatus(lastResult, completion),
                    nodeSummary(lastResult, completion), Map.of(
                            "dagId", safe(state.getDagId()),
                            "nodeType", node.getNodeType() != null ? node.getNodeType().name() : "",
                            "success", lastResult.isSuccess(),
                            "waitingUserInput", completion.isWaitingUserInput() || lastResult.isWaitingUserInput(),
                            "completionPassed", completion.isPassed(),
                            "errorCode", safe(resolveErrorCode(lastResult, completion))));
            log.info("DAG node executed: dagId={}, nodeId={}, type={}, capability={}, status={}, success={}, completionPassed={}, waitingUserInput={}",
                    state.getDagId(),
                    node.getNodeId(),
                    node.getNodeType(),
                    node.getCapability(),
                    lastResult.getObservation() != null ? lastResult.getObservation().getStatus() : null,
                    lastResult.isSuccess(),
                    completion.isPassed(),
                    completion.isWaitingUserInput() || lastResult.isWaitingUserInput());

            if (!completion.isPassed()) {
                return DagRunResult.failed(state, lastResult, completion.getErrorCode(), completion.getMessage(),
                        completionResults, null, node.getNodeId(), DagFailureStage.NODE_COMPLETION_CHECK);
            }

            if (completion.isWaitingUserInput() || lastResult.isWaitingUserInput()) {
                return DagRunResult.waiting(state, lastResult, completionResults);
            }
            if (!lastResult.isSuccess()) {
                return DagRunResult.failed(state, lastResult, lastResult.getErrorCode(), lastResult.getMessage(),
                        completionResults, null, node.getNodeId(), DagFailureStage.NODE_EXECUTION);
            }
        }

        DagRunResult completed = DagRunResult.completed(state, lastResult, completionResults, null);
        if (verifyFinalDag) {
            FinalVerificationResult finalVerification = finalDagVerifier.verify(plan, completed);
            if (!finalVerification.isPassed()) {
                return DagRunResult.failed(state, lastResult, finalVerification.getErrorCode(),
                        finalVerification.getMessage(), completionResults, finalVerification,
                        null, DagFailureStage.FINAL_VERIFICATION);
            }
            return DagRunResult.completed(state, lastResult, completionResults, finalVerification);
        }
        return completed;
    }

    private List<String> boundToolNames(BoundDagNode node) {
        if (node == null || node.getBoundTools() == null) {
            return List.of();
        }
        return node.getBoundTools().stream()
                .map(tool -> tool.getToolName() + "(" + tool.getProviderType() + ")")
                .toList();
    }

    private DagGraphState initializeState(BoundDagPlan plan, DagExecutionContext context) {
        DagGraphState state = new DagGraphState();
        String dagId = plan != null ? plan.getDagId() : null;
        state.setDagId(dagId != null ? dagId : context != null ? context.getDagId() : null);
        state.setConversationId(context != null ? context.getConversationId() : null);
        state.setUserId(context != null ? context.getUserId() : null);
        state.setUserMessage(context != null ? context.getUserMessage() : null);
        return state;
    }

    private void appendNodeTrace(DagExecutionContext context, BoundDagNode node, TraceEventType eventType,
                                 TraceEventStatus status, String summary, Map<String, Object> payload) {
        if (traceStore == null || context == null || context.getTraceContext() == null || node == null) {
            return;
        }
        TraceContext current = context.getTraceContext();
        AppendTraceEventCommand command = AppendTraceEventCommand.builder()
                .eventCode(eventCode(current, node, eventType))
                .correlationId(current.getCorrelationId())
                .eventType(eventType)
                .stage(TraceStage.NODE_EXECUTION)
                .nodeId(node.getNodeId())
                .capability(node.getCapability())
                .status(status)
                .summary(summary != null && !summary.isBlank() ? summary : "DAG节点状态变更")
                .payloadJson(JSON.toJSONString(payload))
                .payloadSchemaVersion(1)
                .producerId("DagRuntime")
                .occurredAt(Instant.now())
                .actor("SYSTEM:DagRuntime")
                .build();
        TraceAppendResult result = traceStore.append(current, command);
        context.setTraceContext(result.getContext());
    }

    private TraceEventType nodeEventType(NodeExecutionResult result, NodeCompletionResult completion) {
        if (completion != null && completion.isWaitingUserInput()
                || result != null && result.isWaitingUserInput()) {
            return TraceEventType.NODE_WAITING_USER;
        }
        if (result != null && result.getObservation() != null
                && NodeExecutionStatus.SKIPPED.equals(result.getObservation().getStatus())) {
            return TraceEventType.NODE_SKIPPED;
        }
        if (completion != null && !completion.isPassed() || result != null && !result.isSuccess()) {
            return TraceEventType.NODE_FAILED;
        }
        return TraceEventType.NODE_COMPLETED;
    }

    private TraceEventStatus nodeStatus(NodeExecutionResult result, NodeCompletionResult completion) {
        TraceEventType type = nodeEventType(result, completion);
        return switch (type) {
            case NODE_WAITING_USER -> TraceEventStatus.WAITING;
            case NODE_SKIPPED -> TraceEventStatus.SKIPPED;
            case NODE_FAILED -> TraceEventStatus.FAILED;
            default -> TraceEventStatus.SUCCEEDED;
        };
    }

    private String nodeSummary(NodeExecutionResult result, NodeCompletionResult completion) {
        if (completion != null && !completion.isPassed()) {
            return completion.getMessage();
        }
        if (result != null && result.getMessage() != null && !result.getMessage().isBlank()) {
            return result.getMessage();
        }
        if (result != null && result.getObservation() != null) {
            return result.getObservation().getSummary();
        }
        return "DAG节点执行完成";
    }

    private String resolveErrorCode(NodeExecutionResult result, NodeCompletionResult completion) {
        if (completion != null && completion.getErrorCode() != null) {
            return completion.getErrorCode();
        }
        return result != null ? result.getErrorCode() : null;
    }

    private String eventCode(TraceContext context, BoundDagNode node, TraceEventType eventType) {
        String material = safe(context.getEpisodeCode()) + "|" + safe(context.getRequestId())
                + "|" + safe(node.getNodeId()) + "|" + eventType.name();
        return "EV_" + UUID.nameUUIDFromBytes(material.getBytes(StandardCharsets.UTF_8));
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
