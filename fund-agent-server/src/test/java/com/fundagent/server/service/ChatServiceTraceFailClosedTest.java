package com.fundagent.server.service;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.fundagent.core.trace.AgentTrace;
import com.fundagent.core.trace.AppendEvidenceCommand;
import com.fundagent.core.trace.AppendTraceEventCommand;
import com.fundagent.core.trace.CreateEpisodeCommand;
import com.fundagent.core.trace.EpisodeSeal;
import com.fundagent.core.trace.EpisodeStatus;
import com.fundagent.core.trace.Evidence;
import com.fundagent.core.trace.TraceAppendResult;
import com.fundagent.core.trace.TraceContext;
import com.fundagent.core.trace.TraceEventStatus;
import com.fundagent.core.trace.TraceEventType;
import com.fundagent.core.trace.TraceIntegrityResult;
import com.fundagent.core.trace.TraceStage;
import com.fundagent.core.trace.TraceStore;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ChatServiceTraceFailClosedTest {

    @Test
    void mainFlowRefusesToStartWhenTraceStoreIsUnavailable() throws Exception {
        ChatService chatService = new ChatService(
                null, null, null, null, null, null, null,
                null, null, null, new EmptyTraceStoreProvider(), "test-agent");

        Class<?> sessionContextClass = Class.forName("com.fundagent.server.service.ChatService$SessionContext");
        Constructor<?> constructor = sessionContextClass.getDeclaredConstructor(String.class, Long.class, boolean.class);
        constructor.setAccessible(true);
        Object sessionContext = constructor.newInstance("conv-1", 1L, true);

        Method startEpisodeTrace = ChatService.class.getDeclaredMethod(
                "startEpisodeTrace", sessionContextClass, String.class, String.class);
        startEpisodeTrace.setAccessible(true);

        assertThatThrownBy(() -> invoke(startEpisodeTrace, chatService, sessionContext))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("主链路Trace未启用");
    }

    @Test
    void recordsPlannerSemanticTraceEvents() throws Exception {
        RecordingTraceStore traceStore = new RecordingTraceStore();
        ChatService chatService = new ChatService(
                null, null, null, null, null, null, null,
                null, null, null, new SingleTraceStoreProvider(traceStore), "test-agent");
        TraceContext context = TraceContext.builder()
                .episodeCode("EP_TEST")
                .requestId("REQ_TEST")
                .correlationId("REQ_TEST")
                .currentEventCode("EV_EPISODE_STARTED")
                .build();

        Method appendPlanningTrace = ChatService.class.getDeclaredMethod(
                "appendPlanningTrace",
                TraceContext.class,
                TraceEventType.class,
                TraceEventStatus.class,
                String.class,
                Map.class);
        appendPlanningTrace.setAccessible(true);
        appendPlanningTrace.invoke(chatService,
                context,
                TraceEventType.PLAN_GENERATED,
                TraceEventStatus.SUCCEEDED,
                "能力DAG已生成",
                Map.of("nodes", List.of(Map.of("nodeId", "n1", "capability", "finance.payment.query"))));

        assertThat(traceStore.commands).hasSize(1);
        AppendTraceEventCommand command = traceStore.commands.get(0);
        assertThat(command.getEventType()).isEqualTo(TraceEventType.PLAN_GENERATED);
        assertThat(command.getStage()).isEqualTo(TraceStage.PLANNING);
        JSONObject payload = JSON.parseObject(command.getPayloadJson());
        assertThat(payload.getJSONArray("nodes").getJSONObject(0).getString("nodeId")).isEqualTo("n1");
        assertThat(payload.getJSONArray("nodes").getJSONObject(0).getString("capability"))
                .isEqualTo("finance.payment.query");
    }

    private void invoke(Method method, Object target, Object argument) {
        try {
            method.invoke(target, argument, "user-1", "查一下 EC2025 的付款状态");
        } catch (InvocationTargetException e) {
            if (e.getCause() instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new RuntimeException(e.getCause());
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    private static class EmptyTraceStoreProvider implements ObjectProvider<TraceStore> {
        @Override
        public TraceStore getObject(Object... args) {
            throw new IllegalStateException("TraceStore unavailable");
        }

        @Override
        public TraceStore getIfAvailable() {
            return null;
        }

        @Override
        public TraceStore getIfUnique() {
            return null;
        }

        @Override
        public TraceStore getObject() {
            throw new IllegalStateException("TraceStore unavailable");
        }

        @Override
        public Iterator<TraceStore> iterator() {
            return Stream.<TraceStore>empty().iterator();
        }

        @Override
        public Stream<TraceStore> stream() {
            return Stream.empty();
        }

        @Override
        public Stream<TraceStore> orderedStream() {
            return Stream.empty();
        }
    }

    private static class SingleTraceStoreProvider implements ObjectProvider<TraceStore> {
        private final TraceStore traceStore;

        private SingleTraceStoreProvider(TraceStore traceStore) {
            this.traceStore = traceStore;
        }

        @Override
        public TraceStore getObject(Object... args) {
            return traceStore;
        }

        @Override
        public TraceStore getIfAvailable() {
            return traceStore;
        }

        @Override
        public TraceStore getIfUnique() {
            return traceStore;
        }

        @Override
        public TraceStore getObject() {
            return traceStore;
        }

        @Override
        public Iterator<TraceStore> iterator() {
            return Stream.of(traceStore).iterator();
        }

        @Override
        public Stream<TraceStore> stream() {
            return Stream.of(traceStore);
        }

        @Override
        public Stream<TraceStore> orderedStream() {
            return Stream.of(traceStore);
        }
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
            return TraceAppendResult.builder()
                    .context(context.childOf(command.getEventCode()))
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
        public AgentTrace loadTrace(String episodeCode) {
            throw new UnsupportedOperationException();
        }

        @Override
        public TraceIntegrityResult verifyIntegrity(String episodeCode) {
            throw new UnsupportedOperationException();
        }
    }
}
