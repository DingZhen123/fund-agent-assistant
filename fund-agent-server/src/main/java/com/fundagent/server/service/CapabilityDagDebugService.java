package com.fundagent.server.service;

import com.fundagent.agents.dag.CapabilityDagRePlanner;
import com.fundagent.agents.dag.CapabilityDagPlanner;
import com.fundagent.agents.dag.ReplanningDagRuntime;
import com.fundagent.core.dag.BoundDagNode;
import com.fundagent.core.dag.BoundDagPlan;
import com.fundagent.core.dag.DagExecutionContext;
import com.fundagent.core.dag.DagPlan;
import com.fundagent.core.dag.DagPlanValidationResult;
import com.fundagent.core.dag.DagPlanValidator;
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
import com.fundagent.core.memory.Memory;
import com.fundagent.server.dto.CapabilityDagDebugResult;
import com.fundagent.server.dto.CapabilityDagReplanDebugResult;
import com.fundagent.server.dto.CapabilityDagRunWithReplanDebugResult;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class CapabilityDagDebugService {
    private final CapabilityDagPlanner capabilityDagPlanner;
    private final CapabilityDagRePlanner capabilityDagRePlanner;
    private final DagPlanValidator dagPlanValidator;
    private final ToolBinder toolBinder;
    private final DagRuntime dagRuntime;
    private final ReplanningDagRuntime replanningDagRuntime;
    private final ReplanPatchValidator replanPatchValidator;

    public CapabilityDagDebugService(CapabilityDagPlanner capabilityDagPlanner,
                                     CapabilityDagRePlanner capabilityDagRePlanner,
                                     DagPlanValidator dagPlanValidator,
                                     ToolBinder toolBinder,
                                     DagRuntime dagRuntime,
                                     ReplanningDagRuntime replanningDagRuntime,
                                     ReplanPatchValidator replanPatchValidator) {
        this.capabilityDagPlanner = capabilityDagPlanner;
        this.capabilityDagRePlanner = capabilityDagRePlanner;
        this.dagPlanValidator = dagPlanValidator;
        this.toolBinder = toolBinder;
        this.dagRuntime = dagRuntime;
        this.replanningDagRuntime = replanningDagRuntime;
        this.replanPatchValidator = replanPatchValidator;
    }

    public CapabilityDagDebugResult plan(String conversationId, String message) {
        return build(conversationId, message, false);
    }

    public CapabilityDagDebugResult run(String conversationId, String message, String userId) {
        return build(conversationId, message, true, userId);
    }

    public CapabilityDagReplanDebugResult replan(String conversationId, String message, String userId) {
        String safeConversationId = conversationId != null && !conversationId.isBlank()
                ? conversationId
                : "debug-capability-dag";
        DagPlan dagPlan = capabilityDagPlanner.plan(new Memory(safeConversationId), message);
        DagPlanValidationResult validation = dagPlanValidator.validate(dagPlan);
        BoundDagPlan boundDagPlan = toolBinder.bind(dagPlan);
        ToolBindingResult binding = toolBinder.validate(boundDagPlan);
        DagRunResult runResult = null;
        if (validation.isValid() && binding.isSuccess()) {
            runResult = dagRuntime.run(boundDagPlan, DagExecutionContext.builder()
                    .dagId(boundDagPlan.getDagId())
                    .conversationId(safeConversationId)
                    .userId(userId)
                    .userMessage(message)
                    .build());
        }

        BoundDagNode failedNode = findFailedNode(boundDagPlan, runResult);
        NodeExecutionResult failedResult = runResult != null ? runResult.getLastResult() : null;
        NodeCompletionResult failedCompletion = findFailedCompletion(runResult, failedNode);
        ReplanContext context = ReplanContext.builder()
                .userMessage(message)
                .boundDagPlan(boundDagPlan)
                .runResult(runResult)
                .failedNode(failedNode)
                .failedResult(failedResult)
                .failedCompletion(failedCompletion)
                .build();

        ReplanPatch patch = shouldReplan(runResult)
                ? capabilityDagRePlanner.replan(context)
                : noopPatch("DAG未失败，不需要RePlanner介入");
        ReplanPatchValidationResult replanValidation = replanPatchValidator.validate(patch, context);

        return new CapabilityDagReplanDebugResult(dagPlan, validation, boundDagPlan, binding, runResult,
                failedNode, failedResult, failedCompletion, patch, replanValidation);
    }

    public CapabilityDagRunWithReplanDebugResult runWithReplan(String conversationId, String message, String userId) {
        String safeConversationId = conversationId != null && !conversationId.isBlank()
                ? conversationId
                : "debug-capability-dag";
        DagPlan dagPlan = capabilityDagPlanner.plan(new Memory(safeConversationId), message);
        DagPlanValidationResult validation = dagPlanValidator.validate(dagPlan);
        BoundDagPlan boundDagPlan = toolBinder.bind(dagPlan);
        ToolBindingResult binding = toolBinder.validate(boundDagPlan);
        ReplanningDagRunResult replanningRunResult = null;
        if (validation.isValid() && binding.isSuccess()) {
            replanningRunResult = replanningDagRuntime.run(boundDagPlan, DagExecutionContext.builder()
                    .dagId(boundDagPlan.getDagId())
                    .conversationId(safeConversationId)
                    .userId(userId)
                    .userMessage(message)
                    .build());
        }
        return new CapabilityDagRunWithReplanDebugResult(dagPlan, validation, boundDagPlan, binding, replanningRunResult);
    }

    private CapabilityDagDebugResult build(String conversationId, String message, boolean run) {
        return build(conversationId, message, run, "debug-user");
    }

    private CapabilityDagDebugResult build(String conversationId, String message, boolean run, String userId) {
        String safeConversationId = conversationId != null && !conversationId.isBlank()
                ? conversationId
                : "debug-capability-dag";
        DagPlan dagPlan = capabilityDagPlanner.plan(new Memory(safeConversationId), message);
        DagPlanValidationResult validation = dagPlanValidator.validate(dagPlan);
        BoundDagPlan boundDagPlan = toolBinder.bind(dagPlan);
        ToolBindingResult binding = toolBinder.validate(boundDagPlan);
        DagRunResult runResult = null;
        if (run && validation.isValid() && binding.isSuccess()) {
            runResult = dagRuntime.run(boundDagPlan, DagExecutionContext.builder()
                    .dagId(boundDagPlan.getDagId())
                    .conversationId(safeConversationId)
                    .userId(userId)
                    .userMessage(message)
                    .build());
        }
        return new CapabilityDagDebugResult(dagPlan, validation, boundDagPlan, binding, runResult);
    }

    private boolean shouldReplan(DagRunResult runResult) {
        return runResult != null && DagRunStatus.FAILED.equals(runResult.getStatus());
    }

    private BoundDagNode findFailedNode(BoundDagPlan boundDagPlan, DagRunResult runResult) {
        if (boundDagPlan == null || boundDagPlan.getNodes() == null || runResult == null) {
            return null;
        }
        String failedNodeId = runResult.getFailedNodeId();
        if (failedNodeId == null && runResult.getLastResult() != null
                && runResult.getLastResult().getObservation() != null) {
            failedNodeId = runResult.getLastResult().getObservation().getNodeId();
        }
        if (failedNodeId == null) {
            return null;
        }
        String targetNodeId = failedNodeId;
        return boundDagPlan.getNodes().stream()
                .filter(node -> targetNodeId.equals(node.getNodeId()))
                .findFirst()
                .orElse(null);
    }

    private NodeCompletionResult findFailedCompletion(DagRunResult runResult, BoundDagNode failedNode) {
        if (runResult == null || failedNode == null || runResult.getNodeCompletionResults() == null) {
            return null;
        }
        return runResult.getNodeCompletionResults().get(failedNode.getNodeId());
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
