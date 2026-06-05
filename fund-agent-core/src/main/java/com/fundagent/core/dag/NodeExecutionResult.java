package com.fundagent.core.dag;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class NodeExecutionResult {
    private boolean success;
    private boolean waitingUserInput;
    private NodeObservation observation;
    private String errorCode;
    private String message;

    public static NodeExecutionResult success(NodeObservation observation) {
        return new NodeExecutionResult(true, false, observation, null, null);
    }

    public static NodeExecutionResult waiting(NodeObservation observation, String message) {
        return new NodeExecutionResult(false, true, observation, null, message);
    }

    public static NodeExecutionResult failed(NodeObservation observation, String errorCode, String message) {
        return new NodeExecutionResult(false, false, observation, errorCode, message);
    }
}
