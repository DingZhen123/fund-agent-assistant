package com.fundagent.core.dag;

import com.fundagent.core.trace.TraceContext;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ReplanContext {
    private String userMessage;
    private TraceContext traceContext;
    private BoundDagPlan boundDagPlan;
    private DagRunResult runResult;
    private BoundDagNode failedNode;
    private NodeExecutionResult failedResult;
    private NodeCompletionResult failedCompletion;
}
