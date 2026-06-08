package com.fundagent.core.dag;

import com.alibaba.fastjson2.annotation.JSONField;
import lombok.Data;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Data
public class ReplanPatch {
    private ReplanAction action;

    private String reason;

    @JSONField(name = "append_nodes")
    private List<DagNode> appendNodes = new ArrayList<>();

    @JSONField(name = "append_edges")
    private List<DagEdge> appendEdges = new ArrayList<>();

    @JSONField(name = "stop_message")
    private String stopMessage;

    private Map<String, Object> metadata = new HashMap<>();
}
