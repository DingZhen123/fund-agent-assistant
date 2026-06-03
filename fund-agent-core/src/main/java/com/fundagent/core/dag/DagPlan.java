package com.fundagent.core.dag;

import com.alibaba.fastjson2.annotation.JSONField;
import lombok.Data;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Data
public class DagPlan {
    @JSONField(name = "dag_id")
    private String dagId;

    private String goal;

    private List<DagNode> nodes = new ArrayList<>();

    private List<DagEdge> edges = new ArrayList<>();

    private Map<String, Object> metadata = new HashMap<>();
}
