package com.fundagent.core.trace;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HmacSha256TraceSignerTest {

    @Test
    void shouldSignAndRejectModifiedContent() {
        HmacSha256TraceSigner signer = new HmacSha256TraceSigner(
                "key-v1",
                "0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8));

        TraceSignature signature = signer.sign("original");

        assertThat(signer.verify("original", signature)).isTrue();
        assertThat(signer.verify("modified", signature)).isFalse();
    }

    @Test
    void shouldRejectWeakSigningKey() {
        assertThatThrownBy(() -> new HmacSha256TraceSigner(
                "key-v1",
                "too-short".getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("32");
    }
}
