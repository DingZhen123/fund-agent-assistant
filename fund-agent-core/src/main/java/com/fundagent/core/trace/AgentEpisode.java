package com.fundagent.core.trace;

import lombok.Builder;
import lombok.Value;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@Value
@Builder(toBuilder = true)
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

    public TraceAppendResult append(TraceContext context, AppendTraceEventCommand command,
                                    TraceSecurity security, Instant receivedAt) {
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(command, "command");
        Objects.requireNonNull(security, "security");
        Objects.requireNonNull(receivedAt, "receivedAt");
        requireText(episodeCode, "episodeCode");
        requireText(command.getEventCode(), "eventCode");
        requireText(command.getProducerId(), "producerId");
        requireText(command.getActor(), "actor");
        Objects.requireNonNull(command.getEventType(), "eventType");
        Objects.requireNonNull(command.getStage(), "stage");
        Objects.requireNonNull(command.getStatus(), "status");
        Objects.requireNonNull(command.getOccurredAt(), "occurredAt");
        if (sealed) {
            throw new IllegalStateException("Episode is sealed and cannot append events: " + episodeCode);
        }
        if (status.isSealable()) {
            throw new IllegalStateException("Episode is already in final status: " + status);
        }
        if (!episodeCode.equals(context.getEpisodeCode())) {
            throw new IllegalArgumentException("TraceContext episode does not match AgentEpisode");
        }
        validateLifecycleEventStatus(command.getEventType(), command.getStatus());

        EpisodeStatus nextStatus = resolveStatus(command.getEventType());
        validateTransition(status, nextStatus, command.getEventType());

        Set<String> causationCodes = new LinkedHashSet<>(command.getCausationEventCodes());
        if (causationCodes.isEmpty() && context.getCurrentEventCode() != null) {
            causationCodes.add(context.getCurrentEventCode());
        }
        String primaryCausation = choosePrimaryCausation(causationCodes, context.getCurrentEventCode());
        String parentEventCode = command.getParentEventCode() != null
                ? command.getParentEventCode()
                : context.getCurrentEventCode();
        String previousHash = lastEventHash == null ? TraceSecurity.GENESIS_HASH : lastEventHash;

        TraceEvent event = security.protectEvent(TraceEvent.builder()
                .eventCode(command.getEventCode())
                .episodeCode(episodeCode)
                .sequenceNo(nextSequenceNo)
                .parentEventCode(parentEventCode)
                .causationEventCode(primaryCausation)
                .correlationId(command.getCorrelationId() != null
                        ? command.getCorrelationId()
                        : context.getCorrelationId())
                .eventType(command.getEventType())
                .stage(command.getStage())
                .nodeId(command.getNodeId())
                .capability(command.getCapability())
                .toolName(command.getToolName())
                .status(command.getStatus())
                .reasonCode(command.getReasonCode())
                .summary(command.getSummary())
                .payloadJson(command.getPayloadJson())
                .payloadSchemaVersion(command.getPayloadSchemaVersion() > 0
                        ? command.getPayloadSchemaVersion()
                        : 1)
                .producerId(command.getProducerId())
                .occurredAt(command.getOccurredAt())
                .receivedAt(receivedAt)
                .persistedAt(receivedAt)
                .previousHash(previousHash)
                .createdBy(command.getActor())
                .build());

        Long nextElapsedMs = elapsedMs;
        if (nextStatus.isSealable() && startedAt != null) {
            nextElapsedMs = Math.max(0, receivedAt.toEpochMilli() - startedAt.toEpochMilli());
        }
        AgentEpisode updated = toBuilder()
                .status(nextStatus)
                .nextSequenceNo(nextSequenceNo + 1)
                .lastEventHash(event.getEventHash())
                .eventCount(eventCount + 1)
                .stepCount(stepCount + increment(command.getEventType(), TraceEventType.NODE_STARTED))
                .modelCallCount(modelCallCount + increment(command.getEventType(), TraceEventType.MODEL_CALL_STARTED))
                .toolCallCount(toolCallCount + increment(command.getEventType(), TraceEventType.TOOL_CALL_STARTED))
                .finishedAt(nextStatus.isSealable() ? receivedAt : finishedAt)
                .elapsedMs(nextElapsedMs)
                .rowVersion(rowVersion + 1)
                .updatedAt(receivedAt)
                .updatedBy(command.getActor())
                .build();

        TraceContext nextContext = TraceContext.builder()
                .episodeCode(context.getEpisodeCode())
                .currentEventCode(event.getEventCode())
                .causationEventCode(primaryCausation)
                .correlationId(event.getCorrelationId())
                .requestId(context.getRequestId())
                .traceFlags(context.getTraceFlags())
                .build();

        return TraceAppendResult.builder()
                .episode(updated)
                .event(event)
                .context(nextContext)
                .relations(buildRelations(event, causationCodes, command.getActor(), receivedAt))
                .build();
    }

    public EpisodeSealResult seal(String sealCode, String actor, TraceSecurity security, Instant sealedAt) {
        requireText(sealCode, "sealCode");
        requireText(actor, "actor");
        Objects.requireNonNull(security, "security");
        Objects.requireNonNull(sealedAt, "sealedAt");
        if (sealed) {
            throw new IllegalStateException("Episode is already sealed: " + episodeCode);
        }
        if (!status.isSealable()) {
            throw new IllegalStateException("Episode status cannot be sealed: " + status);
        }
        if (eventCount == 0 || lastEventHash == null) {
            throw new IllegalStateException("Episode without events cannot be sealed");
        }
        EpisodeSeal seal = security.createSeal(this, sealCode, actor, sealedAt);
        AgentEpisode updated = toBuilder()
                .sealed(true)
                .rowVersion(rowVersion + 1)
                .updatedAt(sealedAt)
                .updatedBy(actor)
                .build();
        return new EpisodeSealResult(updated, seal);
    }

    private EpisodeStatus resolveStatus(TraceEventType eventType) {
        return switch (eventType) {
            case EPISODE_CREATED -> EpisodeStatus.CREATED;
            case EPISODE_STARTED -> EpisodeStatus.RUNNING;
            case EPISODE_WAITING_USER -> EpisodeStatus.WAITING_USER;
            case EPISODE_WAITING_CONFIRMATION -> EpisodeStatus.WAITING_CONFIRMATION;
            case EPISODE_COMPLETED -> EpisodeStatus.COMPLETED;
            case EPISODE_FAILED -> EpisodeStatus.FAILED;
            case EPISODE_ABORTED -> EpisodeStatus.ABORTED;
            case EPISODE_RESULT_UNKNOWN -> EpisodeStatus.RESULT_UNKNOWN;
            default -> status;
        };
    }

    private void validateTransition(EpisodeStatus current, EpisodeStatus next, TraceEventType eventType) {
        if (current == next && eventType != TraceEventType.EPISODE_CREATED) {
            return;
        }
        boolean allowed = switch (current) {
            case CREATED -> next == EpisodeStatus.CREATED
                    || next == EpisodeStatus.RUNNING
                    || next == EpisodeStatus.FAILED
                    || next == EpisodeStatus.ABORTED;
            case RUNNING -> next == EpisodeStatus.RUNNING
                    || next == EpisodeStatus.WAITING_USER
                    || next == EpisodeStatus.WAITING_CONFIRMATION
                    || next == EpisodeStatus.COMPLETED
                    || next == EpisodeStatus.FAILED
                    || next == EpisodeStatus.ABORTED
                    || next == EpisodeStatus.RESULT_UNKNOWN;
            case WAITING_USER, WAITING_CONFIRMATION -> next == current
                    || next == EpisodeStatus.RUNNING
                    || next == EpisodeStatus.FAILED
                    || next == EpisodeStatus.ABORTED;
            case RESULT_UNKNOWN -> next == EpisodeStatus.RESULT_UNKNOWN
                    || next == EpisodeStatus.RUNNING
                    || next == EpisodeStatus.COMPLETED
                    || next == EpisodeStatus.FAILED
                    || next == EpisodeStatus.ABORTED;
            case COMPLETED, FAILED, ABORTED -> next == current;
        };
        if (!allowed) {
            throw new IllegalStateException(
                    "Illegal Episode status transition: " + current + " -> " + next);
        }
    }

    private void validateLifecycleEventStatus(TraceEventType eventType, TraceEventStatus eventStatus) {
        TraceEventStatus expected = switch (eventType) {
            case EPISODE_CREATED -> TraceEventStatus.CREATED;
            case EPISODE_STARTED -> TraceEventStatus.STARTED;
            case EPISODE_WAITING_USER, EPISODE_WAITING_CONFIRMATION -> TraceEventStatus.WAITING;
            case EPISODE_COMPLETED -> TraceEventStatus.SUCCEEDED;
            case EPISODE_FAILED -> TraceEventStatus.FAILED;
            case EPISODE_ABORTED -> TraceEventStatus.REJECTED;
            case EPISODE_RESULT_UNKNOWN -> TraceEventStatus.RESULT_UNKNOWN;
            default -> null;
        };
        if (expected != null && eventStatus != expected) {
            throw new IllegalArgumentException(
                    "Lifecycle event status mismatch: " + eventType + " requires event status " + expected);
        }
    }

    private String choosePrimaryCausation(Set<String> causationCodes, String currentEventCode) {
        if (currentEventCode != null && causationCodes.contains(currentEventCode)) {
            return currentEventCode;
        }
        return causationCodes.stream().sorted().findFirst().orElse(null);
    }

    private List<TraceEventRelation> buildRelations(TraceEvent event, Set<String> causationCodes,
                                                    String actor, Instant createdAt) {
        List<TraceEventRelation> relations = new ArrayList<>();
        causationCodes.stream()
                .filter(code -> code != null && !code.isBlank())
                .sorted(Comparator.naturalOrder())
                .forEach(code -> relations.add(TraceEventRelation.builder()
                        .episodeCode(episodeCode)
                        .sourceEventCode(code)
                        .targetEventCode(event.getEventCode())
                        .relationType(TraceRelationType.CAUSED_BY)
                        .createdAt(createdAt)
                        .createdBy(actor)
                        .build()));
        return relations;
    }

    private int increment(TraceEventType actual, TraceEventType counted) {
        return actual == counted ? 1 : 0;
    }

    private void requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
    }
}
