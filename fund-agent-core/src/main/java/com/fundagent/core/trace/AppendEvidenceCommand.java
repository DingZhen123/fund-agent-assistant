package com.fundagent.core.trace;

import lombok.Builder;
import lombok.Value;

import java.time.Instant;

@Value
@Builder
public class AppendEvidenceCommand {
    String evidenceCode;
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
    Instant collectedAt;
    String actor;
}
