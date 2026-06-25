package com.fundagent.repo.trace;

import com.fundagent.core.trace.AppendTraceEventCommand;
import com.fundagent.core.trace.CreateEpisodeCommand;
import com.fundagent.core.trace.RiskLevel;
import com.fundagent.core.trace.TraceAppendResult;
import com.fundagent.core.trace.TraceContext;
import com.fundagent.core.trace.TraceEventStatus;
import com.fundagent.core.trace.TraceEventType;
import com.fundagent.core.trace.TraceIntegrityResult;
import com.fundagent.core.trace.TraceStage;
import com.fundagent.core.trace.TraceStore;
import com.fundagent.repo.mapper.TraceEventRelationMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;

/**
 * Real MySQL integration tests for {@link PersistentTraceStore}.
 *
 * <p>These tests are disabled unless TRACE_MYSQL_IT=true. The target database must already
 * contain the five Trace tables created by docs/requirements/sql/full-execution-trace.sql.</p>
 */
@SpringBootTest(classes = PersistentTraceStoreMySqlIntegrationTest.TestApplication.class)
@EnabledIfEnvironmentVariable(named = "TRACE_MYSQL_IT", matches = "(?i)true")
class PersistentTraceStoreMySqlIntegrationTest {
    private static final String TEST_SIGNING_KEY = Base64.getEncoder().encodeToString(
            "trace-mysql-integration-key-32bytes".getBytes(StandardCharsets.UTF_8));

    @DynamicPropertySource
    static void mysqlProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",
                () -> requiredEnvironment("TRACE_IT_DB_URL"));
        registry.add("spring.datasource.username",
                () -> requiredEnvironment("TRACE_IT_DB_USERNAME"));
        registry.add("spring.datasource.password",
                () -> requiredEnvironment("TRACE_IT_DB_PASSWORD"));
        registry.add("spring.datasource.driver-class-name",
                () -> "com.mysql.cj.jdbc.Driver");
        registry.add("mybatis-plus.mapper-locations",
                () -> "classpath*:mapper/*.xml");
        registry.add("agent.trace.enabled", () -> "true");
        registry.add("agent.trace.signing-key-id", () -> "mysql-it-key-v1");
        registry.add("agent.trace.signing-key-base64", () -> TEST_SIGNING_KEY);
    }

    @Autowired
    private TraceStore traceStore;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private OuterTransactionProbe outerTransactionProbe;

    @SpyBean
    private TraceEventRelationMapper relationMapper;

    private final List<String> createdEpisodeCodes = new ArrayList<>();

    @AfterEach
    void cleanTraceRows() {
        reset(relationMapper);
        for (String episodeCode : createdEpisodeCodes) {
            Long episodeId = jdbcTemplate.query(
                    "SELECT id FROM agent_episodes WHERE episode_code = ?",
                    resultSet -> resultSet.next() ? resultSet.getLong(1) : null,
                    episodeCode);
            if (episodeId == null) {
                continue;
            }
            jdbcTemplate.update("DELETE FROM episode_seals WHERE episode_id = ?", episodeId);
            jdbcTemplate.update("DELETE FROM trace_evidence WHERE episode_id = ?", episodeId);
            jdbcTemplate.update("DELETE FROM trace_event_relations WHERE episode_id = ?", episodeId);
            List<Long> eventIds = jdbcTemplate.queryForList(
                    """
                    SELECT id FROM trace_events
                    WHERE episode_id = ?
                    ORDER BY sequence_no DESC
                    """,
                    Long.class,
                    episodeId);
            for (Long eventId : eventIds) {
                jdbcTemplate.update("DELETE FROM trace_events WHERE id = ?", eventId);
            }
            jdbcTemplate.update("DELETE FROM agent_episodes WHERE id = ?", episodeId);
        }
        createdEpisodeCodes.clear();
    }

    @Test
    void shouldCreateEpisodeAndInitialEventExactlyOnce() {
        Fixture fixture = fixture("create-idempotent");

        TraceAppendResult first = traceStore.createEpisode(fixture.createCommand());
        TraceAppendResult repeated = traceStore.createEpisode(fixture.createCommand());

        assertThat(repeated.getEpisode().getEpisodeCode()).isEqualTo(first.getEpisode().getEpisodeCode());
        assertThat(repeated.getEvent().getEventCode()).isEqualTo(first.getEvent().getEventCode());
        assertThat(count("agent_episodes", fixture.episodeCode())).isEqualTo(1);
        assertThat(countEvents(fixture.episodeCode())).isEqualTo(1);
        assertThat(traceStore.verifyIntegrity(fixture.episodeCode()).isValid()).isTrue();
    }

    @Test
    void shouldRejectSameIdempotencyKeyWithDifferentContent() {
        Fixture fixture = fixture("create-conflict");
        traceStore.createEpisode(fixture.createCommand());

        CreateEpisodeCommand conflicting = CreateEpisodeCommand.builder()
                .episodeCode(fixture.episodeCode())
                .requestId(fixture.requestId())
                .conversationId("different-conversation")
                .userIdReference("user-hash")
                .agentVersion("agent-v1")
                .originalGoalRedacted("不同目标")
                .riskLevel(RiskLevel.HIGH)
                .startedAt(fixture.startedAt())
                .actor("SYSTEM:MySqlIT")
                .build();

        assertThatThrownBy(() -> traceStore.createEpisode(conflicting))
                .isInstanceOf(TraceIdempotencyConflictException.class);
        assertThat(count("agent_episodes", fixture.episodeCode())).isEqualTo(1);
        assertThat(countEvents(fixture.episodeCode())).isEqualTo(1);
    }

    @Test
    void shouldMakeEventAppendIdempotentAndRejectChangedPayload() {
        Fixture fixture = fixture("event-idempotent");
        TraceAppendResult created = traceStore.createEpisode(fixture.createCommand());
        TraceAppendResult started = traceStore.append(
                created.getContext(),
                fixture.startedCommand("event-start"));

        TraceAppendResult first = traceStore.append(
                started.getContext(),
                fixture.contextCommand("event-context", "{\"value\":1}"));
        TraceAppendResult repeated = traceStore.append(
                started.getContext(),
                fixture.contextCommand("event-context", "{\"value\":1}"));

        assertThat(repeated.getEvent().getEventCode()).isEqualTo(first.getEvent().getEventCode());
        assertThat(countEvents(fixture.episodeCode())).isEqualTo(3);

        assertThatThrownBy(() -> traceStore.append(
                started.getContext(),
                fixture.contextCommand("event-context", "{\"value\":2}")))
                .isInstanceOf(TraceIdempotencyConflictException.class);
        assertThat(countEvents(fixture.episodeCode())).isEqualTo(3);
        assertThat(traceStore.verifyIntegrity(fixture.episodeCode()).isValid()).isTrue();
    }

    @Test
    void shouldSerializeConcurrentAppendsWithoutSequenceOrHashFork() throws Exception {
        Fixture fixture = fixture("concurrent-append");
        TraceAppendResult created = traceStore.createEpisode(fixture.createCommand());
        TraceAppendResult started = traceStore.append(
                created.getContext(),
                fixture.startedCommand("event-start"));
        TraceContext sharedParent = started.getContext();

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch fire = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<TraceAppendResult> first = executor.submit(() -> {
                ready.countDown();
                fire.await(10, TimeUnit.SECONDS);
                return traceStore.append(
                        sharedParent,
                        fixture.contextCommand("event-concurrent-a", "{\"worker\":\"a\"}"));
            });
            Future<TraceAppendResult> second = executor.submit(() -> {
                ready.countDown();
                fire.await(10, TimeUnit.SECONDS);
                return traceStore.append(
                        sharedParent,
                        fixture.contextCommand("event-concurrent-b", "{\"worker\":\"b\"}"));
            });

            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            fire.countDown();
            first.get(20, TimeUnit.SECONDS);
            second.get(20, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
        }

        List<Long> sequences = jdbcTemplate.queryForList(
                """
                SELECT te.sequence_no
                FROM trace_events te
                JOIN agent_episodes ae ON ae.id = te.episode_id
                WHERE ae.episode_code = ?
                ORDER BY te.sequence_no
                """,
                Long.class,
                fixture.episodeCode());
        assertThat(sequences).containsExactly(1L, 2L, 3L, 4L);

        TraceIntegrityResult integrity = traceStore.verifyIntegrity(fixture.episodeCode());
        assertThat(integrity.isValid())
                .withFailMessage("%s: %s", integrity.getReasonCode(), integrity.getMessage())
                .isTrue();
    }

    @Test
    void shouldKeepRequiresNewTraceWhenOuterBusinessTransactionRollsBack() {
        Fixture fixture = fixture("outer-rollback");
        TraceAppendResult created = traceStore.createEpisode(fixture.createCommand());
        TraceAppendResult started = traceStore.append(
                created.getContext(),
                fixture.startedCommand("event-start"));

        assertThatThrownBy(() -> outerTransactionProbe.appendThenRollback(
                started.getContext(),
                fixture.contextCommand("event-survives-outer-rollback", "{\"value\":1}")))
                .isInstanceOf(OuterBusinessRollbackException.class);

        assertThat(countEventCode(fixture.uniqueEventCode("event-survives-outer-rollback"))).isEqualTo(1);
        assertThat(traceStore.verifyIntegrity(fixture.episodeCode()).isValid()).isTrue();
    }

    @Test
    void shouldRollbackEventWhenRelationPersistenceFailsInsideTraceTransaction() {
        Fixture fixture = fixture("inner-rollback");
        TraceAppendResult created = traceStore.createEpisode(fixture.createCommand());
        TraceAppendResult started = traceStore.append(
                created.getContext(),
                fixture.startedCommand("event-start"));
        long eventsBefore = countEvents(fixture.episodeCode());
        long rowVersionBefore = episodeRowVersion(fixture.episodeCode());

        doThrow(new IllegalStateException("injected relation failure"))
                .when(relationMapper).insert(any());

        assertThatThrownBy(() -> traceStore.append(
                started.getContext(),
                fixture.contextCommand("event-must-rollback", "{\"value\":1}")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("injected relation failure");

        reset(relationMapper);
        assertThat(countEventCode(fixture.uniqueEventCode("event-must-rollback"))).isZero();
        assertThat(countEvents(fixture.episodeCode())).isEqualTo(eventsBefore);
        assertThat(episodeRowVersion(fixture.episodeCode())).isEqualTo(rowVersionBefore);
        assertThat(traceStore.verifyIntegrity(fixture.episodeCode()).isValid()).isTrue();
    }

    private Fixture fixture(String scenario) {
        String suffix = UUID.randomUUID().toString().replace("-", "");
        String episodeCode = "IT_" + scenario + "_" + suffix;
        createdEpisodeCodes.add(episodeCode);
        return new Fixture(
                episodeCode,
                "IT_REQ_" + scenario + "_" + suffix,
                Instant.now().minusSeconds(1));
    }

    private long count(String table, String episodeCode) {
        if (!Set.of("agent_episodes").contains(table)) {
            throw new IllegalArgumentException("Unsupported table: " + table);
        }
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM agent_episodes WHERE episode_code = ?",
                Long.class,
                episodeCode);
    }

    private long countEvents(String episodeCode) {
        return jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM trace_events te
                JOIN agent_episodes ae ON ae.id = te.episode_id
                WHERE ae.episode_code = ?
                """,
                Long.class,
                episodeCode);
    }

    private long countEventCode(String eventCode) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM trace_events WHERE event_code = ?",
                Long.class,
                eventCode);
    }

    private long episodeRowVersion(String episodeCode) {
        return jdbcTemplate.queryForObject(
                "SELECT row_version FROM agent_episodes WHERE episode_code = ?",
                Long.class,
                episodeCode);
    }

    private static String requiredEnvironment(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " must be set when TRACE_MYSQL_IT=true");
        }
        return value;
    }

    private record Fixture(String episodeCode, String requestId, Instant startedAt) {

        CreateEpisodeCommand createCommand() {
            return CreateEpisodeCommand.builder()
                    .episodeCode(episodeCode)
                    .requestId(requestId)
                    .conversationId("IT_CONVERSATION")
                    .userIdReference("IT_USER_HASH")
                    .agentVersion("agent-mysql-it-v1")
                    .originalGoalRedacted("MySQL Trace integration test")
                    .riskLevel(RiskLevel.HIGH)
                    .startedAt(startedAt)
                    .actor("SYSTEM:MySqlIT")
                    .build();
        }

        AppendTraceEventCommand startedCommand(String eventCode) {
            return AppendTraceEventCommand.builder()
                    .eventCode(uniqueEventCode(eventCode))
                    .eventType(TraceEventType.EPISODE_STARTED)
                    .stage(TraceStage.EPISODE)
                    .status(TraceEventStatus.STARTED)
                    .summary("Episode started")
                    .payloadJson("{}")
                    .payloadSchemaVersion(1)
                    .producerId("PersistentTraceStoreMySqlIntegrationTest")
                    .occurredAt(startedAt.plusSeconds(1))
                    .actor("SYSTEM:MySqlIT")
                    .build();
        }

        AppendTraceEventCommand contextCommand(String eventCode, String payload) {
            return AppendTraceEventCommand.builder()
                    .eventCode(uniqueEventCode(eventCode))
                    .eventType(TraceEventType.CONTEXT_ASSEMBLED)
                    .stage(TraceStage.CONTEXT)
                    .status(TraceEventStatus.SUCCEEDED)
                    .summary("Context assembled")
                    .payloadJson(payload)
                    .payloadSchemaVersion(1)
                    .producerId("PersistentTraceStoreMySqlIntegrationTest")
                    .occurredAt(startedAt.plusSeconds(2))
                    .actor("SYSTEM:MySqlIT")
                    .build();
        }

        private String uniqueEventCode(String value) {
            return UUID.nameUUIDFromBytes(
                    (episodeCode + ":" + value).getBytes(StandardCharsets.UTF_8)).toString();
        }
    }

    public static class OuterBusinessRollbackException extends RuntimeException {
        OuterBusinessRollbackException() {
            super("injected outer business rollback");
        }
    }

    public static class OuterTransactionProbe {
        private final TraceStore traceStore;

        OuterTransactionProbe(TraceStore traceStore) {
            this.traceStore = traceStore;
        }

        @Transactional
        public void appendThenRollback(TraceContext context, AppendTraceEventCommand command) {
            traceStore.append(context, command);
            throw new OuterBusinessRollbackException();
        }
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @MapperScan("com.fundagent.repo.mapper")
    @Import(TracePersistenceConfiguration.class)
    public static class TestApplication {
        @Bean
        OuterTransactionProbe outerTransactionProbe(TraceStore traceStore) {
            return new OuterTransactionProbe(traceStore);
        }
    }
}
