package com.fundagent.core.dag;

import com.alibaba.fastjson2.annotation.JSONField;
import lombok.Builder;
import lombok.Data;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Data
@Builder
public class NodeObservation {
    @JSONField(name = "node_id")
    private String nodeId;

    @JSONField(name = "node_type")
    private NodeType nodeType;

    private String capability;

    private NodeExecutionStatus status;

    private String summary;

    @Builder.Default
    private Map<String, Object> outputs = new HashMap<>();

    @JSONField(name = "tool_calls")
    private List<ToolCallRecord> toolCalls;

    @JSONField(name = "error_code")
    private String errorCode;

    private String error;

    @JSONField(name = "elapsed_ms")
    private long elapsedMs;
}
