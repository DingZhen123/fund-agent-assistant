package com.fundagent.agents.dag;

import com.alibaba.fastjson2.annotation.JSONField;
import lombok.Data;

import java.util.HashMap;
import java.util.Map;

@Data
public class ToolNodeDecision {
    @JSONField(name = "should_call_tool")
    private boolean shouldCallTool;

    @JSONField(name = "tool_name")
    private String toolName;

    @JSONField(name = "tool_params")
    private Map<String, Object> toolParams = new HashMap<>();

    @JSONField(name = "skip_reason")
    private String skipReason;

    private String rationale;
}
