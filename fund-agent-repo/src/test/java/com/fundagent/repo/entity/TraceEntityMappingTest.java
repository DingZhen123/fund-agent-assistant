package com.fundagent.repo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TraceEntityMappingTest {

    @Test
    void shouldMapTraceEntitiesToApprovedTablesWithAutoIncrementIds() throws Exception {
        assertEntityMapping(AgentEpisodeEntity.class, "agent_episodes");
        assertEntityMapping(TraceEventEntity.class, "trace_events");
        assertEntityMapping(TraceEventRelationEntity.class, "trace_event_relations");
        assertEntityMapping(TraceEvidenceEntity.class, "trace_evidence");
        assertEntityMapping(EpisodeSealEntity.class, "episode_seals");
    }

    @Test
    void shouldExposeStandardAuditFieldsOnEveryTraceEntity() throws Exception {
        for (Class<?> entityType : traceEntityTypes()) {
            assertThat(entityType.getDeclaredField("createdAt").getType()).isEqualTo(LocalDateTime.class);
            assertThat(entityType.getDeclaredField("updatedAt").getType()).isEqualTo(LocalDateTime.class);
            assertThat(entityType.getDeclaredField("createdBy").getType()).isEqualTo(String.class);
            assertThat(entityType.getDeclaredField("updatedBy").getType()).isEqualTo(String.class);
        }
    }

    private void assertEntityMapping(Class<?> entityType, String tableName) throws Exception {
        TableName tableNameAnnotation = entityType.getAnnotation(TableName.class);
        assertThat(tableNameAnnotation).isNotNull();
        assertThat(tableNameAnnotation.value()).isEqualTo(tableName);

        Field idField = entityType.getDeclaredField("id");
        assertThat(idField.getType()).isEqualTo(Long.class);
        TableId tableId = idField.getAnnotation(TableId.class);
        assertThat(tableId).isNotNull();
        assertThat(tableId.type()).isEqualTo(IdType.AUTO);
    }

    private List<Class<?>> traceEntityTypes() {
        return List.of(
                AgentEpisodeEntity.class,
                TraceEventEntity.class,
                TraceEventRelationEntity.class,
                TraceEvidenceEntity.class,
                EpisodeSealEntity.class);
    }
}
