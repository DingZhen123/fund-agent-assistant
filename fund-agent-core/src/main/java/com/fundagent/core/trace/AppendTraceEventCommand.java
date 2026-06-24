package com.fundagent.core.trace;

import lombok.Builder;
import lombok.Value;

import java.time.Instant;
import java.util.Set;

@Value
public class AppendTraceEventCommand {
    String eventCode;
    String parentEventCode;
    Set<String> causationEventCodes;
    String correlationId;
    TraceEventType eventType;
    TraceStage stage;
    String nodeId;
    String capability;
    String toolName;
    TraceEventStatus status;
    String reasonCode;
    String summary;
    String payloadJson;
    int payloadSchemaVersion;
    String producerId;
    Instant occurredAt;
    String actor;

    @Builder
    public AppendTraceEventCommand(String eventCode, String parentEventCode, Set<String> causationEventCodes,
                                   String correlationId, TraceEventType eventType, TraceStage stage,
                                   String nodeId, String capability, String toolName, TraceEventStatus status,
                                   String reasonCode, String summary, String payloadJson,
                                   int payloadSchemaVersion, String producerId, Instant occurredAt, String actor) {
        this.eventCode = eventCode;
        this.parentEventCode = parentEventCode;
        this.causationEventCodes = causationEventCodes == null ? Set.of() : Set.copyOf(causationEventCodes);
        this.correlationId = correlationId;
        this.eventType = eventType;
        this.stage = stage;
        this.nodeId = nodeId;
        this.capability = capability;
        this.toolName = toolName;
        this.status = status;
        this.reasonCode = reasonCode;
        this.summary = summary;
        this.payloadJson = payloadJson;
        this.payloadSchemaVersion = payloadSchemaVersion;
        this.producerId = producerId;
        this.occurredAt = occurredAt;
        this.actor = actor;
    }
}
