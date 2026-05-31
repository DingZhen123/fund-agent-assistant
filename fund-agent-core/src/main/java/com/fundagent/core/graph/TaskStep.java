package com.fundagent.core.graph;

import com.alibaba.fastjson2.annotation.JSONField;
import lombok.Data;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Data
public class TaskStep {
    @JSONField(name = "step_id")
    private String stepId;

    private String name;

    private StepType type;

    @JSONField(name = "depends_on")
    private List<String> dependsOn = new ArrayList<>();

    @JSONField(name = "tool_name")
    private String toolName;

    @JSONField(name = "tool_params")
    private Map<String, Object> toolParams = new HashMap<>();

    private String instruction;
}
