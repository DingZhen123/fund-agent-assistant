package com.fundagent.core.graph;

import com.alibaba.fastjson2.annotation.JSONField;
import lombok.Data;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Data
public class TaskPlan {
    @JSONField(name = "task_id")
    private String taskId;

    private String goal;

    private List<TaskStep> steps = new ArrayList<>();

    private Map<String, Object> metadata = new HashMap<>();
}
