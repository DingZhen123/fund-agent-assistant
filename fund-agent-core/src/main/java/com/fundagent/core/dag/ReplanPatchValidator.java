package com.fundagent.core.dag;

import com.fundagent.core.capability.CapabilityCatalog;
import com.fundagent.core.capability.CapabilityDefinition;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class ReplanPatchValidator {
    private final CapabilityCatalog capabilityCatalog;

    public ReplanPatchValidator(CapabilityCatalog capabilityCatalog) {
        this.capabilityCatalog = capabilityCatalog;
    }

    public ReplanPatchValidationResult validate(ReplanPatch patch, ReplanContext context) {
        if (patch == null) {
            return ReplanPatchValidationResult.error("REPLAN_PATCH_NULL", "ReplanPatch不能为空");
        }
        if (patch.getAction() == null) {
            return ReplanPatchValidationResult.error("MISSING_REPLAN_ACTION", "ReplanPatch缺少action");
        }
        if (isBlank(patch.getReason())) {
            return ReplanPatchValidationResult.error("MISSING_REPLAN_REASON", "ReplanPatch缺少reason");
        }
        return switch (patch.getAction()) {
            case NOOP -> validateNoop(patch);
            case FAIL -> validateFail(patch);
            case APPEND_NODES -> validateAppendNodes(patch, context);
        };
    }

    private ReplanPatchValidationResult validateNoop(ReplanPatch patch) {
        if (patch.getAppendNodes() != null && !patch.getAppendNodes().isEmpty()) {
            return ReplanPatchValidationResult.error("UNEXPECTED_APPEND_NODES", "NOOP不能追加节点");
        }
        return ReplanPatchValidationResult.ok();
    }

    private ReplanPatchValidationResult validateFail(ReplanPatch patch) {
        if (isBlank(patch.getStopMessage())) {
            return ReplanPatchValidationResult.error("MISSING_STOP_MESSAGE", "FAIL必须提供stop_message");
        }
        return ReplanPatchValidationResult.ok();
    }

    private ReplanPatchValidationResult validateAppendNodes(ReplanPatch patch, ReplanContext context) {
        List<DagNode> appendNodes = patch.getAppendNodes();
        if (appendNodes == null || appendNodes.isEmpty()) {
            return ReplanPatchValidationResult.error("EMPTY_APPEND_NODES", "APPEND_NODES必须包含追加节点");
        }

        Set<String> availableNodeIds = collectCompletedNodeIds(context);
        Set<String> existingNodeIds = collectExistingNodeIds(context);
        Set<String> appendNodeIds = new HashSet<>();
        boolean hasFinalAnswer = false;
        for (DagNode node : appendNodes) {
            ReplanPatchValidationResult result = validateAppendNode(node, availableNodeIds, existingNodeIds, appendNodeIds);
            if (!result.isValid()) return result;
            appendNodeIds.add(node.getNodeId());
            availableNodeIds.add(node.getNodeId());
            if (NodeType.FINAL_ANSWER.equals(node.getNodeType())) {
                hasFinalAnswer = true;
            }
        }

        if (!hasFinalAnswer) {
            return ReplanPatchValidationResult.error("MISSING_APPEND_FINAL_ANSWER",
                    "追加恢复节点必须包含FINAL_ANSWER节点");
        }
        return validateAppendEdges(patch, availableNodeIds);
    }

    private ReplanPatchValidationResult validateAppendNode(DagNode node, Set<String> availableNodeIds,
                                                           Set<String> existingNodeIds,
                                                           Set<String> appendNodeIds) {
        if (node == null) {
            return ReplanPatchValidationResult.error("APPEND_NODE_NULL", "追加节点不能为空");
        }
        if (isBlank(node.getNodeId())) {
            return ReplanPatchValidationResult.error("MISSING_NODE_ID", "追加节点node_id不能为空");
        }
        if (existingNodeIds.contains(node.getNodeId()) || appendNodeIds.contains(node.getNodeId())) {
            return ReplanPatchValidationResult.error("DUPLICATE_NODE_ID", "重复的追加节点node_id: " + node.getNodeId());
        }
        if (node.getNodeType() == null) {
            return ReplanPatchValidationResult.error("MISSING_NODE_TYPE", "追加节点缺少node_type: " + node.getNodeId());
        }
        if (isBlank(node.getCapability())) {
            return ReplanPatchValidationResult.error("MISSING_CAPABILITY", "追加节点缺少capability: " + node.getNodeId());
        }
        if (isBlank(node.getInstruction())) {
            return ReplanPatchValidationResult.error("MISSING_INSTRUCTION", "追加节点缺少instruction: " + node.getNodeId());
        }

        ReplanPatchValidationResult capabilityResult = validateCapability(node);
        if (!capabilityResult.isValid()) return capabilityResult;

        if (node.getDependsOn() != null) {
            for (String dependency : node.getDependsOn()) {
                if (isBlank(dependency)) {
                    return ReplanPatchValidationResult.error("MISSING_DEPENDENCY", "追加节点存在空依赖: " + node.getNodeId());
                }
                if (!availableNodeIds.contains(dependency)) {
                    return ReplanPatchValidationResult.error("UNAVAILABLE_DEPENDENCY",
                            "追加节点只能依赖已完成节点或前序追加节点: " + node.getNodeId() + " -> " + dependency);
                }
            }
        }
        return ReplanPatchValidationResult.ok();
    }

    private ReplanPatchValidationResult validateCapability(DagNode node) {
        CapabilityDefinition capability = capabilityCatalog.getCapability(node.getCapability()).orElse(null);
        if (capability == null) {
            return ReplanPatchValidationResult.error("UNKNOWN_CAPABILITY", "未知能力: " + node.getCapability());
        }
        if (!capability.isEnabled()) {
            return ReplanPatchValidationResult.error("CAPABILITY_DISABLED", "能力已禁用: " + node.getCapability());
        }
        String expectedNodeType = capability.getNodeType() != null
                ? capability.getNodeType().trim().toUpperCase(Locale.ROOT)
                : "";
        if (!node.getNodeType().name().equals(expectedNodeType)) {
            return ReplanPatchValidationResult.error("NODE_TYPE_CAPABILITY_MISMATCH",
                    "追加节点node_type与capability不匹配: " + node.getNodeId());
        }
        return ReplanPatchValidationResult.ok();
    }

    private ReplanPatchValidationResult validateAppendEdges(ReplanPatch patch, Set<String> availableNodeIds) {
        if (patch.getAppendEdges() == null) {
            return ReplanPatchValidationResult.ok();
        }
        for (DagEdge edge : patch.getAppendEdges()) {
            if (edge == null) {
                return ReplanPatchValidationResult.error("APPEND_EDGE_NULL", "追加边不能为空");
            }
            if (!availableNodeIds.contains(edge.getFrom())) {
                return ReplanPatchValidationResult.error("UNKNOWN_EDGE_FROM", "追加边from未知: " + edge.getFrom());
            }
            if (!availableNodeIds.contains(edge.getTo())) {
                return ReplanPatchValidationResult.error("UNKNOWN_EDGE_TO", "追加边to未知: " + edge.getTo());
            }
        }
        return ReplanPatchValidationResult.ok();
    }

    private Set<String> collectCompletedNodeIds(ReplanContext context) {
        Set<String> nodeIds = new HashSet<>();
        if (context == null || context.getRunResult() == null || context.getRunResult().getState() == null
                || context.getRunResult().getState().getObservations() == null) {
            return nodeIds;
        }
        context.getRunResult().getState().getObservations().forEach((nodeId, observation) -> {
            if (observation != null
                    && (NodeExecutionStatus.SUCCESS.equals(observation.getStatus())
                    || NodeExecutionStatus.SKIPPED.equals(observation.getStatus()))) {
                nodeIds.add(nodeId);
            }
        });
        return nodeIds;
    }

    private Set<String> collectExistingNodeIds(ReplanContext context) {
        Set<String> nodeIds = new HashSet<>();
        if (context == null || context.getBoundDagPlan() == null || context.getBoundDagPlan().getNodes() == null) {
            return nodeIds;
        }
        context.getBoundDagPlan().getNodes().forEach(node -> {
            if (node != null && node.getNodeId() != null) {
                nodeIds.add(node.getNodeId());
            }
        });
        return nodeIds;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
