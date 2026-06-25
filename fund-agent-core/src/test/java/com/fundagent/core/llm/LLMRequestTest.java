package com.fundagent.core.llm;

import com.fundagent.common.model.Message;
import com.fundagent.core.trace.TraceContext;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LLMRequestTest {

    @Test
    void shouldDefensivelyCopyHistory() {
        List<Message> history = new ArrayList<>();
        history.add(new Message("user", "hello"));

        LLMRequest request = LLMRequest.builder()
                .traceContext(traceContext())
                .callerType(LLMCallerType.PLANNER)
                .systemPrompt("system")
                .history(history)
                .currentMessage("current")
                .build();
        history.add(new Message("assistant", "mutated"));

        assertThat(request.getHistory()).hasSize(1);
        assertThatThrownBy(() -> request.getHistory().add(new Message("user", "x")))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    private TraceContext traceContext() {
        return TraceContext.builder()
                .episodeCode("episode-1")
                .requestId("request-1")
                .currentEventCode("event-parent")
                .correlationId("correlation-1")
                .build();
    }
}
