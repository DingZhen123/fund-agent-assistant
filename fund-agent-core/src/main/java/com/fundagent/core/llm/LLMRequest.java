package com.fundagent.core.llm;

import com.fundagent.common.model.Message;
import com.fundagent.core.trace.TraceContext;
import lombok.Builder;
import lombok.Value;

import java.util.List;
import java.util.Map;

@Value
public class LLMRequest {
    TraceContext traceContext;
    LLMCallerType callerType;
    String callerName;
    String nodeId;
    String capability;
    String systemPrompt;
    List<Message> history;
    String currentMessage;
    LLMResponseFormat responseFormat;
    Map<String, String> metadata;

    @Builder(toBuilder = true)
    public LLMRequest(TraceContext traceContext, LLMCallerType callerType, String callerName,
                      String nodeId, String capability, String systemPrompt, List<Message> history,
                      String currentMessage, LLMResponseFormat responseFormat,
                      Map<String, String> metadata) {
        this.traceContext = traceContext;
        this.callerType = callerType;
        this.callerName = callerName;
        this.nodeId = nodeId;
        this.capability = capability;
        this.systemPrompt = systemPrompt;
        this.history = history == null ? List.of() : List.copyOf(history);
        this.currentMessage = currentMessage;
        this.responseFormat = responseFormat == null ? LLMResponseFormat.text() : responseFormat;
        this.metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }
}
