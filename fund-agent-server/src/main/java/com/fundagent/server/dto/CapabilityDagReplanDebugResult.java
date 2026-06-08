package com.fundagent.server.dto;

import com.fundagent.core.dag.BoundDagNode;
import com.fundagent.core.dag.BoundDagPlan;
import com.fundagent.core.dag.DagPlan;
import com.fundagent.core.dag.DagPlanValidationResult;
import com.fundagent.core.dag.DagRunResult;
import com.fundagent.core.dag.NodeCompletionResult;
import com.fundagent.core.dag.NodeExecutionResult;
import com.fundagent.core.dag.ReplanPatch;
import com.fundagent.core.dag.ReplanPatchValidationResult;
import com.fundagent.core.dag.ToolBindingResult;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class CapabilityDagReplanDebugResult {
    private DagPlan dagPlan;
    private DagPlanValidationResult validation;
    private BoundDagPlan boundDagPlan;
    private ToolBindingResult binding;
    private DagRunResult runResult;
    private BoundDagNode failedNode;
    private NodeExecutionResult failedResult;
    private NodeCompletionResult failedCompletion;
    private ReplanPatch replanPatch;
    private ReplanPatchValidationResult replanValidation;
}
