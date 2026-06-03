package com.fundagent.core.dag;

import com.alibaba.fastjson2.annotation.JSONField;
import lombok.Data;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Data
public class DagNode {
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

    private Map<String, Object> metadata = new HashMap<>();
}
