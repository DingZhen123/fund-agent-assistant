package com.fundagent.core.llm;

import com.fundagent.core.trace.AgentEpisode;
import com.fundagent.core.trace.AppendEvidenceCommand;
import com.fundagent.core.trace.AppendTraceEventCommand;
import com.fundagent.core.trace.CreateEpisodeCommand;
import com.fundagent.core.trace.EpisodeSeal;
import com.fundagent.core.trace.EpisodeStatus;
import com.fundagent.core.trace.Evidence;
import com.fundagent.core.trace.RiskLevel;
import com.fundagent.core.trace.TraceAppendResult;
import com.fundagent.core.trace.TraceContext;
import com.fundagent.core.trace.TraceEvent;
import com.fundagent.core.trace.TraceEventStatus;
import com.fundagent.core.trace.TraceEventType;
import com.fundagent.core.trace.TraceIntegrityResult;
import com.fundagent.core.trace.TraceStage;
import com.fundagent.core.trace.TraceStore;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TraceableLLMServiceTest {
    private static final Instant NOW = Instant.parse("2026-06-25T01:00:00Z");

    @Test
    void shouldRecordStartedAndCompletedAroundProviderCall() {
        RecordingTraceStore traceStore = new RecordingTraceStore();
        AtomicInteger providerCalls = new AtomicInteger();
        AgentLLMService provider = request -> {
            providerCalls.incrementAndGet();
            assertThat(request.getTraceContext().getCurrentEventCode()).isEqualTo("parent-event");
            return LLMResponse.builder()
                    .content("{\"answer\":\"ok\"}")
                    .provider("openai-compatible")
                    .model("gpt-test")
                    .providerRequestId("provider-request-1")
                    .promptTokens(10)
                    .completionTokens(5)
                    .totalTokens(15)
                    .finishReason("stop")
                    .build();
        };
        TraceableLLMService service = new TraceableLLMService(
                provider,
                traceStore,
                new Sha256LLMContentHasher(),
                () -> "call-1",
                Clock.fixed(NOW, ZoneOffset.UTC));

        LLMResponse response = service.call(request());

        assertThat(providerCalls).hasValue(1);
        assertThat(traceStore.commands)
                .extracting(AppendTraceEventCommand::getEventType)
                .containsExactly(TraceEventType.MODEL_CALL_STARTED, TraceEventType.MODEL_CALL_COMPLETED);
        assertThat(traceStore.commands.get(0).getPayloadJson())
                .contains("promptHash")
                .doesNotContain("secret prompt")
                .doesNotContain("sensitive current");
        assertThat(traceStore.commands.get(1).getPayloadJson())
                .contains("outputHash", "provider-request-1")
                .doesNotContain("{\\\"answer\\\":\\\"ok\\\"}");
        assertThat(response.getTraceContext().getCurrentEventCode()).isEqualTo("call-1-completed");
        assertThat(response.getElapsedMs()).isZero();
    }

    @Test
    void shouldRecordFailureAndRethrowProviderException() {
        RecordingTraceStore traceStore = new RecordingTraceStore();
        AgentLLMService provider = request -> {
            throw new LLMCallException("LLM_TIMEOUT", "timeout", true);
        };
        TraceableLLMService service = new TraceableLLMService(
                provider,
                traceStore,
                new Sha256LLMContentHasher(),
                () -> "call-2",
                Clock.fixed(NOW, ZoneOffset.UTC));

        assertThatThrownBy(() -> service.call(request()))
                .isInstanceOf(LLMCallException.class)
                .hasMessageContaining("timeout");

        assertThat(traceStore.commands)
                .extracting(AppendTraceEventCommand::getEventType)
                .containsExactly(TraceEventType.MODEL_CALL_STARTED, TraceEventType.MODEL_CALL_FAILED);
        assertThat(traceStore.commands.get(1).getPayloadJson())
                .contains("LLM_TIMEOUT", "\"retryable\":true");
    }

    @Test
    void shouldNotCallProviderWhenStartedTraceCannotPersist() {
        TraceStore failingStore = new RecordingTraceStore() {
            @Override
            public TraceAppendResult append(TraceContext context, AppendTraceEventCommand command) {
                throw new IllegalStateException("trace unavailable");
            }
        };
        AtomicInteger providerCalls = new AtomicInteger();
        AgentLLMService provider = request -> {
            providerCalls.incrementAndGet();
            return LLMResponse.builder().content("should-not-run").build();
        };
        TraceableLLMService service = new TraceableLLMService(
                provider,
                failingStore,
                new Sha256LLMContentHasher(),
                () -> "call-3",
                Clock.fixed(NOW, ZoneOffset.UTC));

        assertThatThrownBy(() -> service.call(request()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("trace unavailable");
        assertThat(providerCalls).hasValue(0);
    }

    private LLMRequest request() {
        return LLMRequest.builder()
                .traceContext(TraceContext.builder()
                        .episodeCode("episode-1")
                        .requestId("request-1")
                        .currentEventCode("parent-event")
                        .correlationId("correlation-1")
                        .build())
                .callerType(LLMCallerType.PLANNER)
                .callerName("CapabilityDagPlanner")
                .nodeId("planner")
                .capability("planning.capability_dag")
                .systemPrompt("secret prompt")
                .history(List.of())
                .currentMessage("sensitive current")
                .responseFormat(LLMResponseFormat.jsonSchema("capability_dag", "{\"type\":\"object\"}"))
                .metadata(Map.of("promptVersion", "planner-v1"))
                .build();
    }

    private static class RecordingTraceStore implements TraceStore {
        private final List<AppendTraceEventCommand> commands = new ArrayList<>();

        @Override
        public TraceAppendResult createEpisode(CreateEpisodeCommand command) {
            throw new UnsupportedOperationException();
        }

        @Override
        public TraceAppendResult append(TraceContext context, AppendTraceEventCommand command) {
            commands.add(command);
            TraceEvent event = TraceEvent.builder()
                    .eventCode(command.getEventCode())
                    .episodeCode(context.getEpisodeCode())
                    .eventType(command.getEventType())
                    .stage(command.getStage())
                    .status(command.getStatus())
                    .build();
            TraceContext next = context.childOf(command.getEventCode());
            return TraceAppendResult.builder()
                    .episode(AgentEpisode.builder()
                            .episodeCode(context.getEpisodeCode())
                            .riskLevel(RiskLevel.LOW)
                            .status(EpisodeStatus.RUNNING)
                            .build())
                    .event(event)
                    .context(next)
                    .build();
        }

        @Override
        public Evidence appendEvidence(TraceContext context, AppendEvidenceCommand command) {
            throw new UnsupportedOperationException();
        }

        @Override
        public EpisodeSeal sealEpisode(String episodeCode, EpisodeStatus finalStatus, String actor) {
            throw new UnsupportedOperationException();
        }

        @Override
        public com.fundagent.core.trace.AgentTrace loadTrace(String episodeCode) {
            throw new UnsupportedOperationException();
        }

        @Override
        public TraceIntegrityResult verifyIntegrity(String episodeCode) {
            throw new UnsupportedOperationException();
        }
    }
}
