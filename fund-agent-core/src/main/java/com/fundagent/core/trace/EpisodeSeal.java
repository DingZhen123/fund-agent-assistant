package com.fundagent.core.trace;

import lombok.Builder;
import lombok.Value;

import java.time.Instant;

@Value
@Builder
public class EpisodeSeal {
    String sealCode;
    String episodeCode;
    long finalSequenceNo;
    String finalEventHash;
    long eventCount;
    EpisodeStatus finalStatus;
    Instant sealedAt;
    String signatureAlgorithm;
    String signingKeyId;
    String sealSignature;
    Instant createdAt;
    String createdBy;
}
