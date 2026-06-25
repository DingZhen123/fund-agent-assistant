package com.fundagent.repo.trace;

public class TraceIdempotencyConflictException extends IllegalStateException {

    public TraceIdempotencyConflictException(String message) {
        super(message);
    }
}
