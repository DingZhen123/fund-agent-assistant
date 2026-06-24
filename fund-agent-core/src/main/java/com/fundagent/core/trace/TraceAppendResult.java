package com.fundagent.core.trace;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class TraceAppendResult {
    TraceEvent event;
    TraceContext context;
}
