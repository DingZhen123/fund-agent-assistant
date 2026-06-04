package com.fundagent.core.dag;

import com.alibaba.fastjson2.annotation.JSONField;
import lombok.Data;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Data
public class BoundDagPlan {
    @JSONField(name = "dag_id")
    private String dagId;

    private String goal;

    private List<BoundDagNode> nodes = new ArrayList<>();

    private List<DagEdge> edges = new ArrayList<>();

    private Map<String, Object> metadata = new HashMap<>();

    public static BoundDagPlan from(DagPlan dagPlan) {
        BoundDagPlan bound = new BoundDagPlan();
        if (dagPlan == null) {
            return bound;
        }
        bound.setDagId(dagPlan.getDagId());
        bound.setGoal(dagPlan.getGoal());
        bound.setEdges(dagPlan.getEdges() != null ? dagPlan.getEdges() : new ArrayList<>());
        bound.setMetadata(dagPlan.getMetadata() != null ? dagPlan.getMetadata() : new HashMap<>());
        return bound;
    }
}
