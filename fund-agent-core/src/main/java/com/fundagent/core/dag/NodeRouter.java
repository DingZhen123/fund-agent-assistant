package com.fundagent.core.dag;

public interface NodeRouter {
    NodeExecutor route(BoundDagNode node);
}
