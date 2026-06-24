package com.fundagent.core.trace;

import lombok.Builder;
import lombok.Value;

import java.util.Set;

@Value
public class TraceContext {
    String episodeCode;
    String currentEventCode;
    String causationEventCode;
    String correlationId;
    String requestId;
    Set<String> traceFlags;

    @Builder
    public TraceContext(String episodeCode, String currentEventCode, String causationEventCode,
                        String correlationId, String requestId, Set<String> traceFlags) {
        this.episodeCode = episodeCode;
        this.currentEventCode = currentEventCode;
        this.causationEventCode = causationEventCode;
        this.correlationId = correlationId;
        this.requestId = requestId;
        this.traceFlags = traceFlags == null ? Set.of() : Set.copyOf(traceFlags);
    }

    public TraceContext childOf(String childEventCode) {
        return new TraceContext(
                episodeCode,
                childEventCode,
                currentEventCode,
                correlationId,
                requestId,
                traceFlags);
    }
}
