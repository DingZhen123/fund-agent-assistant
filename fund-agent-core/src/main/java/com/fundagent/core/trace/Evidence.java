package com.fundagent.core.trace;

import lombok.Builder;
import lombok.Value;

import java.time.Instant;

@Value
@Builder(toBuilder = true)
public class Evidence {
    String evidenceCode;
    String episodeCode;
    String eventCode;
    String nodeId;
    String evidenceType;
    String sourceType;
    String sourceReference;
    String claim;
    String expectedValueRedacted;
    String actualValueRedacted;
    EvidenceReliabilityLevel reliabilityLevel;
    EvidenceVerificationStatus verificationStatus;
    String payloadJson;
    String payloadHash;
    Instant collectedAt;
    Instant createdAt;
    String createdBy;
}
