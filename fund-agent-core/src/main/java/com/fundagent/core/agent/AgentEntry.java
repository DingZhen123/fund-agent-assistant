package com.fundagent.core.agent;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AgentEntry {
    private String name;
    private String description;
    private Class<? extends Agent> agentClass;
    private boolean isPlanner;
}
