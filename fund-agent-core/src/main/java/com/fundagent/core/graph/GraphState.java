package com.fundagent.core.graph;

import lombok.Data;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Data
public class GraphState {
    private String taskId;
    private String conversationId;
    private String userId;
    private String userMessage;
    private TaskPlan taskPlan;
    private Map<String, Observation> observations = new LinkedHashMap<>();
    private List<String> executedStepIds = new ArrayList<>();
    private int executedSteps;
    private String finalAnswer;
    private boolean waitingUserInput;
}
