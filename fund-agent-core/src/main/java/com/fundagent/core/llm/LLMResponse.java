package com.fundagent.core.llm;

import com.fundagent.core.trace.TraceContext;
import lombok.Builder;
import lombok.Value;

@Value
@Builder(toBuilder = true)
public class LLMResponse {
    String content;
    String provider;
    String model;
    String providerRequestId;
    Integer promptTokens;
    Integer completionTokens;
    Integer totalTokens;
    String finishReason;
    long elapsedMs;
    TraceContext traceContext;
}
