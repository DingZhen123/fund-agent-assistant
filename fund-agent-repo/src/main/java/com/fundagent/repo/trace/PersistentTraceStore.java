package com.fundagent.repo.trace;

import com.alibaba.fastjson2.JSON;
import com.fundagent.core.trace.AgentEpisode;
import com.fundagent.core.trace.AgentTrace;
import com.fundagent.core.trace.AppendEvidenceCommand;
import com.fundagent.core.trace.AppendTraceEventCommand;
import com.fundagent.core.trace.CreateEpisodeCommand;
import com.fundagent.core.trace.EpisodeSeal;
import com.fundagent.core.trace.EpisodeStatus;
import com.fundagent.core.trace.Evidence;
import com.fundagent.core.trace.TraceAppendResult;
import com.fundagent.core.trace.TraceContext;
import com.fundagent.core.trace.TraceEvent;
import com.fundagent.core.trace.TraceEventRelation;
import com.fundagent.core.trace.TraceEventStatus;
import com.fundagent.core.trace.TraceEventType;
import com.fundagent.core.trace.TraceIntegrityResult;
import com.fundagent.core.trace.TraceSecurity;
import com.fundagent.core.trace.TraceStage;
import com.fundagent.core.trace.TraceStore;
import com.fundagent.repo.entity.AgentEpisodeEntity;
import com.fundagent.repo.entity.EpisodeSealEntity;
import com.fundagent.repo.entity.TraceEventEntity;
import com.fundagent.repo.entity.TraceEventRelationEntity;
import com.fundagent.repo.entity.TraceEvidenceEntity;
import com.fundagent.repo.mapper.AgentEpisodeMapper;
import com.fundagent.repo.mapper.EpisodeSealMapper;
import com.fundagent.repo.mapper.TraceEventMapper;
import com.fundagent.repo.mapper.TraceEventRelationMapper;
import com.fundagent.repo.mapper.TraceEvidenceMapper;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

public class PersistentTraceStore implements TraceStore {
    private final AgentEpisodeMapper episodeMapper;
    private final TraceEventMapper eventMapper;
    private final TraceEventRelationMapper relationMapper;
    private final TraceEvidenceMapper evidenceMapper;
    private final EpisodeSealMapper sealMapper;
    private final TraceEntityConverter converter;
    private final TraceSecurity security;
    private final Clock clock;

    public PersistentTraceStore(AgentEpisodeMapper episodeMapper,
                                TraceEventMapper eventMapper,
                                TraceEventRelationMapper relationMapper,
                                TraceEvidenceMapper evidenceMapper,
                                EpisodeSealMapper sealMapper,
                                TraceEntityConverter converter,
                                TraceSecurity security,
                                Clock clock) {
        this.episodeMapper = episodeMapper;
        this.eventMapper = eventMapper;
        this.relationMapper = relationMapper;
        this.evidenceMapper = evidenceMapper;
        this.sealMapper = sealMapper;
        this.converter = converter;
        this.security = security;
        this.clock = clock;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public TraceAppendResult createEpisode(CreateEpisodeCommand command) {
        validateCreateCommand(command);
        AgentEpisodeEntity existing = findExistingEpisode(command);
        if (existing != null) {
            AgentEpisodeEntity lockedExisting = episodeMapper.lockByEpisodeCode(existing.getEpisodeCode());
            if (lockedExisting == null) {
                throw new TracePersistenceException("Existing Episode cannot be locked");
            }
            assertSameCreateRequest(lockedExisting, command);
            return returnCreatedEpisodeResult(lockedExisting, command);
        }

        Instant now = converter.truncateToMicros(clock.instant());
        AgentEpisode initial = AgentEpisode.builder()
                .episodeCode(command.getEpisodeCode())
                .requestId(command.getRequestId())
                .conversationId(command.getConversationId())
                .userIdReference(command.getUserIdReference())
                .agentVersion(command.getAgentVersion())
                .originalGoalRedacted(command.getOriginalGoalRedacted())
                .riskLevel(command.getRiskLevel())
                .status(EpisodeStatus.CREATED)
                .nextSequenceNo(1)
                .eventCount(0)
                .startedAt(converter.truncateToMicros(command.getStartedAt()))
                .sealed(false)
                .rowVersion(0)
                .createdAt(now)
                .updatedAt(now)
                .createdBy(command.getActor())
                .updatedBy(command.getActor())
                .build();
        AgentEpisodeEntity entity = converter.newEpisodeEntity(initial);
        int inserted = episodeMapper.insertIdempotent(entity);
        if (inserted != 1) {
            AgentEpisodeEntity concurrent = findExistingEpisodeForUpdate(command);
            if (concurrent == null) {
                throw new TracePersistenceException("Episode upsert found no existing row");
            }
            assertSameCreateRequest(concurrent, command);
            return returnCreatedEpisodeResult(concurrent, command);
        }

        AgentEpisodeEntity locked = episodeMapper.lockByEpisodeCode(command.getEpisodeCode());
        if (locked == null) {
            locked = episodeMapper.lockByRequestId(command.getRequestId());
        }
        if (locked == null) {
            throw new TracePersistenceException("Created Episode cannot be locked: " + command.getEpisodeCode());
        }
        assertSameCreateRequest(locked, command);
        TraceContext context = TraceContext.builder()
                .episodeCode(command.getEpisodeCode())
                .requestId(command.getRequestId())
                .correlationId(command.getRequestId())
                .build();
        AppendTraceEventCommand createdEvent = AppendTraceEventCommand.builder()
                .eventCode(deterministicCode("episode-created", command.getEpisodeCode()))
                .correlationId(command.getRequestId())
                .eventType(TraceEventType.EPISODE_CREATED)
                .stage(TraceStage.EPISODE)
                .status(TraceEventStatus.CREATED)
                .summary("Agent Episode已创建")
                .payloadJson(JSON.toJSONString(Map.of(
                        "agentVersion", command.getAgentVersion(),
                        "riskLevel", command.getRiskLevel().name())))
                .payloadSchemaVersion(1)
                .producerId("PersistentTraceStore")
                .occurredAt(converter.truncateToMicros(command.getStartedAt()))
                .actor(command.getActor())
                .build();
        return appendLocked(locked, context, createdEvent, now);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public TraceAppendResult append(TraceContext context, AppendTraceEventCommand command) {
        requireText(context != null ? context.getEpisodeCode() : null, "context.episodeCode");
        requireText(command != null ? command.getEventCode() : null, "command.eventCode");
        AgentEpisodeEntity locked = episodeMapper.lockByEpisodeCode(context.getEpisodeCode());
        if (locked == null) {
            throw new TracePersistenceException("Episode does not exist: " + context.getEpisodeCode());
        }
        return appendLocked(locked, context, command, converter.truncateToMicros(clock.instant()));
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Evidence appendEvidence(TraceContext context, AppendEvidenceCommand command) {
        requireText(context != null ? context.getEpisodeCode() : null, "context.episodeCode");
        requireText(command != null ? command.getEvidenceCode() : null, "command.evidenceCode");
        AgentEpisodeEntity locked = episodeMapper.lockByEpisodeCode(context.getEpisodeCode());
        if (locked == null) {
            throw new TracePersistenceException("Episode does not exist: " + context.getEpisodeCode());
        }

        TraceEvidenceEntity existing = evidenceMapper.findByEvidenceCode(command.getEvidenceCode());
        if (existing != null) {
            return returnIdempotentEvidence(locked, existing, command);
        }

        AgentTrace trace = loadTrace(locked);
        Instant now = converter.truncateToMicros(clock.instant());
        AgentTrace updated = trace.appendEvidence(command, security, now);
        Evidence evidence = updated.getEvidence().get(updated.getEvidence().size() - 1);
        TraceEventEntity event = eventMapper.findByEventCode(command.getEventCode());
        if (event == null || !locked.getId().equals(event.getEpisodeId())) {
            throw new TracePersistenceException("Evidence references an event outside the Episode: "
                    + command.getEventCode());
        }
        TraceEvidenceEntity entity = converter.toEntity(evidence, locked.getId(), event.getId());
        if (evidenceMapper.insert(entity) != 1) {
            throw new TracePersistenceException("Evidence insert failed: " + command.getEvidenceCode());
        }
        return evidence;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public EpisodeSeal sealEpisode(String episodeCode, EpisodeStatus finalStatus, String actor) {
        requireText(episodeCode, "episodeCode");
        requireText(actor, "actor");
        Objects.requireNonNull(finalStatus, "finalStatus");
        AgentEpisodeEntity locked = episodeMapper.lockByEpisodeCode(episodeCode);
        if (locked == null) {
            throw new TracePersistenceException("Episode does not exist: " + episodeCode);
        }
        AgentEpisode episode = converter.toDomain(locked);
        if (episode.getStatus() != finalStatus) {
            throw new TraceIdempotencyConflictException(
                    "Seal final status does not match Episode status: " + finalStatus + " != " + episode.getStatus());
        }
        EpisodeSealEntity existing = sealMapper.findByEpisodeId(locked.getId());
        if (existing != null) {
            EpisodeSeal seal = converter.toDomain(existing, episodeCode);
            AgentTrace persisted = loadTrace(locked);
            TraceIntegrityResult integrity = persisted.verifyIntegrity(security);
            if (seal.getFinalStatus() != finalStatus || !integrity.isValid()) {
                throw new TraceIdempotencyConflictException("Existing Episode seal does not match request");
            }
            return seal;
        }

        AgentTrace trace = loadTrace(locked);
        TraceIntegrityResult integrity = trace.verifyIntegrity(security);
        if (!integrity.isValid()) {
            throw new TracePersistenceException(
                    "Cannot seal invalid Trace: " + integrity.getReasonCode() + " " + integrity.getMessage());
        }
        Instant now = converter.truncateToMicros(clock.instant());
        AgentTrace sealed = trace.seal(
                deterministicCode("episode-seal", episodeCode),
                actor,
                security,
                now);
        EpisodeSeal seal = sealed.getSeal();
        if (!security.verifySeal(seal)) {
            throw new TracePersistenceException("Generated Episode seal failed verification");
        }
        if (sealMapper.insert(converter.toEntity(seal, locked.getId())) != 1) {
            throw new TracePersistenceException("Episode seal insert failed: " + episodeCode);
        }
        updateProjection(locked, sealed.getEpisode());
        return seal;
    }

    @Override
    @Transactional(readOnly = true)
    public AgentTrace loadTrace(String episodeCode) {
        requireText(episodeCode, "episodeCode");
        AgentEpisodeEntity episode = episodeMapper.findByEpisodeCode(episodeCode);
        if (episode == null) {
            throw new TracePersistenceException("Episode does not exist: " + episodeCode);
        }
        return loadTrace(episode);
    }

    @Override
    @Transactional(readOnly = true)
    public TraceIntegrityResult verifyIntegrity(String episodeCode) {
        return loadTrace(episodeCode).verifyIntegrity(security);
    }

    private TraceAppendResult appendLocked(AgentEpisodeEntity locked, TraceContext context,
                                           AppendTraceEventCommand command, Instant receivedAt) {
        TraceEventEntity existing = eventMapper.findByEventCode(command.getEventCode());
        if (existing != null) {
            return returnIdempotentEvent(locked, existing, context, command);
        }

        List<TraceEventEntity> currentEvents = eventMapper.findByEpisodeId(locked.getId());
        Map<String, TraceEventEntity> eventsByCode = currentEvents.stream()
                .collect(Collectors.toMap(TraceEventEntity::getEventCode, Function.identity()));
        validateEventReferences(locked, context, command, eventsByCode);

        AgentEpisode episode = converter.toDomain(locked);
        TraceAppendResult result = episode.append(context, command, security, receivedAt);
        TraceEvent event = result.getEvent();
        Long parentId = event.getParentEventCode() == null
                ? null : eventsByCode.get(event.getParentEventCode()).getId();
        Long causationId = event.getCausationEventCode() == null
                ? null : eventsByCode.get(event.getCausationEventCode()).getId();
        TraceEventEntity eventEntity = converter.toEntity(event, locked.getId(), parentId, causationId);
        if (eventMapper.insert(eventEntity) != 1) {
            throw new TracePersistenceException("Trace event insert failed: " + event.getEventCode());
        }

        for (TraceEventRelation relation : result.getRelations()) {
            TraceEventEntity source = eventsByCode.get(relation.getSourceEventCode());
            if (source == null) {
                throw new TracePersistenceException("Causation event does not exist: "
                        + relation.getSourceEventCode());
            }
            TraceEventRelationEntity relationEntity = converter.toEntity(
                    relation, locked.getId(), source.getId(), eventEntity.getId());
            if (relationMapper.insert(relationEntity) != 1) {
                throw new TracePersistenceException("Trace relation insert failed");
            }
        }
        updateProjection(locked, result.getEpisode());
        return result;
    }

    private TraceAppendResult returnIdempotentEvent(AgentEpisodeEntity locked,
                                                    TraceEventEntity existing,
                                                    TraceContext context,
                                                    AppendTraceEventCommand command) {
        if (!locked.getId().equals(existing.getEpisodeId())) {
            throw new TraceIdempotencyConflictException(
                    "eventCode already belongs to another Episode: " + command.getEventCode());
        }
        List<TraceEventEntity> events = eventMapper.findByEpisodeId(locked.getId());
        Map<Long, TraceEventEntity> byId = events.stream()
                .collect(Collectors.toMap(TraceEventEntity::getId, Function.identity()));
        TraceEvent domain = converter.toDomain(
                existing,
                locked.getEpisodeCode(),
                codeOf(byId, existing.getParentEventId()),
                codeOf(byId, existing.getCausationEventId()));
        List<TraceEventRelation> relations = relationsForTarget(existing, locked.getEpisodeCode(), byId);
        assertSameEventRequest(domain, relations, context, command);
        TraceContext nextContext = TraceContext.builder()
                .episodeCode(locked.getEpisodeCode())
                .currentEventCode(domain.getEventCode())
                .causationEventCode(domain.getCausationEventCode())
                .correlationId(domain.getCorrelationId())
                .requestId(context.getRequestId())
                .traceFlags(context.getTraceFlags())
                .build();
        return TraceAppendResult.builder()
                .episode(converter.toDomain(locked))
                .event(domain)
                .context(nextContext)
                .relations(relations)
                .build();
    }

    private Evidence returnIdempotentEvidence(AgentEpisodeEntity locked,
                                              TraceEvidenceEntity existing,
                                              AppendEvidenceCommand command) {
        if (!locked.getId().equals(existing.getEpisodeId())) {
            throw new TraceIdempotencyConflictException(
                    "evidenceCode already belongs to another Episode: " + command.getEvidenceCode());
        }
        TraceEventEntity event = eventMapper.selectById(existing.getEventId());
        if (event == null) {
            throw new TracePersistenceException("Existing Evidence references missing event");
        }
        Evidence evidence = converter.toDomain(existing, locked.getEpisodeCode(), event.getEventCode());
        assertSameEvidenceRequest(evidence, command);
        return evidence;
    }

    private AgentTrace loadTrace(AgentEpisodeEntity episodeEntity) {
        List<TraceEventEntity> eventEntities = eventMapper.findByEpisodeId(episodeEntity.getId());
        Map<Long, TraceEventEntity> byId = eventEntities.stream()
                .collect(Collectors.toMap(TraceEventEntity::getId, Function.identity(), (a, b) -> a,
                        LinkedHashMap::new));
        List<TraceEvent> events = eventEntities.stream()
                .map(entity -> converter.toDomain(
                        entity,
                        episodeEntity.getEpisodeCode(),
                        codeOf(byId, entity.getParentEventId()),
                        codeOf(byId, entity.getCausationEventId())))
                .toList();
        List<TraceEventRelation> relations = relationMapper.findByEpisodeId(episodeEntity.getId()).stream()
                .map(entity -> converter.toDomain(
                        entity,
                        episodeEntity.getEpisodeCode(),
                        requiredCodeOf(byId, entity.getSourceEventId()),
                        requiredCodeOf(byId, entity.getTargetEventId())))
                .toList();
        List<Evidence> evidence = evidenceMapper.findByEpisodeId(episodeEntity.getId()).stream()
                .map(entity -> converter.toDomain(
                        entity,
                        episodeEntity.getEpisodeCode(),
                        requiredCodeOf(byId, entity.getEventId())))
                .toList();
        EpisodeSealEntity sealEntity = sealMapper.findByEpisodeId(episodeEntity.getId());
        EpisodeSeal seal = sealEntity == null
                ? null : converter.toDomain(sealEntity, episodeEntity.getEpisodeCode());
        return AgentTrace.builder()
                .episode(converter.toDomain(episodeEntity))
                .events(events)
                .relations(relations)
                .evidence(evidence)
                .seal(seal)
                .build();
    }

    private List<TraceEventRelation> relationsForTarget(TraceEventEntity target, String episodeCode,
                                                        Map<Long, TraceEventEntity> byId) {
        return relationMapper.findByTargetEventId(target.getId()).stream()
                .map(entity -> converter.toDomain(
                        entity,
                        episodeCode,
                        requiredCodeOf(byId, entity.getSourceEventId()),
                        requiredCodeOf(byId, entity.getTargetEventId())))
                .toList();
    }

    private void updateProjection(AgentEpisodeEntity locked, AgentEpisode updated) {
        int rows = episodeMapper.updateProjection(
                locked.getId(),
                updated.getStatus().name(),
                updated.getNextSequenceNo(),
                updated.getLastEventHash(),
                updated.getEventCount(),
                updated.getStepCount(),
                updated.getModelCallCount(),
                updated.getToolCallCount(),
                updated.getTokenUsage(),
                updated.getFinalErrorCode(),
                updated.getFinalFailureStage(),
                toDateTime(updated.getFinishedAt()),
                updated.getElapsedMs(),
                updated.isSealed(),
                updated.getRowVersion(),
                toDateTime(updated.getUpdatedAt()),
                updated.getUpdatedBy(),
                locked.getRowVersion());
        if (rows != 1) {
            throw new TracePersistenceException(
                    "Episode projection update lost optimistic-lock race: " + locked.getEpisodeCode());
        }
    }

    private void validateEventReferences(AgentEpisodeEntity episode,
                                         TraceContext context,
                                         AppendTraceEventCommand command,
                                         Map<String, TraceEventEntity> eventsByCode) {
        Set<String> references = new LinkedHashSet<>(command.getCausationEventCodes());
        if (command.getParentEventCode() != null) {
            references.add(command.getParentEventCode());
        }
        if (context.getCurrentEventCode() != null) {
            references.add(context.getCurrentEventCode());
        }
        for (String code : references) {
            TraceEventEntity referenced = eventsByCode.get(code);
            if (referenced == null || !episode.getId().equals(referenced.getEpisodeId())) {
                throw new TracePersistenceException(
                        "Trace event reference does not exist in Episode: " + code);
            }
        }
    }

    private AgentEpisodeEntity findExistingEpisode(CreateEpisodeCommand command) {
        AgentEpisodeEntity byRequest = episodeMapper.findByRequestId(command.getRequestId());
        AgentEpisodeEntity byCode = episodeMapper.findByEpisodeCode(command.getEpisodeCode());
        if (byRequest != null && byCode != null && !Objects.equals(byRequest.getId(), byCode.getId())) {
            throw new TraceIdempotencyConflictException(
                    "requestId and episodeCode refer to different Episodes");
        }
        return byRequest != null ? byRequest : byCode;
    }

    private AgentEpisodeEntity findExistingEpisodeForUpdate(CreateEpisodeCommand command) {
        AgentEpisodeEntity byRequest = episodeMapper.lockByRequestId(command.getRequestId());
        AgentEpisodeEntity byCode = episodeMapper.lockByEpisodeCode(command.getEpisodeCode());
        if (byRequest != null && byCode != null && !Objects.equals(byRequest.getId(), byCode.getId())) {
            throw new TraceIdempotencyConflictException(
                    "requestId and episodeCode refer to different Episodes");
        }
        return byRequest != null ? byRequest : byCode;
    }

    private TraceAppendResult returnCreatedEpisodeResult(AgentEpisodeEntity existing,
                                                         CreateEpisodeCommand command) {
        TraceContext context = TraceContext.builder()
                .episodeCode(command.getEpisodeCode())
                .requestId(command.getRequestId())
                .correlationId(command.getRequestId())
                .build();
        AppendTraceEventCommand createdEvent = AppendTraceEventCommand.builder()
                .eventCode(deterministicCode("episode-created", command.getEpisodeCode()))
                .correlationId(command.getRequestId())
                .eventType(TraceEventType.EPISODE_CREATED)
                .stage(TraceStage.EPISODE)
                .status(TraceEventStatus.CREATED)
                .summary("Agent Episode已创建")
                .payloadJson(JSON.toJSONString(Map.of(
                        "agentVersion", command.getAgentVersion(),
                        "riskLevel", command.getRiskLevel().name())))
                .payloadSchemaVersion(1)
                .producerId("PersistentTraceStore")
                .occurredAt(converter.truncateToMicros(command.getStartedAt()))
                .actor(command.getActor())
                .build();
        TraceEventEntity persisted = eventMapper.findByEventCode(createdEvent.getEventCode());
        if (persisted == null) {
            throw new TracePersistenceException(
                    "Existing Episode is missing EPISODE_CREATED event: " + command.getEpisodeCode());
        }
        return returnIdempotentEvent(existing, persisted, context, createdEvent);
    }

    private void assertSameCreateRequest(AgentEpisodeEntity existing, CreateEpisodeCommand command) {
        boolean same = Objects.equals(existing.getEpisodeCode(), command.getEpisodeCode())
                && Objects.equals(existing.getRequestId(), command.getRequestId())
                && Objects.equals(existing.getConversationId(), command.getConversationId())
                && Objects.equals(existing.getUserIdReference(), command.getUserIdReference())
                && Objects.equals(existing.getAgentVersion(), command.getAgentVersion())
                && Objects.equals(existing.getOriginalGoal(), command.getOriginalGoalRedacted())
                && Objects.equals(existing.getRiskLevel(), command.getRiskLevel().name())
                && Objects.equals(toInstant(existing.getStartedAt()),
                converter.truncateToMicros(command.getStartedAt()))
                && Objects.equals(existing.getCreatedBy(), command.getActor());
        if (!same) {
            throw new TraceIdempotencyConflictException(
                    "Episode idempotency key was reused with different content");
        }
    }

    private void assertSameEventRequest(TraceEvent existing,
                                        List<TraceEventRelation> relations,
                                        TraceContext context,
                                        AppendTraceEventCommand command) {
        String effectiveParent = command.getParentEventCode() != null
                ? command.getParentEventCode() : context.getCurrentEventCode();
        Set<String> expectedCauses = new LinkedHashSet<>(command.getCausationEventCodes());
        if (expectedCauses.isEmpty() && context.getCurrentEventCode() != null) {
            expectedCauses.add(context.getCurrentEventCode());
        }
        Set<String> actualCauses = relations.stream()
                .map(TraceEventRelation::getSourceEventCode)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        String effectiveCorrelation = command.getCorrelationId() != null
                ? command.getCorrelationId() : context.getCorrelationId();
        boolean same = Objects.equals(existing.getEventCode(), command.getEventCode())
                && Objects.equals(existing.getParentEventCode(), effectiveParent)
                && Objects.equals(actualCauses, expectedCauses)
                && Objects.equals(existing.getCorrelationId(), effectiveCorrelation)
                && existing.getEventType() == command.getEventType()
                && existing.getStage() == command.getStage()
                && Objects.equals(existing.getNodeId(), command.getNodeId())
                && Objects.equals(existing.getCapability(), command.getCapability())
                && Objects.equals(existing.getToolName(), command.getToolName())
                && existing.getStatus() == command.getStatus()
                && Objects.equals(existing.getReasonCode(), command.getReasonCode())
                && Objects.equals(existing.getSummary(), command.getSummary())
                && Objects.equals(
                security.canonicalizePayload(existing.getPayloadJson()),
                security.canonicalizePayload(command.getPayloadJson()))
                && existing.getPayloadSchemaVersion() == normalizedSchemaVersion(command.getPayloadSchemaVersion())
                && Objects.equals(existing.getProducerId(), command.getProducerId())
                && Objects.equals(existing.getOccurredAt(),
                converter.truncateToMicros(command.getOccurredAt()))
                && Objects.equals(existing.getCreatedBy(), command.getActor());
        if (!same) {
            throw new TraceIdempotencyConflictException(
                    "eventCode was reused with different content: " + command.getEventCode());
        }
    }

    private void assertSameEvidenceRequest(Evidence existing, AppendEvidenceCommand command) {
        boolean same = Objects.equals(existing.getEvidenceCode(), command.getEvidenceCode())
                && Objects.equals(existing.getEventCode(), command.getEventCode())
                && Objects.equals(existing.getNodeId(), command.getNodeId())
                && Objects.equals(existing.getEvidenceType(), command.getEvidenceType())
                && Objects.equals(existing.getSourceType(), command.getSourceType())
                && Objects.equals(existing.getSourceReference(), command.getSourceReference())
                && Objects.equals(existing.getClaim(), command.getClaim())
                && Objects.equals(existing.getExpectedValueRedacted(), command.getExpectedValueRedacted())
                && Objects.equals(existing.getActualValueRedacted(), command.getActualValueRedacted())
                && existing.getReliabilityLevel() == command.getReliabilityLevel()
                && existing.getVerificationStatus() == command.getVerificationStatus()
                && Objects.equals(
                security.canonicalizePayload(existing.getPayloadJson()),
                security.canonicalizePayload(command.getPayloadJson()))
                && Objects.equals(existing.getCollectedAt(),
                converter.truncateToMicros(command.getCollectedAt()))
                && Objects.equals(existing.getCreatedBy(), command.getActor());
        if (!same) {
            throw new TraceIdempotencyConflictException(
                    "evidenceCode was reused with different content: " + command.getEvidenceCode());
        }
    }

    private void validateCreateCommand(CreateEpisodeCommand command) {
        Objects.requireNonNull(command, "command");
        requireText(command.getEpisodeCode(), "episodeCode");
        requireText(command.getRequestId(), "requestId");
        requireText(command.getAgentVersion(), "agentVersion");
        requireText(command.getActor(), "actor");
        Objects.requireNonNull(command.getRiskLevel(), "riskLevel");
        Objects.requireNonNull(command.getStartedAt(), "startedAt");
    }

    private String deterministicCode(String namespace, String value) {
        return UUID.nameUUIDFromBytes((namespace + ":" + value).getBytes(StandardCharsets.UTF_8)).toString();
    }

    private String codeOf(Map<Long, TraceEventEntity> byId, Long id) {
        return id == null ? null : requiredCodeOf(byId, id);
    }

    private String requiredCodeOf(Map<Long, TraceEventEntity> byId, Long id) {
        TraceEventEntity entity = byId.get(id);
        if (entity == null) {
            throw new TracePersistenceException("Trace references a missing event id: " + id);
        }
        return entity.getEventCode();
    }

    private int normalizedSchemaVersion(int value) {
        return value > 0 ? value : 1;
    }

    private java.time.LocalDateTime toDateTime(Instant value) {
        return value == null
                ? null
                : java.time.LocalDateTime.ofInstant(
                converter.truncateToMicros(value), java.time.ZoneOffset.UTC);
    }

    private Instant toInstant(java.time.LocalDateTime value) {
        return value == null ? null : value.toInstant(java.time.ZoneOffset.UTC);
    }

    private void requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
    }
}
