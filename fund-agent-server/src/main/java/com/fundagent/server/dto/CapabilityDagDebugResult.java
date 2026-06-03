package com.fundagent.server.dto;

import com.fundagent.core.dag.DagPlan;
import com.fundagent.core.dag.DagPlanValidationResult;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class CapabilityDagDebugResult {
    private DagPlan dagPlan;
    private DagPlanValidationResult validation;
}
