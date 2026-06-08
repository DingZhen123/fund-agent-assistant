package com.fundagent.core.dag;

import lombok.extern.slf4j.Slf4j;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
public class DagRuntime {
    private final NodeRouter nodeRouter;
    private final NodeCompletionChecker nodeCompletionChecker;
    private final FinalDagVerifier finalDagVerifier;

    public DagRuntime(NodeRouter nodeRouter, NodeCompletionChecker nodeCompletionChecker,
                      FinalDagVerifier finalDagVerifier) {
        this.nodeRouter = nodeRouter;
        this.nodeCompletionChecker = nodeCompletionChecker;
        this.finalDagVerifier = finalDagVerifier;
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
            lastResult = executor.execute(node, state, context);
            state.addObservation(lastResult.getObservation());
            NodeCompletionResult completion = nodeCompletionChecker.check(node, lastResult, state);
            completionResults.put(node.getNodeId(), completion);
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
}
