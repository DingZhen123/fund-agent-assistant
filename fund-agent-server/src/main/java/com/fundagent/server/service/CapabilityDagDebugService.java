package com.fundagent.server.service;

import com.fundagent.agents.dag.CapabilityDagPlanner;
import com.fundagent.core.dag.DagPlan;
import com.fundagent.core.memory.Memory;
import org.springframework.stereotype.Service;

@Service
public class CapabilityDagDebugService {
    private final CapabilityDagPlanner capabilityDagPlanner;

    public CapabilityDagDebugService(CapabilityDagPlanner capabilityDagPlanner) {
        this.capabilityDagPlanner = capabilityDagPlanner;
    }

    public DagPlan plan(String conversationId, String message) {
        String safeConversationId = conversationId != null && !conversationId.isBlank()
                ? conversationId
                : "debug-capability-dag";
        return capabilityDagPlanner.plan(new Memory(safeConversationId), message);
    }
}
