package com.fundagent.agents.dag;

import com.fundagent.core.dag.DagPlan;
import com.fundagent.core.trace.TraceContext;
import lombok.Value;

@Value
public class CapabilityDagPlanningResult {
    DagPlan plan;
    TraceContext traceContext;
}
