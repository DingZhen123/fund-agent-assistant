package com.fundagent.core.trace;

import lombok.Builder;
import lombok.Value;

import java.time.Instant;

@Value
@Builder
public class AgentEpisode {
    String episodeCode;
    String requestId;
    String conversationId;
    String userIdReference;
    String agentVersion;
    String originalGoalRedacted;
    RiskLevel riskLevel;
    EpisodeStatus status;
    long nextSequenceNo;
    String lastEventHash;
    long eventCount;
    int stepCount;
    int modelCallCount;
    int toolCallCount;
    long tokenUsage;
    String finalErrorCode;
    String finalFailureStage;
    Instant startedAt;
    Instant finishedAt;
    Long elapsedMs;
    boolean sealed;
    long rowVersion;
    Instant createdAt;
    Instant updatedAt;
    String createdBy;
    String updatedBy;
}
