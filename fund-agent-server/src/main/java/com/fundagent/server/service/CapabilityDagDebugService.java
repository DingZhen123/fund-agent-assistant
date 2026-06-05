package com.fundagent.server.service;

import com.fundagent.agents.dag.CapabilityDagPlanner;
import com.fundagent.core.dag.BoundDagPlan;
import com.fundagent.core.dag.DagExecutionContext;
import com.fundagent.core.dag.DagPlan;
import com.fundagent.core.dag.DagPlanValidationResult;
import com.fundagent.core.dag.DagPlanValidator;
import com.fundagent.core.dag.DagRunResult;
import com.fundagent.core.dag.DagRuntime;
import com.fundagent.core.dag.ToolBinder;
import com.fundagent.core.dag.ToolBindingResult;
import com.fundagent.core.memory.Memory;
import com.fundagent.server.dto.CapabilityDagDebugResult;
import org.springframework.stereotype.Service;

@Service
public class CapabilityDagDebugService {
    private final CapabilityDagPlanner capabilityDagPlanner;
    private final DagPlanValidator dagPlanValidator;
    private final ToolBinder toolBinder;
    private final DagRuntime dagRuntime;

    public CapabilityDagDebugService(CapabilityDagPlanner capabilityDagPlanner,
                                     DagPlanValidator dagPlanValidator,
                                     ToolBinder toolBinder,
                                     DagRuntime dagRuntime) {
        this.capabilityDagPlanner = capabilityDagPlanner;
        this.dagPlanValidator = dagPlanValidator;
        this.toolBinder = toolBinder;
        this.dagRuntime = dagRuntime;
    }

    public CapabilityDagDebugResult plan(String conversationId, String message) {
        return build(conversationId, message, false);
    }

    public CapabilityDagDebugResult run(String conversationId, String message, String userId) {
        return build(conversationId, message, true, userId);
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
}
