package com.fundagent.core.trace;

public enum EpisodeStatus {
    CREATED,
    RUNNING,
    WAITING_USER,
    WAITING_CONFIRMATION,
    COMPLETED,
    FAILED,
    ABORTED,
    RESULT_UNKNOWN;

    public boolean isTerminal() {
        return this == COMPLETED
                || this == FAILED
                || this == ABORTED;
    }

    public boolean isWaiting() {
        return this == WAITING_USER || this == WAITING_CONFIRMATION;
    }

    public boolean isSealable() {
        return isTerminal();
    }
}
