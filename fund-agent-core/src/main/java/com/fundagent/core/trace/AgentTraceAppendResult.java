package com.fundagent.core.trace;

import lombok.Value;

@Value
public class AgentTraceAppendResult {
    AgentTrace trace;
    TraceEvent event;
    TraceContext context;
}
