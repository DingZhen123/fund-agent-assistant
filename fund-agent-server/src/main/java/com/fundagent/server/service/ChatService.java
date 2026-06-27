package com.fundagent.server.service;

import com.alibaba.fastjson2.JSON;
import com.fundagent.agents.dag.CapabilityDagPlanningResult;
import com.fundagent.agents.dag.CapabilityDagPlanner;
import com.fundagent.agents.dag.ReplanningDagRuntime;
import com.fundagent.core.dag.BoundDagNode;
import com.fundagent.core.dag.BoundDagPlan;
import com.fundagent.core.dag.DagExecutionContext;
import com.fundagent.core.dag.DagNode;
import com.fundagent.core.dag.DagPlan;
import com.fundagent.core.dag.DagPlanValidationResult;
import com.fundagent.core.dag.DagPlanValidator;
import com.fundagent.core.dag.DagRunResult;
import com.fundagent.core.dag.DagRunStatus;
import com.fundagent.core.dag.FinalVerificationResult;
import com.fundagent.core.dag.NodeObservation;
import com.fundagent.core.dag.ReplanningDagRunResult;
import com.fundagent.core.dag.ToolBinder;
import com.fundagent.core.dag.ToolBindingResult;
import com.fundagent.core.memory.Memory;
import com.fundagent.core.memory.Round;
import com.fundagent.core.orchestration.OrchestrationEvent;
import com.fundagent.core.orchestration.OrchestrationEventType;
import com.fundagent.core.post.Post;
import com.fundagent.core.trace.AppendTraceEventCommand;
import com.fundagent.core.trace.CreateEpisodeCommand;
import com.fundagent.core.trace.EpisodeStatus;
import com.fundagent.core.trace.RiskLevel;
import com.fundagent.core.trace.TraceAppendResult;
import com.fundagent.core.trace.TraceContext;
import com.fundagent.core.trace.TraceEventStatus;
import com.fundagent.core.trace.TraceEventType;
import com.fundagent.core.trace.TraceStage;
import com.fundagent.core.trace.TraceStore;
import com.fundagent.repo.entity.ConversationEntity;
import com.fundagent.repo.mapper.ConversationMapper;
import com.fundagent.repo.mapper.PostMapper;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

@Slf4j
@Service
public class ChatService {

    private final CapabilityDagPlanner capabilityDagPlanner;
    private final DagPlanValidator dagPlanValidator;
    private final ToolBinder toolBinder;
    private final ReplanningDagRuntime replanningDagRuntime;
    private final SessionService sessionService;
    private final MemoryService memoryService;
    private final EntityMemoryService entityMemoryService;
    private final ConversationSummaryService conversationSummaryService;
    private final ConversationMapper conversationMapper;
    private final PostMapper postMapper;
    private final TraceStore traceStore;
    private final String agentVersion;

    public ChatService(CapabilityDagPlanner capabilityDagPlanner,
                       DagPlanValidator dagPlanValidator, ToolBinder toolBinder,
                       ReplanningDagRuntime replanningDagRuntime,
                       SessionService sessionService,
                       MemoryService memoryService, EntityMemoryService entityMemoryService,
                       ConversationSummaryService conversationSummaryService,
                       ConversationMapper conversationMapper,
                       PostMapper postMapper,
                       ObjectProvider<TraceStore> traceStoreProvider,
                       @Value("${agent.harness.version:mainline-trace-v1}") String agentVersion) {
        this.capabilityDagPlanner = capabilityDagPlanner;
        this.dagPlanValidator = dagPlanValidator;
        this.toolBinder = toolBinder;
        this.replanningDagRuntime = replanningDagRuntime;
        this.sessionService = sessionService;
        this.memoryService = memoryService;
        this.entityMemoryService = entityMemoryService;
        this.conversationSummaryService = conversationSummaryService;
        this.conversationMapper = conversationMapper;
        this.postMapper = postMapper;
        this.traceStore = traceStoreProvider.getIfAvailable();
        this.agentVersion = agentVersion;
    }

    public void sendMessage(String userId, String message, String conversationId, SseEmitter emitter) {
        try {
            SessionContext ctx = prepareSession(userId, message, conversationId);
            Memory memory = ctx.isNew
                    ? memoryService.getOrCreate(ctx.conversationId)
                    : memoryService.loadFromHistory(ctx.convId);

            Consumer<OrchestrationEvent> listener = event -> {
                try {
                    emitter.send(SseEmitter.event()
                            .name(event.getType().name().toLowerCase())
                            .data(new SseEvent(event.getType().name(), event.getAgent(), event.getMessage())));
                } catch (IOException e) {
                    log.error("SSE send error", e);
                }
            };

            String finalAnswer = processDagMessage(ctx, memory, userId, message, listener);

            emitter.send(SseEmitter.event()
                    .name("message_end")
                    .data(new SseEnd(ctx.conversationId, finalAnswer)));
            emitter.complete();

        } catch (Exception e) {
            log.error("ChatService error", e);
            try {
                emitter.send(SseEmitter.event().name("error").data("系统错误: " + e.getMessage()));
                emitter.complete();
            } catch (IOException ex) {
                emitter.completeWithError(ex);
            }
        }
    }

    public String sendSync(String userId, String message) {
        try {
            SessionContext ctx = prepareSession(userId, message, null);
            Memory memory = ctx.isNew
                    ? memoryService.getOrCreate(ctx.conversationId)
                    : memoryService.loadFromHistory(ctx.convId);

            return processDagMessage(ctx, memory, userId, message, null);
        } catch (Exception e) {
            log.error("ChatService sendSync error", e);
            return "系统繁忙，请稍后重试";
        }
    }

    private SessionContext prepareSession(String userId, String message, String conversationId) {
        boolean explicitConv = (conversationId != null && !conversationId.isEmpty());
        String sessionId = sessionService.getSession(userId);
        boolean isNew;

        if (explicitConv) {
            sessionService.saveSession(userId, conversationId);
            isNew = true;
        } else if (sessionId != null) {
            conversationId = sessionId;
            sessionService.refreshSession(userId);
            isNew = false;
            log.info("使用已有会话: conversationId={}, userId={}", conversationId, userId);
        } else {
            String title = message.length() > 20 ? message.substring(0, 20) + "..." : message;
            ConversationEntity conv = new ConversationEntity();
            conv.setUserId(userId);
            conv.setTitle(title);
            conv.setStatus("active");
            conv.setMessageCount(0);
            conversationMapper.insert(conv);
            conversationId = conv.getId().toString();
            sessionService.saveSession(userId, conversationId);
            isNew = true;
        }

        Long convId = Long.parseLong(conversationId);
        return new SessionContext(conversationId, convId, isNew);
    }

    private String processDagMessage(SessionContext ctx, Memory memory, String userId, String message,
                                     Consumer<OrchestrationEvent> listener) {
        TraceContext traceContext = startEpisodeTrace(ctx, userId, message);
        try {
            emit(listener, OrchestrationEventType.ROUND_START, "DagRuntime", "开始执行DAG复杂任务...");

        traceContext = appendPlanningTrace(traceContext, TraceEventType.PLAN_REQUESTED, TraceEventStatus.STARTED,
                "开始请求能力DAG规划", Map.of(
                        "conversationId", ctx.conversationId,
                        "messageHash", sha256(message)));
        CapabilityDagPlanningResult planningResult = planWithTrace(memory, message, traceContext);
        DagPlan capabilityDag = planningResult.getPlan();
        traceContext = planningResult.getTraceContext();
        traceContext = appendPlanningTrace(traceContext, TraceEventType.PLAN_GENERATED, TraceEventStatus.SUCCEEDED,
                "能力DAG已生成", buildPlanPayload(capabilityDag));
        log.info("Capability DAG planned: {}", JSON.toJSONString(capabilityDag));
        log.info("Capability DAG selected capabilities: dagId={}, capabilities={}",
                capabilityDag != null ? capabilityDag.getDagId() : null,
                plannedCapabilities(capabilityDag));
        log.info("Capability DAG execution plan nodes: dagId={}, nodeCount={}, nodes={}",
                capabilityDag != null ? capabilityDag.getDagId() : null,
                capabilityDag != null && capabilityDag.getNodes() != null ? capabilityDag.getNodes().size() : 0,
                plannedNodes(capabilityDag));
        emit(listener, OrchestrationEventType.AGENT_END, "CapabilityDagPlanner", "能力DAG已生成");
        DagPlanValidationResult capabilityDagValidation = dagPlanValidator.validate(capabilityDag);
        log.info("Capability DAG validation: valid={}, errorCode={}, message={}",
                capabilityDagValidation.isValid(),
                capabilityDagValidation.getErrorCode(),
                capabilityDagValidation.getMessage());
        emit(listener, OrchestrationEventType.AGENT_END, "CapabilityDagValidator",
                capabilityDagValidation.isValid()
                        ? "能力DAG协议校验通过"
                        : "能力DAG协议校验失败: " + capabilityDagValidation.getMessage());
        traceContext = appendPlanningTrace(traceContext,
                capabilityDagValidation.isValid() ? TraceEventType.PLAN_VALIDATED : TraceEventType.PLAN_REJECTED,
                capabilityDagValidation.isValid() ? TraceEventStatus.SUCCEEDED : TraceEventStatus.REJECTED,
                capabilityDagValidation.isValid() ? "能力DAG协议校验通过" : "能力DAG协议校验失败",
                buildPlanValidationPayload(capabilityDagValidation));
        traceContext = appendToolBindingTrace(traceContext, TraceEventType.TOOL_BINDING_STARTED,
                TraceEventStatus.STARTED, "开始能力DAG工具绑定", buildPlanPayload(capabilityDag));
        BoundDagPlan boundCapabilityDag = toolBinder.bind(capabilityDag);
        logBoundNodeTools(boundCapabilityDag);
        ToolBindingResult capabilityDagBinding = toolBinder.validate(boundCapabilityDag);
        log.info("Capability DAG tool binding: success={}, errorCode={}, message={}",
                capabilityDagBinding.isSuccess(),
                capabilityDagBinding.getErrorCode(),
                capabilityDagBinding.getMessage());
        emit(listener, OrchestrationEventType.AGENT_END, "ToolBinder",
                capabilityDagBinding.isSuccess()
                        ? "能力DAG工具绑定完成"
                        : "能力DAG工具绑定失败: " + capabilityDagBinding.getMessage());
        traceContext = appendToolBindingTrace(traceContext,
                capabilityDagBinding.isSuccess() ? TraceEventType.TOOL_BOUND : TraceEventType.TOOL_BINDING_FAILED,
                capabilityDagBinding.isSuccess() ? TraceEventStatus.SUCCEEDED : TraceEventStatus.FAILED,
                capabilityDagBinding.isSuccess() ? "能力DAG工具绑定完成" : "能力DAG工具绑定失败",
                buildToolBindingPayload(boundCapabilityDag, capabilityDagBinding));

        ReplanningDagRunResult replanningRunResult = null;
        String finalAnswer;
        if (capabilityDagValidation.isValid() && capabilityDagBinding.isSuccess()) {
            DagExecutionContext executionContext = DagExecutionContext.builder()
                    .dagId(boundCapabilityDag.getDagId())
                    .conversationId(ctx.conversationId)
                    .userId(userId)
                    .userMessage(message)
                    .traceContext(traceContext)
                    .build();
            replanningRunResult = replanningDagRuntime.run(boundCapabilityDag, executionContext);
            traceContext = executionContext.getTraceContext();
            emit(listener, OrchestrationEventType.AGENT_END, "DagRuntime",
                    "DAG执行完成: " + replanningRunResult.getFinalResult().getStatus());
            finalAnswer = extractFinalAnswer(replanningRunResult);
            rememberDagObservations(memory, replanningRunResult.getFinalResult());
        } else {
            finalAnswer = capabilityDagValidation.isValid()
                    ? "能力DAG工具绑定失败: " + capabilityDagBinding.getMessage()
                    : "能力DAG协议校验失败: " + capabilityDagValidation.getMessage();
            emit(listener, OrchestrationEventType.ERROR, "DagRuntime", finalAnswer);
        }
        Round round = memory.newRound(message);
        Post userPost = Post.create("User", "CapabilityDagPlanner", message);
        userPost.addAttachment("capability_dag", JSON.toJSONString(capabilityDag));
        userPost.addAttachment("capability_dag_validation", JSON.toJSONString(capabilityDagValidation));
        userPost.addAttachment("bound_capability_dag", JSON.toJSONString(boundCapabilityDag));
        userPost.addAttachment("capability_dag_binding", JSON.toJSONString(capabilityDagBinding));
        round.addPost(userPost);

        Post graphPost = Post.create("DagRuntime", "User", finalAnswer);
        graphPost.addAttachment("replanning_run_result", JSON.toJSONString(replanningRunResult));
        graphPost.addAttachment("dag_success", String.valueOf(isDagSuccess(replanningRunResult)));
        graphPost.addAttachment("waiting_user_input", String.valueOf(isWaitingUserInput(replanningRunResult)));
        round.addPost(graphPost);
        round.markCompleted();

        memoryService.savePosts(ctx.convId, round.getPosts(), round.getRoundNum());
        updateConversationMessageCount(ctx, memory);
        conversationSummaryService.refreshSummaryIfNeeded(ctx.convId, memory);
        traceContext = finishEpisodeTrace(traceContext, replanningRunResult, capabilityDagValidation,
                capabilityDagBinding, finalAnswer);

        emit(listener,
                isDagSuccess(replanningRunResult) ? OrchestrationEventType.MESSAGE_END : OrchestrationEventType.ERROR,
                "DagRuntime",
                finalAnswer);
        return finalAnswer;
        } catch (RuntimeException e) {
            finishEpisodeTrace(traceContext, null, null, null, e.getClass().getSimpleName() + ": " + e.getMessage());
            throw e;
        }
    }

    private CapabilityDagPlanningResult planWithTrace(Memory memory, String message, TraceContext traceContext) {
        if (traceContext == null) {
            return new CapabilityDagPlanningResult(capabilityDagPlanner.plan(memory, message), null);
        }
        return capabilityDagPlanner.plan(memory, message, traceContext);
    }

    private TraceContext appendPlanningTrace(TraceContext traceContext, TraceEventType eventType,
                                             TraceEventStatus status, String summary,
                                             Map<String, Object> payload) {
        return appendTrace(traceContext, eventType, TraceStage.PLANNING, status, summary, payload);
    }

    private TraceContext appendToolBindingTrace(TraceContext traceContext, TraceEventType eventType,
                                                TraceEventStatus status, String summary,
                                                Map<String, Object> payload) {
        return appendTrace(traceContext, eventType, TraceStage.TOOL_BINDING, status, summary, payload);
    }

    private TraceContext appendTrace(TraceContext traceContext, TraceEventType eventType, TraceStage stage,
                                     TraceEventStatus status, String summary, Map<String, Object> payload) {
        if (traceContext == null || traceStore == null) {
            return traceContext;
        }
        TraceAppendResult result = traceStore.append(traceContext, AppendTraceEventCommand.builder()
                .eventCode(eventCode(traceContext, eventType))
                .correlationId(traceContext.getCorrelationId())
                .eventType(eventType)
                .stage(stage)
                .status(status)
                .summary(summary)
                .payloadJson(JSON.toJSONString(payload != null ? payload : Map.of()))
                .payloadSchemaVersion(1)
                .producerId("ChatService")
                .occurredAt(Instant.now())
                .actor("SYSTEM:ChatService")
                .build());
        return result.getContext();
    }

    private Map<String, Object> buildPlanPayload(DagPlan plan) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("dagId", plan != null ? safe(plan.getDagId()) : "");
        payload.put("goalHash", plan != null ? sha256(plan.getGoal()) : "");
        payload.put("nodeCount", plan != null && plan.getNodes() != null ? plan.getNodes().size() : 0);
        payload.put("capabilities", plannedCapabilities(plan));
        payload.put("nodes", plannedNodeTraceItems(plan));
        return payload;
    }

    private Map<String, Object> buildPlanValidationPayload(DagPlanValidationResult validation) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("valid", validation != null && validation.isValid());
        payload.put("errorCode", validation != null ? safe(validation.getErrorCode()) : "");
        payload.put("messageHash", validation != null ? sha256(validation.getMessage()) : "");
        return payload;
    }

    private Map<String, Object> buildToolBindingPayload(BoundDagPlan plan, ToolBindingResult binding) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("dagId", plan != null ? safe(plan.getDagId()) : "");
        payload.put("success", binding != null && binding.isSuccess());
        payload.put("errorCode", binding != null ? safe(binding.getErrorCode()) : "");
        payload.put("messageHash", binding != null ? sha256(binding.getMessage()) : "");
        payload.put("nodes", boundNodeTraceItems(plan));
        return payload;
    }

    private TraceContext startEpisodeTrace(SessionContext ctx, String userId, String message) {
        if (traceStore == null) {
            throw new IllegalStateException(
                    "主链路Trace未启用，拒绝执行。请配置 agent.trace.enabled=true 以及有效的Trace签名密钥。");
        }
        String requestId = "REQ_" + UUID.randomUUID();
        String episodeCode = "EP_" + UUID.randomUUID();
        Instant now = Instant.now();
        TraceAppendResult created = traceStore.createEpisode(CreateEpisodeCommand.builder()
                .episodeCode(episodeCode)
                .requestId(requestId)
                .conversationId(ctx.conversationId)
                .userIdReference(userId)
                .agentVersion(agentVersion)
                .originalGoalRedacted(redactGoal(message))
                .riskLevel(RiskLevel.MEDIUM)
                .startedAt(now)
                .actor("USER:" + userId)
                .build());
        return appendEpisodeLifecycle(
                created.getContext(),
                TraceEventType.EPISODE_STARTED,
                TraceEventStatus.STARTED,
                "用户主流程Episode开始执行",
                Map.of("conversationId", ctx.conversationId, "agentVersion", agentVersion));
    }

    private TraceContext finishEpisodeTrace(TraceContext traceContext, ReplanningDagRunResult runResult,
                                            DagPlanValidationResult validation,
                                            ToolBindingResult binding,
                                            String message) {
        if (traceContext == null || traceStore == null) {
            return traceContext;
        }
        TraceEventType eventType = resolveEpisodeFinishEvent(runResult, validation, binding);
        TraceEventStatus status = switch (eventType) {
            case EPISODE_COMPLETED -> TraceEventStatus.SUCCEEDED;
            case EPISODE_WAITING_USER -> TraceEventStatus.WAITING;
            case EPISODE_RESULT_UNKNOWN -> TraceEventStatus.RESULT_UNKNOWN;
            default -> TraceEventStatus.FAILED;
        };
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("runStatus", runResult != null && runResult.getFinalResult() != null
                ? safe(runResult.getFinalResult().getStatus() != null
                ? runResult.getFinalResult().getStatus().name() : null)
                : "");
        payload.put("validationValid", validation != null && validation.isValid());
        payload.put("bindingSuccess", binding != null && binding.isSuccess());
        payload.put("messageHash", sha256(message));
        return appendEpisodeLifecycle(traceContext, eventType, status,
                summaryForEpisodeFinish(eventType, message), payload);
    }

    private TraceEventType resolveEpisodeFinishEvent(ReplanningDagRunResult runResult,
                                                     DagPlanValidationResult validation,
                                                     ToolBindingResult binding) {
        if (validation != null && !validation.isValid()) {
            return TraceEventType.EPISODE_FAILED;
        }
        if (binding != null && !binding.isSuccess()) {
            return TraceEventType.EPISODE_FAILED;
        }
        if (runResult == null || runResult.getFinalResult() == null) {
            return TraceEventType.EPISODE_FAILED;
        }
        DagRunStatus status = runResult.getFinalResult().getStatus();
        if (DagRunStatus.COMPLETED.equals(status)) {
            return TraceEventType.EPISODE_COMPLETED;
        }
        if (DagRunStatus.WAITING_USER_INPUT.equals(status)) {
            return TraceEventType.EPISODE_WAITING_USER;
        }
        return TraceEventType.EPISODE_FAILED;
    }

    private TraceContext appendEpisodeLifecycle(TraceContext traceContext, TraceEventType eventType,
                                                TraceEventStatus status, String summary,
                                                Map<String, Object> payload) {
        if (traceContext == null || traceStore == null) {
            return traceContext;
        }
        TraceAppendResult result = traceStore.append(traceContext, AppendTraceEventCommand.builder()
                .eventCode(eventCode(traceContext, eventType))
                .correlationId(traceContext.getCorrelationId())
                .eventType(eventType)
                .stage(TraceStage.EPISODE)
                .status(status)
                .summary(summary)
                .payloadJson(JSON.toJSONString(payload != null ? payload : Map.of()))
                .payloadSchemaVersion(1)
                .producerId("ChatService")
                .occurredAt(Instant.now())
                .actor("SYSTEM:ChatService")
                .build());
        return result.getContext();
    }

    private String eventCode(TraceContext context, TraceEventType eventType) {
        String material = safe(context.getEpisodeCode()) + "|" + safe(context.getRequestId()) + "|" + eventType.name();
        return "EV_" + UUID.nameUUIDFromBytes(material.getBytes(StandardCharsets.UTF_8));
    }

    private String summaryForEpisodeFinish(TraceEventType eventType, String message) {
        return switch (eventType) {
            case EPISODE_COMPLETED -> "用户主流程Episode执行完成";
            case EPISODE_WAITING_USER -> "用户主流程Episode等待用户补充信息";
            case EPISODE_RESULT_UNKNOWN -> "用户主流程Episode结果未知";
            default -> "用户主流程Episode执行失败";
        };
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(safe(value).getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    private String redactGoal(String message) {
        if (message == null || message.isBlank()) {
            return "";
        }
        String redacted = message.replaceAll("\\b\\d{6,}\\b", "***");
        return redacted.length() > 500 ? redacted.substring(0, 500) + "..." : redacted;
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private List<String> plannedCapabilities(DagPlan plan) {
        if (plan == null || plan.getNodes() == null) {
            return List.of();
        }
        return plan.getNodes().stream()
                .map(DagNode::getCapability)
                .filter(capability -> capability != null && !capability.isBlank())
                .distinct()
                .toList();
    }

    private List<String> plannedNodes(DagPlan plan) {
        if (plan == null || plan.getNodes() == null) {
            return List.of();
        }
        return plan.getNodes().stream()
                .map(node -> String.format("%s[%s,%s] dependsOn=%s",
                        node.getNodeId(),
                        node.getNodeType(),
                        node.getCapability(),
                        node.getDependsOn()))
                .toList();
    }

    private List<Map<String, Object>> plannedNodeTraceItems(DagPlan plan) {
        if (plan == null || plan.getNodes() == null) {
            return List.of();
        }
        return plan.getNodes().stream()
                .map(node -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("nodeId", safe(node.getNodeId()));
                    item.put("nodeType", node.getNodeType() != null ? node.getNodeType().name() : "");
                    item.put("capability", safe(node.getCapability()));
                    item.put("dependsOn", node.getDependsOn() != null ? node.getDependsOn() : List.of());
                    item.put("expectedOutputs", node.getExpectedOutputs() != null ? node.getExpectedOutputs() : List.of());
                    item.put("instructionHash", sha256(node.getInstruction()));
                    return item;
                })
                .toList();
    }

    private List<Map<String, Object>> boundNodeTraceItems(BoundDagPlan plan) {
        if (plan == null || plan.getNodes() == null) {
            return List.of();
        }
        return plan.getNodes().stream()
                .map(node -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("nodeId", safe(node.getNodeId()));
                    item.put("nodeType", node.getNodeType() != null ? node.getNodeType().name() : "");
                    item.put("capability", safe(node.getCapability()));
                    item.put("bindingStatus", node.getBindingStatus() != null ? node.getBindingStatus().name() : "");
                    item.put("tools", boundToolNames(node));
                    return item;
                })
                .toList();
    }

    private void logBoundNodeTools(BoundDagPlan plan) {
        if (plan == null || plan.getNodes() == null) {
            log.info("Capability DAG bound tools: dagId=null, nodeCount=0");
            return;
        }
        log.info("Capability DAG bound tools: dagId={}, nodeCount={}", plan.getDagId(), plan.getNodes().size());
        plan.getNodes().forEach(node -> log.info(
                "Capability DAG node binding: dagId={}, nodeId={}, type={}, capability={}, bindingStatus={}, tools={}",
                plan.getDagId(),
                node.getNodeId(),
                node.getNodeType(),
                node.getCapability(),
                node.getBindingStatus(),
                boundToolNames(node)));
    }

    private List<String> boundToolNames(BoundDagNode node) {
        if (node == null || node.getBoundTools() == null) {
            return List.of();
        }
        return node.getBoundTools().stream()
                .map(tool -> tool.getToolName() + "(" + tool.getProviderType() + ")")
                .toList();
    }

    private void updateConversationMessageCount(SessionContext ctx, Memory memory) {
        ConversationEntity conv = conversationMapper.selectById(ctx.convId);
        if (conv != null) {
            conv.setMessageCount(memory.getAllPosts().size());
            conversationMapper.updateById(conv);
        }
    }

    private String extractFinalAnswer(ReplanningDagRunResult replanningRunResult) {
        DagRunResult finalResult = replanningRunResult != null ? replanningRunResult.getFinalResult() : null;
        if (finalResult == null) {
            return "DAG执行未产生结果";
        }
        FinalVerificationResult verification = finalResult.getFinalVerification();
        if (verification != null && verification.getFinalAnswer() != null && !verification.getFinalAnswer().isBlank()) {
            return verification.getFinalAnswer();
        }
        if (finalResult.getLastResult() != null
                && finalResult.getLastResult().getObservation() != null
                && finalResult.getLastResult().getObservation().getSummary() != null) {
            return finalResult.getLastResult().getObservation().getSummary();
        }
        if (DagRunStatus.WAITING_USER_INPUT.equals(finalResult.getStatus())) {
            return finalResult.getMessage() != null ? finalResult.getMessage() : "需要用户补充信息";
        }
        return finalResult.getMessage() != null ? finalResult.getMessage() : "DAG执行失败";
    }

    private boolean isDagSuccess(ReplanningDagRunResult replanningRunResult) {
        return replanningRunResult != null
                && replanningRunResult.getFinalResult() != null
                && DagRunStatus.COMPLETED.equals(replanningRunResult.getFinalResult().getStatus());
    }

    private boolean isWaitingUserInput(ReplanningDagRunResult replanningRunResult) {
        return replanningRunResult != null
                && replanningRunResult.getFinalResult() != null
                && DagRunStatus.WAITING_USER_INPUT.equals(replanningRunResult.getFinalResult().getStatus());
    }

    private void rememberDagObservations(Memory memory, DagRunResult result) {
        if (result == null || result.getState() == null || result.getState().getObservations() == null) {
            return;
        }
        result.getState().getObservations().values().forEach(observation -> {
            com.fundagent.core.graph.Observation legacyObservation = toLegacyObservation(observation);
            if (legacyObservation != null) {
                entityMemoryService.rememberObservation(memory, legacyObservation);
            }
        });
    }

    private com.fundagent.core.graph.Observation toLegacyObservation(NodeObservation observation) {
        if (observation == null) {
            return null;
        }
        boolean success = observation.getStatus() != null
                && ("SUCCESS".equals(observation.getStatus().name())
                || "SKIPPED".equals(observation.getStatus().name()));
        return new com.fundagent.core.graph.Observation(
                observation.getNodeId(),
                observation.getCapability(),
                success,
                observation.getOutputs(),
                observation.getError(),
                observation.getErrorCode(),
                observation.getStatus() != null ? observation.getStatus().name() : null,
                false,
                observation.getToolCalls() != null ? observation.getToolCalls().size() : 1,
                observation.getElapsedMs());
    }

    private void emit(Consumer<OrchestrationEvent> listener,
                      OrchestrationEventType type, String agent, String message) {
        if (listener != null) {
            listener.accept(new OrchestrationEvent(type, agent, message));
        }
    }

    @Data
    @AllArgsConstructor
    private static class SessionContext {
        private String conversationId;
        private Long convId;
        private boolean isNew;
    }

    public List<ConversationEntity> getConversations(String userId) {
        return conversationMapper.findByUserId(userId);
    }

    public List<com.fundagent.repo.entity.PostEntity> loadConversation(String userId, String conversationId) {
        sessionService.saveSession(userId, conversationId);
        memoryService.loadFromHistory(Long.parseLong(conversationId));
        return postMapper.findByConversationId(Long.parseLong(conversationId));
    }

    @Data
    @AllArgsConstructor
    public static class SseEvent {
        private String eventType;
        private String agent;
        private String message;
    }

    @Data
    @AllArgsConstructor
    public static class SseEnd {
        private String conversationId;
        private String answer;
    }
}
