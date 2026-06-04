package com.fundagent.core.dag;

import com.fundagent.core.capability.CapabilityCatalog;
import com.fundagent.core.capability.CapabilityDefinition;
import com.fundagent.core.tool.ToolDefinition;
import com.fundagent.core.tool.schema.ToolSchemaResolver;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class DefaultToolBinder implements ToolBinder {
    private final CapabilityCatalog capabilityCatalog;
    private final ToolSchemaResolver toolSchemaResolver;

    public DefaultToolBinder(CapabilityCatalog capabilityCatalog, ToolSchemaResolver toolSchemaResolver) {
        this.capabilityCatalog = capabilityCatalog;
        this.toolSchemaResolver = toolSchemaResolver;
    }

    @Override
    public BoundDagPlan bind(DagPlan dagPlan) {
        BoundDagPlan boundDagPlan = BoundDagPlan.from(dagPlan);
        if (dagPlan == null || dagPlan.getNodes() == null) {
            return boundDagPlan;
        }

        List<BoundDagNode> boundNodes = dagPlan.getNodes().stream()
                .map(this::bindNode)
                .toList();
        boundDagPlan.setNodes(boundNodes);
        return boundDagPlan;
    }

    @Override
    public ToolBindingResult validate(BoundDagPlan boundDagPlan) {
        if (boundDagPlan == null) {
            return ToolBindingResult.error("BOUND_DAG_NULL", "绑定后的DAG为空");
        }
        if (boundDagPlan.getNodes() == null || boundDagPlan.getNodes().isEmpty()) {
            return ToolBindingResult.error("EMPTY_BOUND_NODES", "绑定后的DAG节点不能为空");
        }
        for (BoundDagNode node : boundDagPlan.getNodes()) {
            if (ToolBindingStatus.FAILED.equals(node.getBindingStatus())) {
                return ToolBindingResult.error("TOOL_BINDING_FAILED",
                        "节点工具绑定失败: " + node.getNodeId());
            }
        }
        return ToolBindingResult.ok();
    }

    private BoundDagNode bindNode(DagNode node) {
        BoundDagNode boundNode = BoundDagNode.from(node);
        if (node == null || node.getCapability() == null || node.getCapability().trim().isEmpty()) {
            boundNode.setBindingStatus(ToolBindingStatus.FAILED);
            return boundNode;
        }

        CapabilityDefinition capability = capabilityCatalog.getCapability(node.getCapability()).orElse(null);
        if (capability == null || !capability.isEnabled()) {
            boundNode.setBindingStatus(ToolBindingStatus.FAILED);
            return boundNode;
        }

        List<String> bindableTools = capability.getBindableTools();
        if (bindableTools == null || bindableTools.isEmpty()) {
            boundNode.setBindingStatus(ToolBindingStatus.NO_TOOL_REQUIRED);
            return boundNode;
        }

        List<ToolDefinition> definitions = toolSchemaResolver.resolve(bindableTools);
        if (!allToolsResolved(bindableTools, definitions)) {
            boundNode.setBindingStatus(ToolBindingStatus.FAILED);
            return boundNode;
        }

        boundNode.setBoundTools(definitions.stream()
                .map(BoundTool::fromDefinition)
                .toList());
        boundNode.setBindingStatus(ToolBindingStatus.BOUND);
        return boundNode;
    }

    private boolean allToolsResolved(List<String> expectedToolNames, List<ToolDefinition> definitions) {
        if (definitions == null || definitions.isEmpty()) {
            return false;
        }
        Set<String> resolvedNames = new HashSet<>(definitions.stream()
                .map(ToolDefinition::getName)
                .toList());
        return expectedToolNames.stream().allMatch(resolvedNames::contains);
    }
}
