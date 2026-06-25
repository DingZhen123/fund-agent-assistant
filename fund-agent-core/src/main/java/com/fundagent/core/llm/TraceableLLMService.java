package com.fundagent.core.llm;

import com.alibaba.fastjson2.JSON;
import com.fundagent.common.model.Message;
import com.fundagent.core.trace.AppendTraceEventCommand;
import com.fundagent.core.trace.TraceAppendResult;
import com.fundagent.core.trace.TraceContext;
import com.fundagent.core.trace.TraceEventStatus;
import com.fundagent.core.trace.TraceEventType;
import com.fundagent.core.trace.TraceStage;
import com.fundagent.core.trace.TraceStore;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public class TraceableLLMService implements AgentLLMService {
    private final AgentLLMService delegate;
    private final TraceStore traceStore;
    private final LLMContentHasher contentHasher;
    private final LLMCallIdGenerator callIdGenerator;
    private final Clock clock;

    public TraceableLLMService(AgentLLMService delegate, TraceStore traceStore,
                               LLMContentHasher contentHasher, LLMCallIdGenerator callIdGenerator,
                               Clock clock) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        this.traceStore = Objects.requireNonNull(traceStore, "traceStore");
        this.contentHasher = Objects.requireNonNull(contentHasher, "contentHasher");
        this.callIdGenerator = Objects.requireNonNull(callIdGenerator, "callIdGenerator");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public LLMResponse call(LLMRequest request) {
        validateRequest(request);
        String callId = requireCallId(callIdGenerator.nextId());
        Instant startedAt = clock.instant();
        TraceAppendResult started = traceStore.append(
                request.getTraceContext(),
                eventCommand(
                        callId + "-started",
                        request,
                        TraceEventType.MODEL_CALL_STARTED,
                        TraceEventStatus.STARTED,
                        "LLM调用开始",
                        startedPayload(callId, request),
                        startedAt));

        try {
            LLMResponse providerResponse = delegate.call(request);
            Instant completedAt = clock.instant();
            long elapsedMs = elapsedMillis(startedAt, completedAt);
            TraceAppendResult completed = traceStore.append(
                    started.getContext(),
                    eventCommand(
                            callId + "-completed",
                            request,
                            TraceEventType.MODEL_CALL_COMPLETED,
                            TraceEventStatus.SUCCEEDED,
                            "LLM调用完成",
                            completedPayload(providerResponse, elapsedMs),
                            completedAt));
            return providerResponse.toBuilder()
                    .elapsedMs(elapsedMs)
                    .traceContext(completed.getContext())
                    .build();
        } catch (RuntimeException providerFailure) {
            Instant failedAt = clock.instant();
            try {
                traceStore.append(
                        started.getContext(),
                        eventCommand(
                                callId + "-failed",
                                request,
                                TraceEventType.MODEL_CALL_FAILED,
                                TraceEventStatus.FAILED,
                                "LLM调用失败",
                                failedPayload(providerFailure, elapsedMillis(startedAt, failedAt)),
                                failedAt));
            } catch (RuntimeException traceFailure) {
                traceFailure.addSuppressed(providerFailure);
                throw traceFailure;
            }
            throw providerFailure;
        }
    }

    private AppendTraceEventCommand eventCommand(String eventCode, LLMRequest request,
                                                 TraceEventType eventType, TraceEventStatus status,
                                                 String summary, Map<String, Object> payload,
                                                 Instant occurredAt) {
        return AppendTraceEventCommand.builder()
                .eventCode(eventCode)
                .correlationId(request.getTraceContext().getCorrelationId())
                .eventType(eventType)
                .stage(TraceStage.MODEL_CALL)
                .nodeId(request.getNodeId())
                .capability(request.getCapability())
                .status(status)
                .summary(summary)
                .payloadJson(JSON.toJSONString(payload))
                .payloadSchemaVersion(1)
                .producerId("TraceableLLMService")
                .occurredAt(occurredAt)
                .actor("SYSTEM:" + request.getCallerName())
                .build();
    }

    private Map<String, Object> startedPayload(String callId, LLMRequest request) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("callId", callId);
        payload.put("callerType", request.getCallerType().name());
        payload.put("callerName", request.getCallerName());
        payload.put("nodeId", nullSafe(request.getNodeId()));
        payload.put("capability", nullSafe(request.getCapability()));
        payload.put("promptHash", hashPrompt(request));
        payload.put("historyCount", request.getHistory().size());
        payload.put("responseFormat", request.getResponseFormat().getType().name());
        payload.put("schemaName", nullSafe(request.getResponseFormat().getSchemaName()));
        payload.put("schemaHash", contentHasher.hash(request.getResponseFormat().getSchemaJson()));
        payload.put("metadata", request.getMetadata());
        return payload;
    }

    private Map<String, Object> completedPayload(LLMResponse response, long elapsedMs) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("provider", nullSafe(response.getProvider()));
        payload.put("model", nullSafe(response.getModel()));
        payload.put("providerRequestId", nullSafe(response.getProviderRequestId()));
        payload.put("outputHash", contentHasher.hash(response.getContent()));
        payload.put("promptTokens", number(response.getPromptTokens()));
        payload.put("completionTokens", number(response.getCompletionTokens()));
        payload.put("totalTokens", number(response.getTotalTokens()));
        payload.put("finishReason", nullSafe(response.getFinishReason()));
        payload.put("elapsedMs", elapsedMs);
        return payload;
    }

    private Map<String, Object> failedPayload(RuntimeException failure, long elapsedMs) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("errorType", failure.getClass().getSimpleName());
        payload.put("errorCode", failure instanceof LLMCallException callException
                ? callException.getErrorCode() : "LLM_CALL_FAILED");
        payload.put("retryable", failure instanceof LLMCallException callException
                && callException.isRetryable());
        payload.put("elapsedMs", elapsedMs);
        return payload;
    }

    private String hashPrompt(LLMRequest request) {
        StringBuilder material = new StringBuilder();
        material.append(nullSafe(request.getSystemPrompt())).append('\n');
        for (Message message : request.getHistory()) {
            material.append(nullSafe(message.getRole()))
                    .append(':')
                    .append(nullSafe(message.getContent()))
                    .append('\n');
        }
        material.append(nullSafe(request.getCurrentMessage()));
        return contentHasher.hash(material.toString());
    }

    private void validateRequest(LLMRequest request) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(request.getTraceContext(), "request.traceContext");
        Objects.requireNonNull(request.getCallerType(), "request.callerType");
        requireText(request.getCallerName(), "request.callerName");
        requireText(request.getTraceContext().getEpisodeCode(), "traceContext.episodeCode");
    }

    private String requireCallId(String callId) {
        requireText(callId, "callId");
        if (callId.length() > 48) {
            throw new IllegalArgumentException("callId must not exceed 48 characters");
        }
        return callId;
    }

    private void requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
    }

    private long elapsedMillis(Instant startedAt, Instant endedAt) {
        return Math.max(0, endedAt.toEpochMilli() - startedAt.toEpochMilli());
    }

    private String nullSafe(String value) {
        return value == null ? "" : value;
    }

    private int number(Integer value) {
        return value == null ? 0 : value;
    }
}
