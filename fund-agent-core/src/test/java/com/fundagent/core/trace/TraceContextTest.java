package com.fundagent.core.trace;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TraceContextTest {

    @Test
    void shouldDefensivelyCopyTraceFlags() {
        TraceContext context = TraceContext.builder()
                .episodeCode("episode-1")
                .requestId("request-1")
                .traceFlags(Set.of("AUDIT", "FUND_SAFE"))
                .build();

        assertThat(context.getTraceFlags()).containsExactlyInAnyOrder("AUDIT", "FUND_SAFE");
        assertThatThrownBy(() -> context.getTraceFlags().add("MUTATED"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void shouldCreateChildContextWithoutChangingOriginalContext() {
        TraceContext original = TraceContext.builder()
                .episodeCode("episode-1")
                .requestId("request-1")
                .currentEventCode("event-1")
                .correlationId("correlation-1")
                .build();

        TraceContext child = original.childOf("event-2");

        assertThat(original.getCurrentEventCode()).isEqualTo("event-1");
        assertThat(original.getCausationEventCode()).isNull();
        assertThat(child.getCurrentEventCode()).isEqualTo("event-2");
        assertThat(child.getCausationEventCode()).isEqualTo("event-1");
        assertThat(child.getEpisodeCode()).isEqualTo(original.getEpisodeCode());
        assertThat(child.getRequestId()).isEqualTo(original.getRequestId());
    }
}
