package com.fundagent.core.trace;

import lombok.Builder;
import lombok.Value;

import java.time.Instant;

@Value
@Builder
public class CreateEpisodeCommand {
    String episodeCode;
    String requestId;
    String conversationId;
    String userIdReference;
    String agentVersion;
    String originalGoalRedacted;
    RiskLevel riskLevel;
    Instant startedAt;
    String actor;
}
