package com.fundagent.core.dag;

import lombok.Data;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Data
public class DagGraphState {
    private String dagId;
    private String conversationId;
    private String userId;
    private String userMessage;
    private Map<String, NodeObservation> observations = new LinkedHashMap<>();

    public boolean dependenciesCompleted(List<String> dependsOn) {
        if (dependsOn == null || dependsOn.isEmpty()) {
            return true;
        }
        return dependsOn.stream()
                .allMatch(nodeId -> observations.containsKey(nodeId)
                        && isCompleted(observations.get(nodeId).getStatus()));
    }

    public void addObservation(NodeObservation observation) {
        if (observation != null && observation.getNodeId() != null) {
            observations.put(observation.getNodeId(), observation);
        }
    }

    private boolean isCompleted(NodeExecutionStatus status) {
        return NodeExecutionStatus.SUCCESS.equals(status) || NodeExecutionStatus.SKIPPED.equals(status);
    }
}
