package com.fundagent.server.dto;

import com.fundagent.core.dag.BoundDagPlan;
import com.fundagent.core.dag.DagPlan;
import com.fundagent.core.dag.DagPlanValidationResult;
import com.fundagent.core.dag.ToolBindingResult;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class CapabilityDagDebugResult {
    private DagPlan dagPlan;
    private DagPlanValidationResult validation;
    private BoundDagPlan boundDagPlan;
    private ToolBindingResult binding;
}
