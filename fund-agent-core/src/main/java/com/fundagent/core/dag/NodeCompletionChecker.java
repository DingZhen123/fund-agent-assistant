package com.fundagent.core.dag;

public interface NodeCompletionChecker {
    NodeCompletionResult check(BoundDagNode node, NodeExecutionResult result, DagGraphState state);
}
