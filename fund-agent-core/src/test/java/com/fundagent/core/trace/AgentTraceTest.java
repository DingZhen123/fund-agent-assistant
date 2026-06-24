package com.fundagent.core.trace;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class AgentTraceTest {
    private static final Instant NOW = Instant.parse("2026-06-24T02:00:00Z");

    private final TraceSecurity security = new TraceSecurity(
            new DefaultTraceCanonicalizer(),
            new Sha256TraceHasher(),
            new HmacSha256TraceSigner(
                    "key-v1",
                    "0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8)));

    @Test
    void shouldAppendEventAndEvidenceAsNewAggregate() {
        AgentTrace original = AgentTrace.builder()
                .episode(episode())
                .build();
        TraceContext context = TraceContext.builder()
                .episodeCode("episode-1")
                .requestId("request-1")
                .build();

        AgentTraceAppendResult appended = original.append(
                context,
                eventCommand("event-1", TraceEventType.EPISODE_STARTED, TraceEventStatus.STARTED),
                security,
                NOW);
        AgentTrace withEvidence = appended.getTrace().appendEvidence(
                AppendEvidenceCommand.builder()
                        .evidenceCode("evidence-1")
                        .eventCode("event-1")
                        .nodeId("node-1")
                        .evidenceType("PAYMENT_STATUS")
                        .sourceType("DATABASE_QUERY")
                        .sourceReference("payment:masked")
                        .claim("payment_status")
                        .expectedValueRedacted("PAID")
                        .actualValueRedacted("PAID")
                        .reliabilityLevel(EvidenceReliabilityLevel.DETERMINISTIC)
                        .verificationStatus(EvidenceVerificationStatus.SUPPORTED)
                        .payloadJson("{\"b\":2,\"a\":1}")
                        .collectedAt(NOW.plusSeconds(1))
                        .actor("SYSTEM:Verifier")
                        .build(),
                security,
                NOW.plusSeconds(1));

        assertThat(original.getEvents()).isEmpty();
        assertThat(appended.getTrace().getEvents()).hasSize(1);
        assertThat(withEvidence.getEvidence()).hasSize(1);
        assertThat(withEvidence.getEvidence().get(0).getPayloadJson()).isEqualTo("{\"a\":1,\"b\":2}");
        assertThat(withEvidence.getEvidence().get(0).getPayloadHash()).hasSize(64);
        assertThat(security.verifyEvidence(withEvidence.getEvidence().get(0))).isTrue();
        assertThat(withEvidence.verifyIntegrity(security).isValid()).isTrue();
    }

    @Test
    void shouldSealAggregateAndVerifySeal() {
        AgentTrace trace = AgentTrace.builder().episode(episode()).build();
        TraceContext context = TraceContext.builder()
                .episodeCode("episode-1")
                .requestId("request-1")
                .build();
        AgentTraceAppendResult started = trace.append(
                context,
                eventCommand("event-1", TraceEventType.EPISODE_STARTED, TraceEventStatus.STARTED),
                security,
                NOW);
        AgentTraceAppendResult completed = started.getTrace().append(
                started.getContext(),
                eventCommand("event-2", TraceEventType.EPISODE_COMPLETED, TraceEventStatus.SUCCEEDED),
                security,
                NOW.plusSeconds(1));

        AgentTrace sealed = completed.getTrace().seal(
                "seal-1",
                "SYSTEM:TraceRecorder",
                security,
                NOW.plusSeconds(2));

        assertThat(sealed.getEpisode().isSealed()).isTrue();
        assertThat(sealed.getSeal()).isNotNull();
        assertThat(sealed.verifyIntegrity(security).isValid()).isTrue();

        AgentTrace missingSeal = AgentTrace.builder()
                .episode(sealed.getEpisode())
                .events(sealed.getEvents())
                .relations(sealed.getRelations())
                .evidence(sealed.getEvidence())
                .build();
        assertThat(missingSeal.verifyIntegrity(security).getReasonCode())
                .isEqualTo("SEAL_STATE_MISMATCH");
    }

    private AgentEpisode episode() {
        return AgentEpisode.builder()
                .episodeCode("episode-1")
                .requestId("request-1")
                .agentVersion("agent-v1")
                .riskLevel(RiskLevel.HIGH)
                .status(EpisodeStatus.CREATED)
                .nextSequenceNo(1)
                .startedAt(NOW)
                .createdAt(NOW)
                .updatedAt(NOW)
                .createdBy("SYSTEM:ChatService")
                .updatedBy("SYSTEM:ChatService")
                .build();
    }

    private AppendTraceEventCommand eventCommand(String eventCode, TraceEventType type,
                                                 TraceEventStatus status) {
        return AppendTraceEventCommand.builder()
                .eventCode(eventCode)
                .eventType(type)
                .stage(TraceStage.EPISODE)
                .status(status)
                .summary(type.name())
                .payloadJson("{}")
                .payloadSchemaVersion(1)
                .producerId("AgentTraceTest")
                .occurredAt(NOW)
                .actor("SYSTEM:AgentTraceTest")
                .build();
    }
}
