package com.fundagent.repo.trace;

import com.alibaba.fastjson2.JSON;
import com.fundagent.core.trace.AgentEpisode;
import com.fundagent.core.trace.AppendTraceEventCommand;
import com.fundagent.core.trace.CreateEpisodeCommand;
import com.fundagent.core.trace.DefaultTraceCanonicalizer;
import com.fundagent.core.trace.EpisodeStatus;
import com.fundagent.core.trace.HmacSha256TraceSigner;
import com.fundagent.core.trace.RiskLevel;
import com.fundagent.core.trace.Sha256TraceHasher;
import com.fundagent.core.trace.TraceAppendResult;
import com.fundagent.core.trace.TraceContext;
import com.fundagent.core.trace.TraceEvent;
import com.fundagent.core.trace.TraceEventStatus;
import com.fundagent.core.trace.TraceEventType;
import com.fundagent.core.trace.TraceSecurity;
import com.fundagent.core.trace.TraceStage;
import com.fundagent.repo.entity.AgentEpisodeEntity;
import com.fundagent.repo.entity.TraceEventEntity;
import com.fundagent.repo.mapper.AgentEpisodeMapper;
import com.fundagent.repo.mapper.EpisodeSealMapper;
import com.fundagent.repo.mapper.TraceEventMapper;
import com.fundagent.repo.mapper.TraceEventRelationMapper;
import com.fundagent.repo.mapper.TraceEvidenceMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PersistentTraceStoreTest {
    private static final Instant NOW = Instant.parse("2026-06-24T03:00:00Z");

    private AgentEpisodeMapper episodeMapper;
    private TraceEventMapper eventMapper;
    private TraceEventRelationMapper relationMapper;
    private TraceEvidenceMapper evidenceMapper;
    private EpisodeSealMapper sealMapper;
    private TraceEntityConverter converter;
    private TraceSecurity security;
    private PersistentTraceStore store;

    @BeforeEach
    void setUp() {
        episodeMapper = mock(AgentEpisodeMapper.class);
        eventMapper = mock(TraceEventMapper.class);
        relationMapper = mock(TraceEventRelationMapper.class);
        evidenceMapper = mock(TraceEvidenceMapper.class);
        sealMapper = mock(EpisodeSealMapper.class);
        converter = new TraceEntityConverter();
        security = new TraceSecurity(
                new DefaultTraceCanonicalizer(),
                new Sha256TraceHasher(),
                new HmacSha256TraceSigner(
                        "key-v1",
                        "0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8)));
        store = new PersistentTraceStore(
                episodeMapper,
                eventMapper,
                relationMapper,
                evidenceMapper,
                sealMapper,
                converter,
                security,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void shouldCreateEpisodeAndCreatedEventAtomically() {
        CreateEpisodeCommand command = createCommand();
        AgentEpisodeEntity inserted = initialEntity();
        AgentEpisodeEntity locked = initialEntity();
        locked.setId(10L);

        when(episodeMapper.insertIdempotent(any())).thenAnswer(invocation -> {
            AgentEpisodeEntity entity = invocation.getArgument(0);
            entity.setId(10L);
            return 1;
        });
        when(episodeMapper.lockByEpisodeCode("episode-1")).thenReturn(locked);
        when(eventMapper.findByEpisodeId(10L)).thenReturn(List.of());
        when(eventMapper.insert(any())).thenAnswer(invocation -> {
            TraceEventEntity entity = invocation.getArgument(0);
            entity.setId(100L);
            return 1;
        });
        when(episodeMapper.updateProjection(
                anyLong(), anyString(), anyLong(), anyString(), anyLong(),
                anyInt(), anyInt(), anyInt(), anyLong(),
                isNull(), isNull(), isNull(), isNull(),
                anyBoolean(), anyLong(), any(), anyString(), anyLong()))
                .thenReturn(1);

        TraceAppendResult creation = store.createEpisode(command);
        AgentEpisode created = creation.getEpisode();

        assertThat(created.getStatus()).isEqualTo(EpisodeStatus.CREATED);
        assertThat(created.getEventCount()).isEqualTo(1);
        assertThat(created.getNextSequenceNo()).isEqualTo(2);
        assertThat(creation.getEvent().getEventType()).isEqualTo(TraceEventType.EPISODE_CREATED);
        assertThat(creation.getContext().getCurrentEventCode()).isEqualTo(creation.getEvent().getEventCode());
        verify(eventMapper).insert(any(TraceEventEntity.class));
        verify(episodeMapper).updateProjection(
                anyLong(), anyString(), anyLong(), anyString(), anyLong(),
                anyInt(), anyInt(), anyInt(), anyLong(),
                isNull(), isNull(), isNull(), isNull(),
                anyBoolean(), anyLong(), any(), anyString(), anyLong());
    }

    @Test
    void shouldReturnExistingEpisodeForSameCreateRequestWithoutWritingAgain() {
        AgentEpisodeEntity existing = initialEntity();
        existing.setId(10L);
        TraceEvent createdEvent = protectedCreatedEvent();
        TraceEventEntity createdEntity = converter.toEntity(createdEvent, 10L, null, null);
        createdEntity.setId(100L);
        existing.setNextSequenceNo(2L);
        existing.setEventCount(1L);
        existing.setLastEventHash(createdEvent.getEventHash());
        existing.setRowVersion(1L);
        when(episodeMapper.findByRequestId("request-1")).thenReturn(existing);
        when(episodeMapper.findByEpisodeCode("episode-1")).thenReturn(existing);
        when(episodeMapper.lockByEpisodeCode("episode-1")).thenReturn(existing);
        when(eventMapper.findByEventCode(createdEvent.getEventCode())).thenReturn(createdEntity);
        when(eventMapper.findByEpisodeId(10L)).thenReturn(List.of(createdEntity));
        when(relationMapper.findByTargetEventId(100L)).thenReturn(List.of());

        TraceAppendResult result = store.createEpisode(createCommand());

        assertThat(result.getEpisode().getEpisodeCode()).isEqualTo("episode-1");
        assertThat(result.getEvent().getEventType()).isEqualTo(TraceEventType.EPISODE_CREATED);
        verify(episodeMapper, never()).insertIdempotent(any());
        verify(eventMapper, never()).insert(any());
    }

    @Test
    void shouldCurrentReadConcurrentEpisodeAfterIdempotentUpsert() {
        AgentEpisodeEntity existing = initialEntity();
        existing.setId(10L);
        TraceEvent createdEvent = protectedCreatedEvent();
        TraceEventEntity createdEntity = converter.toEntity(createdEvent, 10L, null, null);
        createdEntity.setId(100L);
        existing.setNextSequenceNo(2L);
        existing.setEventCount(1L);
        existing.setLastEventHash(createdEvent.getEventHash());
        existing.setRowVersion(1L);

        when(episodeMapper.insertIdempotent(any())).thenReturn(0);
        when(episodeMapper.lockByRequestId("request-1")).thenReturn(existing);
        when(episodeMapper.lockByEpisodeCode("episode-1")).thenReturn(existing);
        when(eventMapper.findByEventCode(createdEvent.getEventCode())).thenReturn(createdEntity);
        when(eventMapper.findByEpisodeId(10L)).thenReturn(List.of(createdEntity));
        when(relationMapper.findByTargetEventId(100L)).thenReturn(List.of());

        TraceAppendResult result = store.createEpisode(createCommand());

        assertThat(result.getEpisode().getEventCount()).isEqualTo(1);
        verify(episodeMapper).lockByRequestId("request-1");
        verify(episodeMapper).lockByEpisodeCode("episode-1");
        verify(eventMapper, never()).insert(any());
    }

    @Test
    void shouldReturnExistingEventForIdenticalIdempotentRequest() {
        AgentEpisodeEntity locked = runningEntity();
        TraceEvent existing = protectedStartedEvent();
        TraceEventEntity eventEntity = converter.toEntity(existing, 10L, null, null);
        eventEntity.setId(100L);

        when(episodeMapper.lockByEpisodeCode("episode-1")).thenReturn(locked);
        when(eventMapper.findByEventCode("event-1")).thenReturn(eventEntity);
        when(eventMapper.findByEpisodeId(10L)).thenReturn(List.of(eventEntity));
        when(relationMapper.findByTargetEventId(100L)).thenReturn(List.of());

        TraceAppendResult result = store.append(context(null), startedCommand("EPISODE_STARTED"));

        assertThat(result.getEvent().getEventCode()).isEqualTo("event-1");
        assertThat(result.getEpisode().getEventCount()).isEqualTo(1);
        verify(eventMapper, never()).insert(any());
        verify(episodeMapper, never()).updateProjection(
                anyLong(), anyString(), anyLong(), anyString(), anyLong(),
                anyInt(), anyInt(), anyInt(), anyLong(),
                any(), any(), any(), any(),
                anyBoolean(), anyLong(), any(), anyString(), anyLong());
    }

    @Test
    void shouldRejectReusedEventCodeWithDifferentContent() {
        AgentEpisodeEntity locked = runningEntity();
        TraceEvent existing = protectedStartedEvent();
        TraceEventEntity eventEntity = converter.toEntity(existing, 10L, null, null);
        eventEntity.setId(100L);

        when(episodeMapper.lockByEpisodeCode("episode-1")).thenReturn(locked);
        when(eventMapper.findByEventCode("event-1")).thenReturn(eventEntity);
        when(eventMapper.findByEpisodeId(10L)).thenReturn(List.of(eventEntity));
        when(relationMapper.findByTargetEventId(100L)).thenReturn(List.of());

        assertThatThrownBy(() -> store.append(context(null), startedCommand("不同内容")))
                .isInstanceOf(TraceIdempotencyConflictException.class)
                .hasMessageContaining("eventCode");
        verify(eventMapper, never()).insert(any());
    }

    @Test
    void shouldPersistEventThenRelationsThenProjectionUnderEpisodeLock() {
        AgentEpisodeEntity locked = runningEntity();
        TraceEvent previous = protectedStartedEvent();
        TraceEventEntity previousEntity = converter.toEntity(previous, 10L, null, null);
        previousEntity.setId(100L);

        when(episodeMapper.lockByEpisodeCode("episode-1")).thenReturn(locked);
        when(eventMapper.findByEventCode("event-2")).thenReturn(null);
        when(eventMapper.findByEpisodeId(10L)).thenReturn(List.of(previousEntity));
        when(eventMapper.insert(any())).thenAnswer(invocation -> {
            TraceEventEntity entity = invocation.getArgument(0);
            entity.setId(101L);
            return 1;
        });
        when(relationMapper.insert(any())).thenReturn(1);
        when(episodeMapper.updateProjection(
                anyLong(), anyString(), anyLong(), anyString(), anyLong(),
                anyInt(), anyInt(), anyInt(), anyLong(),
                any(), any(), any(), any(),
                anyBoolean(), anyLong(), any(), anyString(), anyLong()))
                .thenReturn(1);

        AppendTraceEventCommand command = AppendTraceEventCommand.builder()
                .eventCode("event-2")
                .eventType(TraceEventType.CONTEXT_ASSEMBLED)
                .stage(TraceStage.CONTEXT)
                .status(TraceEventStatus.SUCCEEDED)
                .summary("上下文完成")
                .payloadJson("{}")
                .payloadSchemaVersion(1)
                .producerId("ChatService")
                .occurredAt(NOW.plusSeconds(1))
                .actor("SYSTEM:ChatService")
                .build();

        TraceAppendResult result = store.append(context("event-1"), command);

        assertThat(result.getEvent().getSequenceNo()).isEqualTo(2);
        assertThat(result.getEvent().getPreviousHash()).isEqualTo(previous.getEventHash());
        assertThat(result.getRelations()).hasSize(1);

        InOrder order = inOrder(episodeMapper, eventMapper, relationMapper);
        order.verify(episodeMapper).lockByEpisodeCode("episode-1");
        order.verify(eventMapper).findByEventCode("event-2");
        order.verify(eventMapper).findByEpisodeId(10L);
        order.verify(eventMapper).insert(any());
        order.verify(relationMapper).insert(any());
        order.verify(episodeMapper).updateProjection(
                anyLong(), anyString(), anyLong(), anyString(), anyLong(),
                anyInt(), anyInt(), anyInt(), anyLong(),
                any(), any(), any(), any(),
                anyBoolean(), anyLong(), any(), anyString(), anyLong());
    }

    @Test
    void shouldUseRequiresNewTransactionsForWrites() throws Exception {
        assertThat(PersistentTraceStore.class
                .getMethod("append", TraceContext.class, AppendTraceEventCommand.class)
                .getAnnotation(Transactional.class)
                .propagation()).isEqualTo(Propagation.REQUIRES_NEW);
        assertThat(PersistentTraceStore.class
                .getMethod("createEpisode", CreateEpisodeCommand.class)
                .getAnnotation(Transactional.class)
                .propagation()).isEqualTo(Propagation.REQUIRES_NEW);
    }

    private CreateEpisodeCommand createCommand() {
        return CreateEpisodeCommand.builder()
                .episodeCode("episode-1")
                .requestId("request-1")
                .conversationId("conversation-1")
                .userIdReference("user-hash")
                .agentVersion("agent-v1")
                .originalGoalRedacted("查询付款状态")
                .riskLevel(RiskLevel.HIGH)
                .startedAt(NOW)
                .actor("SYSTEM:ChatService")
                .build();
    }

    private AgentEpisodeEntity initialEntity() {
        AgentEpisodeEntity entity = new AgentEpisodeEntity();
        entity.setEpisodeCode("episode-1");
        entity.setRequestId("request-1");
        entity.setConversationId("conversation-1");
        entity.setUserIdReference("user-hash");
        entity.setAgentVersion("agent-v1");
        entity.setOriginalGoal("查询付款状态");
        entity.setRiskLevel("HIGH");
        entity.setStatus("CREATED");
        entity.setNextSequenceNo(1L);
        entity.setEventCount(0L);
        entity.setStepCount(0);
        entity.setModelCallCount(0);
        entity.setToolCallCount(0);
        entity.setTokenUsage(0L);
        entity.setStartedAt(java.time.LocalDateTime.ofInstant(NOW, ZoneOffset.UTC));
        entity.setSealed(false);
        entity.setRowVersion(0L);
        entity.setCreatedAt(java.time.LocalDateTime.ofInstant(NOW, ZoneOffset.UTC));
        entity.setUpdatedAt(java.time.LocalDateTime.ofInstant(NOW, ZoneOffset.UTC));
        entity.setCreatedBy("SYSTEM:ChatService");
        entity.setUpdatedBy("SYSTEM:ChatService");
        return entity;
    }

    private AgentEpisodeEntity runningEntity() {
        AgentEpisodeEntity entity = initialEntity();
        entity.setId(10L);
        TraceEvent event = protectedStartedEvent();
        entity.setStatus("RUNNING");
        entity.setNextSequenceNo(2L);
        entity.setEventCount(1L);
        entity.setLastEventHash(event.getEventHash());
        entity.setRowVersion(1L);
        return entity;
    }

    private TraceEvent protectedStartedEvent() {
        return security.protectEvent(TraceEvent.builder()
                .eventCode("event-1")
                .episodeCode("episode-1")
                .sequenceNo(1)
                .correlationId("request-1")
                .eventType(TraceEventType.EPISODE_STARTED)
                .stage(TraceStage.EPISODE)
                .status(TraceEventStatus.STARTED)
                .summary("EPISODE_STARTED")
                .payloadJson("{}")
                .payloadSchemaVersion(1)
                .producerId("TraceTest")
                .occurredAt(NOW)
                .receivedAt(NOW)
                .persistedAt(NOW)
                .previousHash(TraceSecurity.GENESIS_HASH)
                .createdBy("SYSTEM:TraceTest")
                .build());
    }

    private TraceEvent protectedCreatedEvent() {
        String eventCode = java.util.UUID.nameUUIDFromBytes(
                "episode-created:episode-1".getBytes(StandardCharsets.UTF_8)).toString();
        return security.protectEvent(TraceEvent.builder()
                .eventCode(eventCode)
                .episodeCode("episode-1")
                .sequenceNo(1)
                .correlationId("request-1")
                .eventType(TraceEventType.EPISODE_CREATED)
                .stage(TraceStage.EPISODE)
                .status(TraceEventStatus.CREATED)
                .summary("Agent Episode已创建")
                .payloadJson(JSON.toJSONString(java.util.Map.of(
                        "agentVersion", "agent-v1",
                        "riskLevel", "HIGH")))
                .payloadSchemaVersion(1)
                .producerId("PersistentTraceStore")
                .occurredAt(NOW)
                .receivedAt(NOW)
                .persistedAt(NOW)
                .previousHash(TraceSecurity.GENESIS_HASH)
                .createdBy("SYSTEM:ChatService")
                .build());
    }

    private AppendTraceEventCommand startedCommand(String summary) {
        return AppendTraceEventCommand.builder()
                .eventCode("event-1")
                .eventType(TraceEventType.EPISODE_STARTED)
                .stage(TraceStage.EPISODE)
                .status(TraceEventStatus.STARTED)
                .summary(summary)
                .payloadJson("{}")
                .payloadSchemaVersion(1)
                .producerId("TraceTest")
                .occurredAt(NOW)
                .actor("SYSTEM:TraceTest")
                .build();
    }

    private TraceContext context(String currentEventCode) {
        return TraceContext.builder()
                .episodeCode("episode-1")
                .requestId("request-1")
                .correlationId("request-1")
                .currentEventCode(currentEventCode)
                .build();
    }
}
