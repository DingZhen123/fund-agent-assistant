package com.fundagent.core.trace;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AgentEpisodeAppendTest {
    private static final Instant CREATED_AT = Instant.parse("2026-06-24T01:00:00Z");
    private static final Instant EVENT_TIME = Instant.parse("2026-06-24T01:00:01Z");

    private final TraceSecurity security = new TraceSecurity(
            new DefaultTraceCanonicalizer(),
            new Sha256TraceHasher(),
            new HmacSha256TraceSigner(
                    "trace-key-v1",
                    "test-trace-secret-with-at-least-32-bytes".getBytes(StandardCharsets.UTF_8)));

    @Test
    void shouldAppendProtectedEventAndReturnNewEpisode() {
        AgentEpisode original = createdEpisode();

        TraceAppendResult result = original.append(
                context(null),
                command("event-1", TraceEventType.EPISODE_STARTED, "{\"b\":2,\"a\":1}"),
                security,
                EVENT_TIME);

        assertThat(original.getStatus()).isEqualTo(EpisodeStatus.CREATED);
        assertThat(original.getEventCount()).isZero();
        assertThat(original.getNextSequenceNo()).isEqualTo(1);

        AgentEpisode updated = result.getEpisode();
        TraceEvent event = result.getEvent();
        assertThat(updated.getStatus()).isEqualTo(EpisodeStatus.RUNNING);
        assertThat(updated.getEventCount()).isEqualTo(1);
        assertThat(updated.getNextSequenceNo()).isEqualTo(2);
        assertThat(updated.getLastEventHash()).isEqualTo(event.getEventHash());
        assertThat(updated.getRowVersion()).isEqualTo(1);

        assertThat(event.getSequenceNo()).isEqualTo(1);
        assertThat(event.getPreviousHash()).isEqualTo(TraceSecurity.GENESIS_HASH);
        assertThat(event.getPayloadJson()).isEqualTo("{\"a\":1,\"b\":2}");
        assertThat(event.getPayloadHash()).hasSize(64);
        assertThat(event.getEventHash()).hasSize(64);
        assertThat(event.getSignatureAlgorithm()).isEqualTo("HmacSHA256");
        assertThat(event.getSigningKeyId()).isEqualTo("trace-key-v1");
        assertThat(security.verifyEvent(event)).isTrue();
    }

    @Test
    void shouldChainSecondEventToFirstEventHashAndCreateCausalRelations() {
        TraceAppendResult first = createdEpisode().append(
                context(null),
                command("event-1", TraceEventType.EPISODE_STARTED, "{}"),
                security,
                EVENT_TIME);

        AppendTraceEventCommand secondCommand = AppendTraceEventCommand.builder()
                .eventCode("event-2")
                .parentEventCode("event-1")
                .causationEventCodes(Set.of("event-1", "event-external"))
                .correlationId("correlation-1")
                .eventType(TraceEventType.CONTEXT_ASSEMBLED)
                .stage(TraceStage.CONTEXT)
                .status(TraceEventStatus.SUCCEEDED)
                .summary("上下文组装完成")
                .payloadJson("{}")
                .payloadSchemaVersion(1)
                .producerId("ChatService")
                .occurredAt(EVENT_TIME.plusSeconds(1))
                .actor("SYSTEM:ChatService")
                .build();

        TraceAppendResult second = first.getEpisode().append(
                first.getContext(),
                secondCommand,
                security,
                EVENT_TIME.plusSeconds(1));

        assertThat(second.getEvent().getSequenceNo()).isEqualTo(2);
        assertThat(second.getEvent().getPreviousHash()).isEqualTo(first.getEvent().getEventHash());
        assertThat(second.getEvent().getCausationEventCode()).isEqualTo("event-1");
        assertThat(second.getRelations())
                .extracting(TraceEventRelation::getSourceEventCode)
                .containsExactlyInAnyOrder("event-1", "event-external");
        assertThat(security.verifyEvent(second.getEvent())).isTrue();
    }

    @Test
    void shouldRejectIllegalLifecycleTransitionAndAppendAfterSeal() {
        AgentEpisode created = createdEpisode();

        assertThatThrownBy(() -> created.append(
                context(null),
                command("event-1", TraceEventType.EPISODE_COMPLETED, "{}"),
                security,
                EVENT_TIME))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("CREATED")
                .hasMessageContaining("COMPLETED");

        TraceAppendResult started = created.append(
                context(null),
                command("event-1", TraceEventType.EPISODE_STARTED, "{}"),
                security,
                EVENT_TIME);
        TraceAppendResult completed = started.getEpisode().append(
                started.getContext(),
                command("event-2", TraceEventType.EPISODE_COMPLETED, "{}"),
                security,
                EVENT_TIME.plusSeconds(1));
        EpisodeSealResult sealed = completed.getEpisode().seal(
                "seal-1",
                "SYSTEM:TraceRecorder",
                security,
                EVENT_TIME.plusSeconds(2));

        assertThatThrownBy(() -> sealed.getEpisode().append(
                completed.getContext(),
                command("event-3", TraceEventType.CONTEXT_ASSEMBLED, "{}"),
                security,
                EVENT_TIME.plusSeconds(3)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("sealed");
    }

    @Test
    void shouldRejectTamperedEventAndSealCompletedEpisode() {
        TraceAppendResult started = createdEpisode().append(
                context(null),
                command("event-1", TraceEventType.EPISODE_STARTED, "{}"),
                security,
                EVENT_TIME);
        TraceAppendResult completed = started.getEpisode().append(
                started.getContext(),
                command("event-2", TraceEventType.EPISODE_COMPLETED, "{\"answer\":\"ok\"}"),
                security,
                EVENT_TIME.plusSeconds(1));

        TraceEvent tampered = completed.getEvent().toBuilder()
                .summary("被修改")
                .build();
        assertThat(security.verifyEvent(tampered)).isFalse();

        EpisodeSealResult result = completed.getEpisode().seal(
                "seal-1",
                "SYSTEM:TraceRecorder",
                security,
                EVENT_TIME.plusSeconds(2));

        assertThat(result.getEpisode().isSealed()).isTrue();
        assertThat(result.getSeal().getFinalStatus()).isEqualTo(EpisodeStatus.COMPLETED);
        assertThat(result.getSeal().getFinalEventHash()).isEqualTo(completed.getEvent().getEventHash());
        assertThat(security.verifySeal(result.getSeal())).isTrue();
    }

    @Test
    void shouldDetectBrokenEventChain() {
        TraceAppendResult first = createdEpisode().append(
                context(null),
                command("event-1", TraceEventType.EPISODE_STARTED, "{}"),
                security,
                EVENT_TIME);
        TraceAppendResult second = first.getEpisode().append(
                first.getContext(),
                command("event-2", TraceEventType.CONTEXT_ASSEMBLED, "{}"),
                security,
                EVENT_TIME.plusSeconds(1));

        TraceIntegrityResult valid = security.verifyChain(
                second.getEpisode(),
                List.of(first.getEvent(), second.getEvent()),
                null);
        assertThat(valid.isValid()).isTrue();

        TraceEvent broken = second.getEvent().toBuilder()
                .previousHash(TraceSecurity.GENESIS_HASH)
                .build();
        TraceIntegrityResult invalid = security.verifyChain(
                second.getEpisode(),
                List.of(first.getEvent(), broken),
                null);
        assertThat(invalid.isValid()).isFalse();
        assertThat(invalid.getReasonCode()).isEqualTo("PREVIOUS_HASH_MISMATCH");
        assertThat(invalid.getFailedSequenceNo()).isEqualTo(2);
    }

    @Test
    void shouldKeepResultUnknownUnsealedAndAllowRecovery() {
        TraceAppendResult started = createdEpisode().append(
                context(null),
                command("event-1", TraceEventType.EPISODE_STARTED, "{}"),
                security,
                EVENT_TIME);
        TraceAppendResult unknown = started.getEpisode().append(
                started.getContext(),
                lifecycleCommand(
                        "event-2",
                        TraceEventType.EPISODE_RESULT_UNKNOWN,
                        TraceEventStatus.RESULT_UNKNOWN),
                security,
                EVENT_TIME.plusSeconds(1));

        assertThat(unknown.getEpisode().getStatus()).isEqualTo(EpisodeStatus.RESULT_UNKNOWN);
        assertThatThrownBy(() -> unknown.getEpisode().seal(
                "seal-unknown",
                "SYSTEM:TraceRecorder",
                security,
                EVENT_TIME.plusSeconds(2)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("RESULT_UNKNOWN");

        TraceAppendResult recovered = unknown.getEpisode().append(
                unknown.getContext(),
                lifecycleCommand("event-3", TraceEventType.EPISODE_STARTED, TraceEventStatus.STARTED),
                security,
                EVENT_TIME.plusSeconds(3));
        assertThat(recovered.getEpisode().getStatus()).isEqualTo(EpisodeStatus.RUNNING);
    }

    @Test
    void shouldRejectAppendingAfterFinalLifecycleEventAndMismatchedLifecycleStatus() {
        TraceAppendResult started = createdEpisode().append(
                context(null),
                command("event-1", TraceEventType.EPISODE_STARTED, "{}"),
                security,
                EVENT_TIME);
        TraceAppendResult completed = started.getEpisode().append(
                started.getContext(),
                command("event-2", TraceEventType.EPISODE_COMPLETED, "{}"),
                security,
                EVENT_TIME.plusSeconds(1));

        assertThatThrownBy(() -> completed.getEpisode().append(
                completed.getContext(),
                command("event-3", TraceEventType.CONTEXT_ASSEMBLED, "{}"),
                security,
                EVENT_TIME.plusSeconds(2)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("final status");

        AppendTraceEventCommand inconsistent = AppendTraceEventCommand.builder()
                .eventCode("event-invalid")
                .eventType(TraceEventType.EPISODE_COMPLETED)
                .stage(TraceStage.EPISODE)
                .status(TraceEventStatus.FAILED)
                .summary("错误状态")
                .payloadJson("{}")
                .payloadSchemaVersion(1)
                .producerId("TraceTest")
                .occurredAt(EVENT_TIME)
                .actor("SYSTEM:TraceTest")
                .build();
        assertThatThrownBy(() -> started.getEpisode().append(
                started.getContext(),
                inconsistent,
                security,
                EVENT_TIME.plusSeconds(1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("event status");
    }

    private AgentEpisode createdEpisode() {
        return AgentEpisode.builder()
                .episodeCode("episode-1")
                .requestId("request-1")
                .conversationId("conversation-1")
                .userIdReference("user-hash")
                .agentVersion("agent-v1")
                .originalGoalRedacted("查询付款状态")
                .riskLevel(RiskLevel.HIGH)
                .status(EpisodeStatus.CREATED)
                .nextSequenceNo(1)
                .lastEventHash(null)
                .eventCount(0)
                .startedAt(CREATED_AT)
                .createdAt(CREATED_AT)
                .updatedAt(CREATED_AT)
                .createdBy("SYSTEM:ChatService")
                .updatedBy("SYSTEM:ChatService")
                .build();
    }

    private TraceContext context(String currentEventCode) {
        return TraceContext.builder()
                .episodeCode("episode-1")
                .requestId("request-1")
                .currentEventCode(currentEventCode)
                .correlationId("correlation-1")
                .build();
    }

    private AppendTraceEventCommand command(String eventCode, TraceEventType type, String payloadJson) {
        return AppendTraceEventCommand.builder()
                .eventCode(eventCode)
                .eventType(type)
                .stage(stageOf(type))
                .status(statusOf(type))
                .summary(type.name())
                .payloadJson(payloadJson)
                .payloadSchemaVersion(1)
                .producerId("TraceTest")
                .occurredAt(EVENT_TIME)
                .actor("SYSTEM:TraceTest")
                .build();
    }

    private AppendTraceEventCommand lifecycleCommand(String eventCode, TraceEventType type,
                                                     TraceEventStatus eventStatus) {
        return AppendTraceEventCommand.builder()
                .eventCode(eventCode)
                .eventType(type)
                .stage(TraceStage.EPISODE)
                .status(eventStatus)
                .summary(type.name())
                .payloadJson("{}")
                .payloadSchemaVersion(1)
                .producerId("TraceTest")
                .occurredAt(EVENT_TIME)
                .actor("SYSTEM:TraceTest")
                .build();
    }

    private TraceStage stageOf(TraceEventType type) {
        return type.name().startsWith("EPISODE_") ? TraceStage.EPISODE : TraceStage.CONTEXT;
    }

    private TraceEventStatus statusOf(TraceEventType type) {
        return switch (type) {
            case EPISODE_STARTED -> TraceEventStatus.STARTED;
            case EPISODE_COMPLETED -> TraceEventStatus.SUCCEEDED;
            default -> TraceEventStatus.SUCCEEDED;
        };
    }
}
