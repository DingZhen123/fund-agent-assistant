package com.fundagent.core.dag;

import com.alibaba.fastjson2.annotation.JSONField;
import lombok.Builder;
import lombok.Data;

import java.util.Map;

@Data
@Builder
public class ToolCallRecord {
    @JSONField(name = "tool_name")
    private String toolName;

    private Map<String, Object> args;

    private boolean success;

    private Object data;

    private String error;

    @JSONField(name = "error_code")
    private String errorCode;
}
