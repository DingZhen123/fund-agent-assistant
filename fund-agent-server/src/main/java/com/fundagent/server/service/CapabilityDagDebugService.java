package com.fundagent.server.service;

import com.fundagent.agents.dag.CapabilityDagPlanner;
import com.fundagent.core.dag.DagPlan;
import com.fundagent.core.dag.DagPlanValidationResult;
import com.fundagent.core.dag.DagPlanValidator;
import com.fundagent.core.memory.Memory;
import com.fundagent.server.dto.CapabilityDagDebugResult;
import org.springframework.stereotype.Service;

@Service
public class CapabilityDagDebugService {
    private final CapabilityDagPlanner capabilityDagPlanner;
    private final DagPlanValidator dagPlanValidator;

    public CapabilityDagDebugService(CapabilityDagPlanner capabilityDagPlanner,
                                     DagPlanValidator dagPlanValidator) {
        this.capabilityDagPlanner = capabilityDagPlanner;
        this.dagPlanValidator = dagPlanValidator;
    }

    public CapabilityDagDebugResult plan(String conversationId, String message) {
        String safeConversationId = conversationId != null && !conversationId.isBlank()
                ? conversationId
                : "debug-capability-dag";
        DagPlan dagPlan = capabilityDagPlanner.plan(new Memory(safeConversationId), message);
        DagPlanValidationResult validation = dagPlanValidator.validate(dagPlan);
        return new CapabilityDagDebugResult(dagPlan, validation);
    }
}
