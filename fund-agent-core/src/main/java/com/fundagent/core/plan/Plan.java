package com.fundagent.core.plan;

import com.alibaba.fastjson2.annotation.JSONField;
import lombok.Data;

import java.util.HashMap;
import java.util.Map;

@Data
public class Plan {
    @JSONField(name = "plan_reasoning")
    private String planReasoning;

    @JSONField(name = "send_to")
    private String sendTo;

    private String message;

    @JSONField(name = "tool_name")
    private String toolName;

    @JSONField(name = "tool_params")
    private Map<String, Object> toolParams = new HashMap<>();

    private boolean stop;
}
