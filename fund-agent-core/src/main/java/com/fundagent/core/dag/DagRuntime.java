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
        Map<String, NodeCompletionResult> completionResults = new LinkedHashMap<>();
        if (plan == null) {
            return DagRunResult.failed(state, null, "BOUND_DAG_NULL", "绑定后的DAG为空",
                    completionResults, null);
        }
        if (plan.getNodes() == null || plan.getNodes().isEmpty()) {
            return DagRunResult.failed(state, null, "EMPTY_BOUND_NODES", "绑定后的DAG节点不能为空",
                    completionResults, null);
        }

        NodeExecutionResult lastResult = null;
        for (BoundDagNode node : plan.getNodes()) {
            if (!state.dependenciesCompleted(node.getDependsOn())) {
                return DagRunResult.failed(state, lastResult, "DEPENDENCY_NOT_COMPLETED",
                        "节点依赖尚未成功完成: " + node.getNodeId(), completionResults, null);
            }

            NodeExecutor executor;
            try {
                executor = nodeRouter.route(node);
            } catch (Exception e) {
                return DagRunResult.failed(state, lastResult, "NODE_EXECUTOR_NOT_FOUND", e.getMessage(),
                        completionResults, null);
            }

            lastResult = executor.execute(node, state, context);
            state.addObservation(lastResult.getObservation());
            NodeCompletionResult completion = nodeCompletionChecker.check(node, lastResult, state);
            completionResults.put(node.getNodeId(), completion);
            log.info("DAG node executed: dagId={}, nodeId={}, status={}, success={}",
                    state.getDagId(),
                    node.getNodeId(),
                    lastResult.getObservation() != null ? lastResult.getObservation().getStatus() : null,
                    lastResult.isSuccess());

            if (!completion.isPassed()) {
                return DagRunResult.failed(state, lastResult, completion.getErrorCode(), completion.getMessage(),
                        completionResults, null);
            }

            if (completion.isWaitingUserInput() || lastResult.isWaitingUserInput()) {
                return DagRunResult.waiting(state, lastResult, completionResults);
            }
            if (!lastResult.isSuccess()) {
                return DagRunResult.failed(state, lastResult, lastResult.getErrorCode(), lastResult.getMessage(),
                        completionResults, null);
            }
        }

        DagRunResult completed = DagRunResult.completed(state, lastResult, completionResults, null);
        FinalVerificationResult finalVerification = finalDagVerifier.verify(plan, completed);
        if (!finalVerification.isPassed()) {
            return DagRunResult.failed(state, lastResult, finalVerification.getErrorCode(),
                    finalVerification.getMessage(), completionResults, finalVerification);
        }
        return DagRunResult.completed(state, lastResult, completionResults, finalVerification);
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
