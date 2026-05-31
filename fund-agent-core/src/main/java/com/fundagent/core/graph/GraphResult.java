package com.fundagent.core.graph;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class GraphResult {
    private boolean success;
    private boolean waitingUserInput;
    private String answer;
    private GraphState state;

    public static GraphResult completed(String answer, GraphState state) {
        return new GraphResult(true, false, answer, state);
    }

    public static GraphResult waiting(String message, GraphState state) {
        return new GraphResult(true, true, message, state);
    }

    public static GraphResult failed(String message, GraphState state) {
        return new GraphResult(false, false, message, state);
    }
}
