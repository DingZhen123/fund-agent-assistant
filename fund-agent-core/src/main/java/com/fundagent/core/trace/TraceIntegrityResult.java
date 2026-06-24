package com.fundagent.core.trace;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class TraceIntegrityResult {
    boolean valid;
    String reasonCode;
    String message;
    Long failedSequenceNo;
    String failedEventCode;
}
