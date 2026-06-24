package com.fundagent.core.trace;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TraceCanonicalizerTest {
    private final TraceCanonicalizer canonicalizer = new DefaultTraceCanonicalizer();

    @Test
    void shouldCanonicalizeEquivalentJsonToSameRepresentation() {
        String first = canonicalizer.canonicalizeJson(
                "{\"z\":[{\"b\":2,\"a\":1}],\"number\":1.00,\"flag\":true}");
        String second = canonicalizer.canonicalizeJson(
                "{ \"flag\": true, \"number\": 1.0, \"z\": [ { \"a\": 1, \"b\": 2 } ] }");

        assertThat(first).isEqualTo(second);
        assertThat(first).isEqualTo("{\"flag\":true,\"number\":1,\"z\":[{\"a\":1,\"b\":2}]}");
    }

    @Test
    void shouldRejectInvalidJson() {
        assertThatThrownBy(() -> canonicalizer.canonicalizeJson("{not-json}"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("payload");
    }
}
