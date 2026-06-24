package com.fundagent.core.trace;

import lombok.Builder;
import lombok.Value;

import java.time.Instant;

@Value
@Builder
public class TraceEventRelation {
    String episodeCode;
    String sourceEventCode;
    String targetEventCode;
    TraceRelationType relationType;
    Instant createdAt;
    String createdBy;
}
