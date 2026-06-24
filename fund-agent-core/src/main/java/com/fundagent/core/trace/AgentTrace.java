package com.fundagent.core.trace;

import lombok.Builder;
import lombok.Value;

import java.util.List;

@Value
public class AgentTrace {
    AgentEpisode episode;
    List<TraceEvent> events;
    List<TraceEventRelation> relations;
    List<Evidence> evidence;
    EpisodeSeal seal;

    @Builder
    public AgentTrace(AgentEpisode episode, List<TraceEvent> events,
                      List<TraceEventRelation> relations, List<Evidence> evidence, EpisodeSeal seal) {
        this.episode = episode;
        this.events = events == null ? List.of() : List.copyOf(events);
        this.relations = relations == null ? List.of() : List.copyOf(relations);
        this.evidence = evidence == null ? List.of() : List.copyOf(evidence);
        this.seal = seal;
    }
}
