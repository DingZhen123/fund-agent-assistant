package com.fundagent.core.dag;

import com.alibaba.fastjson2.annotation.JSONField;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ReplanningDagRunResult {
    @JSONField(name = "initial_run_result")
    private DagRunResult initialRunResult;

    @JSONField(name = "replan_patch")
    private ReplanPatch replanPatch;

    @JSONField(name = "replan_validation")
    private ReplanPatchValidationResult replanValidation;

    @JSONField(name = "patch_bound_dag_plan")
    private BoundDagPlan patchBoundDagPlan;

    @JSONField(name = "patch_binding")
    private ToolBindingResult patchBinding;

    @JSONField(name = "recovery_run_result")
    private DagRunResult recoveryRunResult;

    @JSONField(name = "final_result")
    private DagRunResult finalResult;

    public static ReplanningDagRunResult noReplan(DagRunResult result, ReplanPatch patch,
                                                  ReplanPatchValidationResult validation) {
        return new ReplanningDagRunResult(result, patch, validation, null, null, null, result);
    }

    public static ReplanningDagRunResult recovered(DagRunResult initialRunResult, ReplanPatch patch,
                                                   ReplanPatchValidationResult validation,
                                                   BoundDagPlan patchBoundDagPlan,
                                                   ToolBindingResult patchBinding,
                                                   DagRunResult recoveryRunResult) {
        return new ReplanningDagRunResult(initialRunResult, patch, validation, patchBoundDagPlan,
                patchBinding, recoveryRunResult, recoveryRunResult);
    }
}
