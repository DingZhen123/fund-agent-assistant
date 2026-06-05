package com.fundagent.core.dag;

public interface NodeExecutor {
    boolean supports(BoundDagNode node);

    NodeExecutionResult execute(BoundDagNode node, DagGraphState state, DagExecutionContext context);
}
