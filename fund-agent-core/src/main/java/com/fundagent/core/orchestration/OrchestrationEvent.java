package com.fundagent.core.orchestration;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class OrchestrationEvent {
    private OrchestrationEventType type;
    private String agent;
    private String message;
}
