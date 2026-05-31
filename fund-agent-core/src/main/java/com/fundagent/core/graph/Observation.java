package com.fundagent.core.graph;

import com.alibaba.fastjson2.annotation.JSONField;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Observation {
    @JSONField(name = "step_id")
    private String stepId;

    private String source;

    private boolean success;

    private Object data;

    private String error;

    @JSONField(name = "error_code")
    private String errorCode;

    @JSONField(name = "error_type")
    private String errorType;

    private boolean retryable;

    private int attempts;

    @JSONField(name = "elapsed_ms")
    private long elapsedMs;
}
