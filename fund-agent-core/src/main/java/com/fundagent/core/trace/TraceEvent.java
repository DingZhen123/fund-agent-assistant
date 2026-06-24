package com.fundagent.core.trace;

import lombok.Builder;
import lombok.Value;

import java.time.Instant;

@Value
@Builder(toBuilder = true)
public class TraceEvent {
    String eventCode;
    String episodeCode;
    long sequenceNo;
    String parentEventCode;
    String causationEventCode;
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
    String payloadHash;
    String producerId;
    Instant occurredAt;
    Instant receivedAt;
    Instant persistedAt;
    String previousHash;
    String eventHash;
    String signatureAlgorithm;
    String signingKeyId;
    String eventSignature;
    String createdBy;
}
