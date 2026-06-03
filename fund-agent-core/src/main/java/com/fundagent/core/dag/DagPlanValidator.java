package com.fundagent.core.dag;

import com.fundagent.core.capability.CapabilityCatalog;
import com.fundagent.core.capability.CapabilityDefinition;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class DagPlanValidator {
    private static final String FINAL_ANSWER_CAPABILITY = "conversation.answer";

    private final CapabilityCatalog capabilityCatalog;

    public DagPlanValidator(CapabilityCatalog capabilityCatalog) {
        this.capabilityCatalog = capabilityCatalog;
    }

    public DagPlanValidationResult validate(DagPlan plan) {
        if (plan == null) {
            return DagPlanValidationResult.error("DAG_PLAN_NULL", "DAG计划为空");
        }
        if (isBlank(plan.getGoal())) {
            return DagPlanValidationResult.error("MISSING_GOAL", "DAG目标不能为空");
        }

        List<DagNode> nodes = plan.getNodes();
        if (nodes == null || nodes.isEmpty()) {
            return DagPlanValidationResult.error("EMPTY_NODES", "DAG节点不能为空");
        }

        Set<String> seenNodeIds = new HashSet<>();
        boolean hasFinalAnswer = false;
        for (DagNode node : nodes) {
            DagPlanValidationResult result = validateNode(node, seenNodeIds);
            if (!result.isValid()) return result;
            if (NodeType.FINAL_ANSWER.equals(node.getNodeType())
                    && FINAL_ANSWER_CAPABILITY.equals(node.getCapability())) {
                hasFinalAnswer = true;
            }
            seenNodeIds.add(node.getNodeId());
        }

        if (!hasFinalAnswer) {
            return DagPlanValidationResult.error("MISSING_FINAL_ANSWER",
                    "DAG计划必须包含conversation.answer对应的FINAL_ANSWER节点");
        }

        return validateEdges(plan.getEdges(), seenNodeIds);
    }

    private DagPlanValidationResult validateNode(DagNode node, Set<String> seenNodeIds) {
        if (node == null) {
            return DagPlanValidationResult.error("NODE_NULL", "DAG节点不能为空");
        }
        if (isBlank(node.getNodeId())) {
            return DagPlanValidationResult.error("MISSING_NODE_ID", "node_id不能为空");
        }
        if (seenNodeIds.contains(node.getNodeId())) {
            return DagPlanValidationResult.error("DUPLICATE_NODE_ID", "重复的node_id: " + node.getNodeId());
        }
        if (node.getNodeType() == null) {
            return DagPlanValidationResult.error("MISSING_NODE_TYPE", "节点" + node.getNodeId() + "缺少node_type");
        }
        if (isBlank(node.getCapability())) {
            return DagPlanValidationResult.error("MISSING_CAPABILITY", "节点" + node.getNodeId() + "缺少capability");
        }
        if (isBlank(node.getInstruction())) {
            return DagPlanValidationResult.error("MISSING_INSTRUCTION", "节点" + node.getNodeId() + "缺少instruction");
        }

        DagPlanValidationResult capabilityResult = validateCapability(node);
        if (!capabilityResult.isValid()) return capabilityResult;

        return validateDependencies(node, seenNodeIds);
    }

    private DagPlanValidationResult validateCapability(DagNode node) {
        return capabilityCatalog.getCapability(node.getCapability())
                .map(capability -> validateCapabilityDefinition(node, capability))
                .orElseGet(() -> DagPlanValidationResult.error("UNKNOWN_CAPABILITY",
                        "未知能力: " + node.getCapability()));
    }

    private DagPlanValidationResult validateCapabilityDefinition(DagNode node, CapabilityDefinition capability) {
        if (!capability.isEnabled()) {
            return DagPlanValidationResult.error("CAPABILITY_DISABLED", "能力已禁用: " + node.getCapability());
        }
        if (isBlank(capability.getNodeType())) {
            return DagPlanValidationResult.error("CAPABILITY_NODE_TYPE_MISSING",
                    "能力缺少nodeType: " + node.getCapability());
        }
        String expectedNodeType = capability.getNodeType().trim().toUpperCase(Locale.ROOT);
        if (!node.getNodeType().name().equals(expectedNodeType)) {
            return DagPlanValidationResult.error("NODE_TYPE_CAPABILITY_MISMATCH",
                    "节点" + node.getNodeId() + "的node_type与能力不匹配: "
                            + node.getNodeType().name() + " != " + expectedNodeType);
        }
        return DagPlanValidationResult.ok();
    }

    private DagPlanValidationResult validateDependencies(DagNode node, Set<String> seenNodeIds) {
        if (node.getDependsOn() == null) {
            return DagPlanValidationResult.ok();
        }
        for (String dependency : node.getDependsOn()) {
            if (isBlank(dependency)) {
                return DagPlanValidationResult.error("MISSING_DEPENDENCY",
                        "节点" + node.getNodeId() + "存在空依赖");
            }
            if (dependency.equals(node.getNodeId())) {
                return DagPlanValidationResult.error("SELF_DEPENDENCY",
                        "节点不能依赖自身: " + node.getNodeId());
            }
            if (!seenNodeIds.contains(dependency)) {
                return DagPlanValidationResult.error("UNSUPPORTED_DEPENDENCY",
                        "第一版仅支持串行拓扑顺序，节点" + node.getNodeId() + "依赖未完成节点: " + dependency);
            }
        }
        return DagPlanValidationResult.ok();
    }

    private DagPlanValidationResult validateEdges(List<DagEdge> edges, Set<String> nodeIds) {
        if (edges == null || edges.isEmpty()) {
            return DagPlanValidationResult.ok();
        }
        for (DagEdge edge : edges) {
            if (edge == null) {
                return DagPlanValidationResult.error("EDGE_NULL", "DAG边不能为空");
            }
            if (isBlank(edge.getFrom())) {
                return DagPlanValidationResult.error("MISSING_EDGE_FROM", "DAG边缺少from");
            }
            if (isBlank(edge.getTo())) {
                return DagPlanValidationResult.error("MISSING_EDGE_TO", "DAG边缺少to");
            }
            if (!nodeIds.contains(edge.getFrom())) {
                return DagPlanValidationResult.error("UNKNOWN_EDGE_FROM", "DAG边引用未知from节点: " + edge.getFrom());
            }
            if (!nodeIds.contains(edge.getTo())) {
                return DagPlanValidationResult.error("UNKNOWN_EDGE_TO", "DAG边引用未知to节点: " + edge.getTo());
            }
        }
        return DagPlanValidationResult.ok();
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
