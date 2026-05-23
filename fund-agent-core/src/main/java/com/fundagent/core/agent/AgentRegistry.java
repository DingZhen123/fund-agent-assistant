package com.fundagent.core.agent;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class AgentRegistry {
    private final Map<String, AgentEntry> entries = new ConcurrentHashMap<>();
    private final Map<String, Agent> instances = new ConcurrentHashMap<>();

    public void register(AgentEntry entry) {
        entries.put(entry.getName(), entry);
    }

    public void registerInstance(Agent agent) {
        instances.put(agent.getAgentName(), agent);
    }

    public Agent getInstance(String name) {
        return instances.get(name);
    }

    public boolean hasAgent(String name) {
        return entries.containsKey(name);
    }

    public Agent getPlanner() {
        return instances.values().stream()
                .filter(a -> {
                    AgentEntry entry = entries.get(a.getAgentName());
                    return entry != null && entry.isPlanner();
                })
                .findFirst()
                .orElse(null);
    }

    public String getWorkersDescription() {
        return entries.values().stream()
                .filter(e -> !e.isPlanner())
                .map(e -> "- " + e.getName() + ": " + e.getDescription())
                .collect(Collectors.joining("\n"));
    }

    public List<AgentEntry> getAllEntries() {
        return List.copyOf(entries.values());
    }
}
