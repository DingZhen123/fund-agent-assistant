package com.fundagent.server.service;

import com.fundagent.agents.dag.CapabilityDagPlanner;
import com.fundagent.core.dag.BoundDagPlan;
import com.fundagent.core.dag.DagPlan;
import com.fundagent.core.dag.DagPlanValidationResult;
import com.fundagent.core.dag.DagPlanValidator;
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

    public CapabilityDagDebugService(CapabilityDagPlanner capabilityDagPlanner,
                                     DagPlanValidator dagPlanValidator,
                                     ToolBinder toolBinder) {
        this.capabilityDagPlanner = capabilityDagPlanner;
        this.dagPlanValidator = dagPlanValidator;
        this.toolBinder = toolBinder;
    }

    public CapabilityDagDebugResult plan(String conversationId, String message) {
        String safeConversationId = conversationId != null && !conversationId.isBlank()
                ? conversationId
                : "debug-capability-dag";
        DagPlan dagPlan = capabilityDagPlanner.plan(new Memory(safeConversationId), message);
        DagPlanValidationResult validation = dagPlanValidator.validate(dagPlan);
        BoundDagPlan boundDagPlan = toolBinder.bind(dagPlan);
        ToolBindingResult binding = toolBinder.validate(boundDagPlan);
        return new CapabilityDagDebugResult(dagPlan, validation, boundDagPlan, binding);
    }
}
