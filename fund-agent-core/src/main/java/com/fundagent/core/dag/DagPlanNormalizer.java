package com.fundagent.core.dag;

import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Slf4j
public class DagPlanNormalizer {
    private static final String FINAL_ANSWER_CAPABILITY = "conversation.answer";

    public DagPlan normalize(DagPlan plan) {
        if (plan == null) {
            return null;
        }
        ensureCollections(plan);
        ensureFinalAnswerNode(plan);
        return plan;
    }

    private void ensureCollections(DagPlan plan) {
        if (plan.getNodes() == null) {
            plan.setNodes(new ArrayList<>());
        }
        if (plan.getEdges() == null) {
            plan.setEdges(new ArrayList<>());
        }
        if (plan.getMetadata() == null) {
            plan.setMetadata(new java.util.HashMap<>());
        }
    }

    private void ensureFinalAnswerNode(DagPlan plan) {
        if (plan.getNodes().isEmpty()) {
            return;
        }
        boolean hasFinalAnswer = plan.getNodes().stream()
                .anyMatch(node -> node != null
                        && NodeType.FINAL_ANSWER.equals(node.getNodeType())
                        && FINAL_ANSWER_CAPABILITY.equals(node.getCapability()));
        if (hasFinalAnswer) {
            return;
        }

        String finalNodeId = nextFinalNodeId(plan.getNodes());
        List<String> dependencies = plan.getNodes().stream()
                .filter(node -> node != null && !isBlank(node.getNodeId()))
                .map(DagNode::getNodeId)
                .toList();

        DagNode finalNode = new DagNode();
        finalNode.setNodeId(finalNodeId);
        finalNode.setName("Provide Final Answer");
        finalNode.setNodeType(NodeType.FINAL_ANSWER);
        finalNode.setCapability(FINAL_ANSWER_CAPABILITY);
        finalNode.setInstruction("根据用户问题和所有前序节点Observation，生成最终用户回复。");
        finalNode.setDependsOn(dependencies);
        finalNode.setExpectedOutputs(List.of("final_user_response"));
        finalNode.setAgent("AnswerAgent");
        plan.getNodes().add(finalNode);

        for (String dependency : dependencies) {
            DagEdge edge = new DagEdge();
            edge.setFrom(dependency);
            edge.setTo(finalNodeId);
            edge.setCondition("on_complete");
            plan.getEdges().add(edge);
        }

        log.info("DAG plan normalized: added final answer node, dagId={}, nodeId={}, dependsOn={}",
                plan.getDagId(), finalNodeId, dependencies);
    }

    private String nextFinalNodeId(List<DagNode> nodes) {
        Set<String> nodeIds = new HashSet<>();
        nodes.stream()
                .filter(node -> node != null && node.getNodeId() != null)
                .map(DagNode::getNodeId)
                .forEach(nodeIds::add);
        String base = "final_answer";
        if (!nodeIds.contains(base)) {
            return base;
        }
        int index = 1;
        while (nodeIds.contains(base + "_" + index)) {
            index++;
        }
        return base + "_" + index;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
