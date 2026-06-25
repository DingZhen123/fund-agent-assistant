package com.fundagent.repo.trace;

import com.fundagent.core.trace.AgentEpisode;
import com.fundagent.core.trace.EpisodeSeal;
import com.fundagent.core.trace.EpisodeStatus;
import com.fundagent.core.trace.Evidence;
import com.fundagent.core.trace.EvidenceReliabilityLevel;
import com.fundagent.core.trace.EvidenceVerificationStatus;
import com.fundagent.core.trace.RiskLevel;
import com.fundagent.core.trace.TraceEvent;
import com.fundagent.core.trace.TraceEventRelation;
import com.fundagent.core.trace.TraceEventStatus;
import com.fundagent.core.trace.TraceEventType;
import com.fundagent.core.trace.TraceRelationType;
import com.fundagent.core.trace.TraceStage;
import com.fundagent.repo.entity.AgentEpisodeEntity;
import com.fundagent.repo.entity.EpisodeSealEntity;
import com.fundagent.repo.entity.TraceEventEntity;
import com.fundagent.repo.entity.TraceEventRelationEntity;
import com.fundagent.repo.entity.TraceEvidenceEntity;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;

public class TraceEntityConverter {

    public AgentEpisode toDomain(AgentEpisodeEntity entity) {
        return AgentEpisode.builder()
                .episodeCode(entity.getEpisodeCode())
                .requestId(entity.getRequestId())
                .conversationId(entity.getConversationId())
                .userIdReference(entity.getUserIdReference())
                .agentVersion(entity.getAgentVersion())
                .originalGoalRedacted(entity.getOriginalGoal())
                .riskLevel(enumValue(RiskLevel.class, entity.getRiskLevel()))
                .status(enumValue(EpisodeStatus.class, entity.getStatus()))
                .nextSequenceNo(value(entity.getNextSequenceNo()))
                .lastEventHash(entity.getLastEventHash())
                .eventCount(value(entity.getEventCount()))
                .stepCount(value(entity.getStepCount()))
                .modelCallCount(value(entity.getModelCallCount()))
                .toolCallCount(value(entity.getToolCallCount()))
                .tokenUsage(value(entity.getTokenUsage()))
                .finalErrorCode(entity.getFinalErrorCode())
                .finalFailureStage(entity.getFinalFailureStage())
                .startedAt(toInstant(entity.getStartedAt()))
                .finishedAt(toInstant(entity.getFinishedAt()))
                .elapsedMs(entity.getElapsedMs())
                .sealed(Boolean.TRUE.equals(entity.getSealed()))
                .rowVersion(value(entity.getRowVersion()))
                .createdAt(toInstant(entity.getCreatedAt()))
                .updatedAt(toInstant(entity.getUpdatedAt()))
                .createdBy(entity.getCreatedBy())
                .updatedBy(entity.getUpdatedBy())
                .build();
    }

    public AgentEpisodeEntity newEpisodeEntity(AgentEpisode episode) {
        AgentEpisodeEntity entity = new AgentEpisodeEntity();
        applyEpisode(entity, episode);
        return entity;
    }

    public void applyEpisode(AgentEpisodeEntity entity, AgentEpisode episode) {
        entity.setEpisodeCode(episode.getEpisodeCode());
        entity.setRequestId(episode.getRequestId());
        entity.setConversationId(episode.getConversationId());
        entity.setUserIdReference(episode.getUserIdReference());
        entity.setAgentVersion(episode.getAgentVersion());
        entity.setOriginalGoal(episode.getOriginalGoalRedacted());
        entity.setRiskLevel(name(episode.getRiskLevel()));
        entity.setStatus(name(episode.getStatus()));
        entity.setNextSequenceNo(episode.getNextSequenceNo());
        entity.setLastEventHash(episode.getLastEventHash());
        entity.setEventCount(episode.getEventCount());
        entity.setStepCount(episode.getStepCount());
        entity.setModelCallCount(episode.getModelCallCount());
        entity.setToolCallCount(episode.getToolCallCount());
        entity.setTokenUsage(episode.getTokenUsage());
        entity.setFinalErrorCode(episode.getFinalErrorCode());
        entity.setFinalFailureStage(episode.getFinalFailureStage());
        entity.setStartedAt(toDateTime(episode.getStartedAt()));
        entity.setFinishedAt(toDateTime(episode.getFinishedAt()));
        entity.setElapsedMs(episode.getElapsedMs());
        entity.setSealed(episode.isSealed());
        entity.setRowVersion(episode.getRowVersion());
        entity.setCreatedAt(toDateTime(episode.getCreatedAt()));
        entity.setUpdatedAt(toDateTime(episode.getUpdatedAt()));
        entity.setCreatedBy(episode.getCreatedBy());
        entity.setUpdatedBy(episode.getUpdatedBy());
    }

    public TraceEvent toDomain(TraceEventEntity entity, String episodeCode,
                               String parentEventCode, String causationEventCode) {
        return TraceEvent.builder()
                .eventCode(entity.getEventCode())
                .episodeCode(episodeCode)
                .sequenceNo(value(entity.getSequenceNo()))
                .parentEventCode(parentEventCode)
                .causationEventCode(causationEventCode)
                .correlationId(entity.getCorrelationId())
                .eventType(enumValue(TraceEventType.class, entity.getEventType()))
                .stage(enumValue(TraceStage.class, entity.getStage()))
                .nodeId(entity.getNodeId())
                .capability(entity.getCapability())
                .toolName(entity.getToolName())
                .status(enumValue(TraceEventStatus.class, entity.getStatus()))
                .reasonCode(entity.getReasonCode())
                .summary(entity.getSummary())
                .payloadJson(entity.getPayloadJson())
                .payloadSchemaVersion(value(entity.getPayloadSchemaVersion()))
                .payloadHash(entity.getPayloadHash())
                .producerId(entity.getProducerId())
                .occurredAt(toInstant(entity.getOccurredAt()))
                .receivedAt(toInstant(entity.getReceivedAt()))
                .persistedAt(toInstant(entity.getPersistedAt()))
                .previousHash(entity.getPreviousHash())
                .eventHash(entity.getEventHash())
                .signatureAlgorithm(entity.getSignatureAlgorithm())
                .signingKeyId(entity.getSigningKeyId())
                .eventSignature(entity.getEventSignature())
                .createdBy(entity.getCreatedBy())
                .build();
    }

    public TraceEventEntity toEntity(TraceEvent event, Long episodeId,
                                     Long parentEventId, Long causationEventId) {
        TraceEventEntity entity = new TraceEventEntity();
        entity.setEventCode(event.getEventCode());
        entity.setEpisodeId(episodeId);
        entity.setSequenceNo(event.getSequenceNo());
        entity.setParentEventId(parentEventId);
        entity.setCausationEventId(causationEventId);
        entity.setCorrelationId(event.getCorrelationId());
        entity.setEventType(name(event.getEventType()));
        entity.setStage(name(event.getStage()));
        entity.setNodeId(event.getNodeId());
        entity.setCapability(event.getCapability());
        entity.setToolName(event.getToolName());
        entity.setStatus(name(event.getStatus()));
        entity.setReasonCode(event.getReasonCode());
        entity.setSummary(event.getSummary());
        entity.setPayloadJson(event.getPayloadJson());
        entity.setPayloadSchemaVersion(event.getPayloadSchemaVersion());
        entity.setPayloadHash(event.getPayloadHash());
        entity.setProducerId(event.getProducerId());
        entity.setOccurredAt(toDateTime(event.getOccurredAt()));
        entity.setReceivedAt(toDateTime(event.getReceivedAt()));
        entity.setPersistedAt(toDateTime(event.getPersistedAt()));
        entity.setPreviousHash(event.getPreviousHash());
        entity.setEventHash(event.getEventHash());
        entity.setSignatureAlgorithm(event.getSignatureAlgorithm());
        entity.setSigningKeyId(event.getSigningKeyId());
        entity.setEventSignature(event.getEventSignature());
        entity.setCreatedAt(toDateTime(event.getPersistedAt()));
        entity.setUpdatedAt(toDateTime(event.getPersistedAt()));
        entity.setCreatedBy(event.getCreatedBy());
        entity.setUpdatedBy(event.getCreatedBy());
        return entity;
    }

    public TraceEventRelation toDomain(TraceEventRelationEntity entity,
                                       String episodeCode, String sourceCode, String targetCode) {
        return TraceEventRelation.builder()
                .episodeCode(episodeCode)
                .sourceEventCode(sourceCode)
                .targetEventCode(targetCode)
                .relationType(enumValue(TraceRelationType.class, entity.getRelationType()))
                .createdAt(toInstant(entity.getCreatedAt()))
                .createdBy(entity.getCreatedBy())
                .build();
    }

    public TraceEventRelationEntity toEntity(TraceEventRelation relation,
                                             Long episodeId, Long sourceId, Long targetId) {
        TraceEventRelationEntity entity = new TraceEventRelationEntity();
        entity.setEpisodeId(episodeId);
        entity.setSourceEventId(sourceId);
        entity.setTargetEventId(targetId);
        entity.setRelationType(name(relation.getRelationType()));
        entity.setCreatedAt(toDateTime(relation.getCreatedAt()));
        entity.setUpdatedAt(toDateTime(relation.getCreatedAt()));
        entity.setCreatedBy(relation.getCreatedBy());
        entity.setUpdatedBy(relation.getCreatedBy());
        return entity;
    }

    public Evidence toDomain(TraceEvidenceEntity entity, String episodeCode, String eventCode) {
        return Evidence.builder()
                .evidenceCode(entity.getEvidenceCode())
                .episodeCode(episodeCode)
                .eventCode(eventCode)
                .nodeId(entity.getNodeId())
                .evidenceType(entity.getEvidenceType())
                .sourceType(entity.getSourceType())
                .sourceReference(entity.getSourceReference())
                .claim(entity.getClaim())
                .expectedValueRedacted(entity.getExpectedValue())
                .actualValueRedacted(entity.getActualValue())
                .reliabilityLevel(enumValue(EvidenceReliabilityLevel.class, entity.getReliabilityLevel()))
                .verificationStatus(enumValue(EvidenceVerificationStatus.class, entity.getVerificationStatus()))
                .payloadJson(entity.getPayloadJson())
                .payloadHash(entity.getPayloadHash())
                .collectedAt(toInstant(entity.getCollectedAt()))
                .createdAt(toInstant(entity.getCreatedAt()))
                .createdBy(entity.getCreatedBy())
                .build();
    }

    public TraceEvidenceEntity toEntity(Evidence evidence, Long episodeId, Long eventId) {
        TraceEvidenceEntity entity = new TraceEvidenceEntity();
        entity.setEvidenceCode(evidence.getEvidenceCode());
        entity.setEpisodeId(episodeId);
        entity.setEventId(eventId);
        entity.setNodeId(evidence.getNodeId());
        entity.setEvidenceType(evidence.getEvidenceType());
        entity.setSourceType(evidence.getSourceType());
        entity.setSourceReference(evidence.getSourceReference());
        entity.setClaim(evidence.getClaim());
        entity.setExpectedValue(evidence.getExpectedValueRedacted());
        entity.setActualValue(evidence.getActualValueRedacted());
        entity.setReliabilityLevel(name(evidence.getReliabilityLevel()));
        entity.setVerificationStatus(name(evidence.getVerificationStatus()));
        entity.setPayloadJson(evidence.getPayloadJson());
        entity.setPayloadHash(evidence.getPayloadHash());
        entity.setCollectedAt(toDateTime(evidence.getCollectedAt()));
        entity.setCreatedAt(toDateTime(evidence.getCreatedAt()));
        entity.setUpdatedAt(toDateTime(evidence.getCreatedAt()));
        entity.setCreatedBy(evidence.getCreatedBy());
        entity.setUpdatedBy(evidence.getCreatedBy());
        return entity;
    }

    public EpisodeSeal toDomain(EpisodeSealEntity entity, String episodeCode) {
        return EpisodeSeal.builder()
                .sealCode(entity.getSealCode())
                .episodeCode(episodeCode)
                .finalSequenceNo(value(entity.getFinalSequenceNo()))
                .finalEventHash(entity.getFinalEventHash())
                .eventCount(value(entity.getEventCount()))
                .finalStatus(enumValue(EpisodeStatus.class, entity.getFinalStatus()))
                .sealedAt(toInstant(entity.getSealedAt()))
                .signatureAlgorithm(entity.getSignatureAlgorithm())
                .signingKeyId(entity.getSigningKeyId())
                .sealSignature(entity.getSealSignature())
                .createdAt(toInstant(entity.getCreatedAt()))
                .createdBy(entity.getCreatedBy())
                .build();
    }

    public EpisodeSealEntity toEntity(EpisodeSeal seal, Long episodeId) {
        EpisodeSealEntity entity = new EpisodeSealEntity();
        entity.setSealCode(seal.getSealCode());
        entity.setEpisodeId(episodeId);
        entity.setFinalSequenceNo(seal.getFinalSequenceNo());
        entity.setFinalEventHash(seal.getFinalEventHash());
        entity.setEventCount(seal.getEventCount());
        entity.setFinalStatus(name(seal.getFinalStatus()));
        entity.setSealedAt(toDateTime(seal.getSealedAt()));
        entity.setSignatureAlgorithm(seal.getSignatureAlgorithm());
        entity.setSigningKeyId(seal.getSigningKeyId());
        entity.setSealSignature(seal.getSealSignature());
        entity.setCreatedAt(toDateTime(seal.getCreatedAt()));
        entity.setUpdatedAt(toDateTime(seal.getCreatedAt()));
        entity.setCreatedBy(seal.getCreatedBy());
        entity.setUpdatedBy(seal.getCreatedBy());
        return entity;
    }

    public Instant truncateToMicros(Instant value) {
        return value == null ? null : value.truncatedTo(ChronoUnit.MICROS);
    }

    private LocalDateTime toDateTime(Instant value) {
        return value == null ? null : LocalDateTime.ofInstant(truncateToMicros(value), ZoneOffset.UTC);
    }

    private Instant toInstant(LocalDateTime value) {
        return value == null ? null : value.toInstant(ZoneOffset.UTC);
    }

    private String name(Enum<?> value) {
        return value == null ? null : value.name();
    }

    private <T extends Enum<T>> T enumValue(Class<T> type, String value) {
        return value == null ? null : Enum.valueOf(type, value);
    }

    private long value(Long value) {
        return value == null ? 0 : value;
    }

    private int value(Integer value) {
        return value == null ? 0 : value;
    }
}
