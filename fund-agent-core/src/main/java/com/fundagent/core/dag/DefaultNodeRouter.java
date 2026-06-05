package com.fundagent.core.dag;

import java.util.Comparator;
import java.util.List;

public class DefaultNodeRouter implements NodeRouter {
    private final List<NodeExecutor> executors;

    public DefaultNodeRouter(List<NodeExecutor> executors) {
        this.executors = executors == null
                ? List.of()
                : executors.stream()
                .sorted(Comparator.comparing(executor -> executor.getClass().getSimpleName()))
                .toList();
    }

    @Override
    public NodeExecutor route(BoundDagNode node) {
        return executors.stream()
                .filter(executor -> executor.supports(node))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("没有可执行当前节点的NodeExecutor: "
                        + (node != null ? node.getNodeId() : "null")));
    }
}
