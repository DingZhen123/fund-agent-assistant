package com.fundagent.agents.dag;

import com.fundagent.core.dag.BoundDagNode;
import com.fundagent.core.dag.DagExecutionContext;
import com.fundagent.core.dag.DagGraphState;
import com.fundagent.core.dag.NodeExecutionResult;
import com.fundagent.core.dag.NodeExecutionStatus;
import com.fundagent.core.dag.NodeExecutor;
import com.fundagent.core.dag.NodeObservation;
import com.fundagent.core.dag.NodeType;

import java.util.HashMap;
import java.util.Map;

public class AskUserNodeExecutor implements NodeExecutor {

    @Override
    public boolean supports(BoundDagNode node) {
        return node != null && NodeType.ASK_USER.equals(node.getNodeType());
    }

    @Override
    public NodeExecutionResult execute(BoundDagNode node, DagGraphState state, DagExecutionContext context) {
        long start = System.currentTimeMillis();
        if (!supports(node)) {
            NodeObservation observation = failedObservation(node, "UNSUPPORTED_NODE",
                    "AskUserNodeExecutor不支持当前节点", System.currentTimeMillis() - start);
            return NodeExecutionResult.failed(observation, "UNSUPPORTED_NODE", observation.getError());
        }

        Map<String, Object> outputs = new HashMap<>();
        outputs.put("question", node.getInstruction());
        NodeObservation observation = NodeObservation.builder()
                .nodeId(node.getNodeId())
                .nodeType(node.getNodeType())
                .capability(node.getCapability())
                .status(NodeExecutionStatus.WAITING_USER_INPUT)
                .summary(node.getInstruction())
                .outputs(outputs)
                .elapsedMs(System.currentTimeMillis() - start)
                .build();
        return NodeExecutionResult.waiting(observation, node.getInstruction());
    }

    private NodeObservation failedObservation(BoundDagNode node, String errorCode, String error, long elapsedMs) {
        return NodeObservation.builder()
                .nodeId(node != null ? node.getNodeId() : null)
                .nodeType(node != null ? node.getNodeType() : null)
                .capability(node != null ? node.getCapability() : null)
                .status(NodeExecutionStatus.FAILED)
                .summary(error)
                .errorCode(errorCode)
                .error(error)
                .elapsedMs(elapsedMs)
                .build();
    }
}
