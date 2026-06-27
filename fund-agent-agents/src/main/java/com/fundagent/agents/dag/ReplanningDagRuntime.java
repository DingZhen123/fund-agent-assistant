package com.fundagent.agents.dag;

import com.fundagent.core.dag.BoundDagNode;
import com.fundagent.core.dag.BoundDagPlan;
import com.fundagent.core.dag.DagExecutionContext;
import com.fundagent.core.dag.DagPlan;
import com.fundagent.core.dag.DagRunResult;
import com.fundagent.core.dag.DagRunStatus;
import com.fundagent.core.dag.DagRuntime;
import com.fundagent.core.dag.NodeCompletionResult;
import com.fundagent.core.dag.NodeExecutionResult;
import com.fundagent.core.dag.ReplanAction;
import com.fundagent.core.dag.ReplanContext;
import com.fundagent.core.dag.ReplanPatch;
import com.fundagent.core.dag.ReplanPatchValidationResult;
import com.fundagent.core.dag.ReplanPatchValidator;
import com.fundagent.core.dag.ReplanningDagRunResult;
import com.fundagent.core.dag.ToolBinder;
import com.fundagent.core.dag.ToolBindingResult;

import java.util.List;

public class ReplanningDagRuntime {
    private final DagRuntime dagRuntime;
    private final CapabilityDagRePlanner rePlanner;
    private final ReplanPatchValidator patchValidator;
    private final ToolBinder toolBinder;

    public ReplanningDagRuntime(DagRuntime dagRuntime, CapabilityDagRePlanner rePlanner,
                                ReplanPatchValidator patchValidator, ToolBinder toolBinder) {
        this.dagRuntime = dagRuntime;
        this.rePlanner = rePlanner;
        this.patchValidator = patchValidator;
        this.toolBinder = toolBinder;
    }

    public ReplanningDagRunResult run(BoundDagPlan plan, DagExecutionContext context) {
        DagRunResult initialRunResult = dagRuntime.run(plan, context);
        if (!DagRunStatus.FAILED.equals(initialRunResult.getStatus())) {
            ReplanPatch noop = noopPatch("DAG未失败，不需要RePlanner介入");
            ReplanPatchValidationResult validation = patchValidator.validate(noop, buildContext(context, plan, initialRunResult));
            return ReplanningDagRunResult.noReplan(initialRunResult, noop, validation);
        }

        ReplanContext replanContext = buildContext(context, plan, initialRunResult);
        ReplanPatch patch = rePlanner.replan(replanContext);
        if (context != null) {
            context.setTraceContext(replanContext.getTraceContext());
        }
        ReplanPatchValidationResult validation = patchValidator.validate(patch, replanContext);
        if (!validation.isValid() || !ReplanAction.APPEND_NODES.equals(patch.getAction())) {
            return ReplanningDagRunResult.noReplan(initialRunResult, patch, validation);
        }

        BoundDagPlan patchBoundPlan = toolBinder.bind(toPatchDagPlan(plan, patch));
        ToolBindingResult patchBinding = toolBinder.validate(patchBoundPlan);
        if (!patchBinding.isSuccess()) {
            return ReplanningDagRunResult.recovered(initialRunResult, patch, validation,
                    patchBoundPlan, patchBinding, initialRunResult);
        }

        DagRunResult recoveryRunResult = dagRuntime.continueRun(
                patchBoundPlan,
                context,
                initialRunResult.getState(),
                initialRunResult.getNodeCompletionResults(),
                true);
        return ReplanningDagRunResult.recovered(initialRunResult, patch, validation,
                patchBoundPlan, patchBinding, recoveryRunResult);
    }

    private ReplanContext buildContext(DagExecutionContext context, BoundDagPlan plan, DagRunResult runResult) {
        BoundDagNode failedNode = findFailedNode(plan, runResult);
        return ReplanContext.builder()
                .userMessage(context != null ? context.getUserMessage() : null)
                .traceContext(context != null ? context.getTraceContext() : null)
                .boundDagPlan(plan)
                .runResult(runResult)
                .failedNode(failedNode)
                .failedResult(runResult != null ? runResult.getLastResult() : null)
                .failedCompletion(findFailedCompletion(runResult, failedNode))
                .build();
    }

    private BoundDagNode findFailedNode(BoundDagPlan plan, DagRunResult runResult) {
        if (plan == null || plan.getNodes() == null || runResult == null || runResult.getFailedNodeId() == null) {
            return null;
        }
        return plan.getNodes().stream()
                .filter(node -> runResult.getFailedNodeId().equals(node.getNodeId()))
                .findFirst()
                .orElse(null);
    }

    private NodeCompletionResult findFailedCompletion(DagRunResult runResult, BoundDagNode failedNode) {
        if (runResult == null || failedNode == null || runResult.getNodeCompletionResults() == null) {
            return null;
        }
        return runResult.getNodeCompletionResults().get(failedNode.getNodeId());
    }

    private DagPlan toPatchDagPlan(BoundDagPlan originalPlan, ReplanPatch patch) {
        DagPlan patchPlan = new DagPlan();
        patchPlan.setDagId((originalPlan != null ? originalPlan.getDagId() : "dag") + "_replan");
        patchPlan.setGoal("Replan recovery: " + patch.getReason());
        patchPlan.setNodes(patch.getAppendNodes() != null ? patch.getAppendNodes() : List.of());
        patchPlan.setEdges(patch.getAppendEdges() != null ? patch.getAppendEdges() : List.of());
        return patchPlan;
    }

    private ReplanPatch noopPatch(String reason) {
        ReplanPatch patch = new ReplanPatch();
        patch.setAction(ReplanAction.NOOP);
        patch.setReason(reason);
        patch.setAppendNodes(List.of());
        patch.setAppendEdges(List.of());
        patch.setStopMessage("");
        return patch;
    }
}
