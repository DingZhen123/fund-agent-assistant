package com.fundagent.core.dag;

public interface ToolBinder {
    BoundDagPlan bind(DagPlan dagPlan);

    ToolBindingResult validate(BoundDagPlan boundDagPlan);
}
