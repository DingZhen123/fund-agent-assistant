package com.fundagent.agents.dag;

import com.alibaba.fastjson2.annotation.JSONField;
import lombok.Data;

@Data
public class ToolNodeSelectionDecision {
    @JSONField(name = "should_call_tool")
    private boolean shouldCallTool;

    @JSONField(name = "tool_name")
    private String toolName;

    @JSONField(name = "skip_reason")
    private String skipReason;

    private String rationale;
}
