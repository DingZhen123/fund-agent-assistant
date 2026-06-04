package com.fundagent.core.dag;

import com.alibaba.fastjson2.annotation.JSONField;
import lombok.Data;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Data
public class BoundDagNode {
    @JSONField(name = "node_id")
    private String nodeId;

    private String name;

    @JSONField(name = "node_type")
    private NodeType nodeType;

    private String capability;

    private String instruction;

    @JSONField(name = "depends_on")
    private List<String> dependsOn = new ArrayList<>();

    @JSONField(name = "expected_outputs")
    private List<String> expectedOutputs = new ArrayList<>();

    private String agent;

    @JSONField(name = "binding_status")
    private ToolBindingStatus bindingStatus;

    @JSONField(name = "bound_tools")
    private List<BoundTool> boundTools = new ArrayList<>();

    private Map<String, Object> metadata = new HashMap<>();

    public static BoundDagNode from(DagNode node) {
        BoundDagNode bound = new BoundDagNode();
        if (node == null) {
            return bound;
        }
        bound.setNodeId(node.getNodeId());
        bound.setName(node.getName());
        bound.setNodeType(node.getNodeType());
        bound.setCapability(node.getCapability());
        bound.setInstruction(node.getInstruction());
        bound.setDependsOn(node.getDependsOn() != null ? node.getDependsOn() : new ArrayList<>());
        bound.setExpectedOutputs(node.getExpectedOutputs() != null ? node.getExpectedOutputs() : new ArrayList<>());
        bound.setAgent(node.getAgent());
        bound.setMetadata(node.getMetadata() != null ? node.getMetadata() : new HashMap<>());
        return bound;
    }
}
