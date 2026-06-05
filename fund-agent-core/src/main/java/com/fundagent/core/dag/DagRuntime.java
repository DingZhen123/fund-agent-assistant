package com.fundagent.core.dag;

import lombok.extern.slf4j.Slf4j;

import java.util.List;

@Slf4j
public class DagRuntime {
    private final NodeRouter nodeRouter;

    public DagRuntime(NodeRouter nodeRouter) {
        this.nodeRouter = nodeRouter;
    }

    public DagRunResult run(BoundDagPlan plan, DagExecutionContext context) {
        DagGraphState state = initializeState(plan, context);
        if (plan == null) {
            return DagRunResult.failed(state, null, "BOUND_DAG_NULL", "绑定后的DAG为空");
        }
        if (plan.getNodes() == null || plan.getNodes().isEmpty()) {
            return DagRunResult.failed(state, null, "EMPTY_BOUND_NODES", "绑定后的DAG节点不能为空");
        }

        NodeExecutionResult lastResult = null;
        for (BoundDagNode node : plan.getNodes()) {
            if (!state.dependenciesCompleted(node.getDependsOn())) {
                return DagRunResult.failed(state, lastResult, "DEPENDENCY_NOT_COMPLETED",
                        "节点依赖尚未成功完成: " + node.getNodeId());
            }

            NodeExecutor executor;
            try {
                executor = nodeRouter.route(node);
            } catch (Exception e) {
                return DagRunResult.failed(state, lastResult, "NODE_EXECUTOR_NOT_FOUND", e.getMessage());
            }

            lastResult = executor.execute(node, state, context);
            state.addObservation(lastResult.getObservation());
            log.info("DAG node executed: dagId={}, nodeId={}, status={}, success={}",
                    state.getDagId(),
                    node.getNodeId(),
                    lastResult.getObservation() != null ? lastResult.getObservation().getStatus() : null,
                    lastResult.isSuccess());

            if (lastResult.isWaitingUserInput()) {
                return DagRunResult.waiting(state, lastResult);
            }
            if (!lastResult.isSuccess()) {
                return DagRunResult.failed(state, lastResult, lastResult.getErrorCode(), lastResult.getMessage());
            }
        }

        return DagRunResult.completed(state, lastResult);
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
