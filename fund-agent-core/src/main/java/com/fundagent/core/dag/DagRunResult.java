package com.fundagent.core.dag;

import com.alibaba.fastjson2.annotation.JSONField;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class DagRunResult {
    private DagRunStatus status;
    private DagGraphState state;

    @JSONField(name = "last_result")
    private NodeExecutionResult lastResult;

    @JSONField(name = "error_code")
    private String errorCode;

    private String message;

    public static DagRunResult completed(DagGraphState state, NodeExecutionResult lastResult) {
        return new DagRunResult(DagRunStatus.COMPLETED, state, lastResult, null, null);
    }

    public static DagRunResult waiting(DagGraphState state, NodeExecutionResult lastResult) {
        return new DagRunResult(DagRunStatus.WAITING_USER_INPUT, state, lastResult,
                null, lastResult != null ? lastResult.getMessage() : null);
    }

    public static DagRunResult failed(DagGraphState state, NodeExecutionResult lastResult,
                                      String errorCode, String message) {
        return new DagRunResult(DagRunStatus.FAILED, state, lastResult, errorCode, message);
    }
}
