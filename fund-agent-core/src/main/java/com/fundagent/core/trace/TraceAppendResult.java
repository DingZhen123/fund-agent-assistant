package com.fundagent.core.trace;

import lombok.Builder;
import lombok.Value;

import java.util.List;

@Value
public class TraceAppendResult {
    AgentEpisode episode;
    TraceEvent event;
    TraceContext context;
    List<TraceEventRelation> relations;

    @Builder
    public TraceAppendResult(AgentEpisode episode, TraceEvent event, TraceContext context,
                             List<TraceEventRelation> relations) {
        this.episode = episode;
        this.event = event;
        this.context = context;
        this.relations = relations == null ? List.of() : List.copyOf(relations);
    }
}
