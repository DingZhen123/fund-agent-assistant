package com.fundagent.core.dag;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ReplanContext {
    private String userMessage;
    private BoundDagPlan boundDagPlan;
    private DagRunResult runResult;
    private BoundDagNode failedNode;
    private NodeExecutionResult failedResult;
    private NodeCompletionResult failedCompletion;
}
