package com.fundagent.core.trace;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EpisodeStatusTest {

    @Test
    void shouldClassifyTerminalAndWaitingStatuses() {
        assertThat(EpisodeStatus.COMPLETED.isTerminal()).isTrue();
        assertThat(EpisodeStatus.FAILED.isTerminal()).isTrue();
        assertThat(EpisodeStatus.ABORTED.isTerminal()).isTrue();
        assertThat(EpisodeStatus.RESULT_UNKNOWN.isTerminal()).isFalse();

        assertThat(EpisodeStatus.CREATED.isTerminal()).isFalse();
        assertThat(EpisodeStatus.RUNNING.isTerminal()).isFalse();
        assertThat(EpisodeStatus.WAITING_USER.isTerminal()).isFalse();
        assertThat(EpisodeStatus.WAITING_CONFIRMATION.isTerminal()).isFalse();

        assertThat(EpisodeStatus.WAITING_USER.isWaiting()).isTrue();
        assertThat(EpisodeStatus.WAITING_CONFIRMATION.isWaiting()).isTrue();
        assertThat(EpisodeStatus.RUNNING.isWaiting()).isFalse();
    }
}
