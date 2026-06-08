package com.fundagent.core.dag;

import com.alibaba.fastjson2.annotation.JSONField;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.LinkedHashMap;
import java.util.Map;

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

    @JSONField(name = "failed_node_id")
    private String failedNodeId;

    @JSONField(name = "failed_stage")
    private DagFailureStage failedStage;

    @JSONField(name = "node_completion_results")
    private Map<String, NodeCompletionResult> nodeCompletionResults;

    @JSONField(name = "final_verification")
    private FinalVerificationResult finalVerification;

    public static DagRunResult completed(DagGraphState state, NodeExecutionResult lastResult,
                                         Map<String, NodeCompletionResult> nodeCompletionResults,
                                         FinalVerificationResult finalVerification) {
        return new DagRunResult(DagRunStatus.COMPLETED, state, lastResult, null, null,
                null, null, safeResults(nodeCompletionResults), finalVerification);
    }

    public static DagRunResult waiting(DagGraphState state, NodeExecutionResult lastResult,
                                       Map<String, NodeCompletionResult> nodeCompletionResults) {
        return new DagRunResult(DagRunStatus.WAITING_USER_INPUT, state, lastResult,
                null, lastResult != null ? lastResult.getMessage() : null,
                extractNodeId(lastResult), null, safeResults(nodeCompletionResults), null);
    }

    public static DagRunResult failed(DagGraphState state, NodeExecutionResult lastResult,
                                      String errorCode, String message,
                                      Map<String, NodeCompletionResult> nodeCompletionResults,
                                      FinalVerificationResult finalVerification,
                                      String failedNodeId,
                                      DagFailureStage failedStage) {
        return new DagRunResult(DagRunStatus.FAILED, state, lastResult, errorCode, message,
                failedNodeId != null ? failedNodeId : extractNodeId(lastResult),
                failedStage,
                safeResults(nodeCompletionResults), finalVerification);
    }

    public static DagRunResult failed(DagGraphState state, NodeExecutionResult lastResult,
                                      String errorCode, String message,
                                      Map<String, NodeCompletionResult> nodeCompletionResults,
                                      FinalVerificationResult finalVerification) {
        return failed(state, lastResult, errorCode, message, nodeCompletionResults, finalVerification,
                extractNodeId(lastResult), DagFailureStage.NODE_EXECUTION);
    }

    public static DagRunResult failed(DagGraphState state, NodeExecutionResult lastResult,
                                      String errorCode, String message) {
        return failed(state, lastResult, errorCode, message, null, null);
    }

    private static Map<String, NodeCompletionResult> safeResults(Map<String, NodeCompletionResult> results) {
        return results != null ? results : new LinkedHashMap<>();
    }

    private static String extractNodeId(NodeExecutionResult result) {
        if (result == null || result.getObservation() == null) {
            return null;
        }
        return result.getObservation().getNodeId();
    }
}
