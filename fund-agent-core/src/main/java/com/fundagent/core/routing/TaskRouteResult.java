package com.fundagent.core.routing;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class TaskRouteResult {
    private TaskMode mode;
    private double confidence;
    private String reason;
    private List<String> matchedRules;

    public static TaskRouteResult simple(double confidence, String reason, List<String> matchedRules) {
        return new TaskRouteResult(TaskMode.SIMPLE, confidence, reason, matchedRules);
    }

    public static TaskRouteResult complex(double confidence, String reason, List<String> matchedRules) {
        return new TaskRouteResult(TaskMode.COMPLEX, confidence, reason, matchedRules);
    }

    public static TaskRouteResult needClassification(String reason, List<String> matchedRules) {
        return new TaskRouteResult(TaskMode.NEED_CLASSIFICATION, 0.5, reason, matchedRules);
    }
}
