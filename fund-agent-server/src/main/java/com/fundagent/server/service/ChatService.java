package com.fundagent.server.service;

import com.alibaba.fastjson2.JSON;
import com.fundagent.agents.dag.CapabilityDagPlanner;
import com.fundagent.agents.graph.GraphTaskPlanner;
import com.fundagent.core.dag.BoundDagPlan;
import com.fundagent.core.dag.DagPlan;
import com.fundagent.core.dag.DagPlanValidationResult;
import com.fundagent.core.dag.DagPlanValidator;
import com.fundagent.core.dag.ToolBinder;
import com.fundagent.core.dag.ToolBindingResult;
import com.fundagent.core.graph.GraphResult;
import com.fundagent.core.graph.GraphOrchestrator;
import com.fundagent.core.graph.TaskPlan;
import com.fundagent.core.memory.Memory;
import com.fundagent.core.memory.Round;
import com.fundagent.core.orchestration.OrchestrationEvent;
import com.fundagent.core.orchestration.OrchestrationEventType;
import com.fundagent.core.orchestration.OrchestrationResult;
import com.fundagent.core.orchestration.Orchestrator;
import com.fundagent.core.post.Post;
import com.fundagent.core.routing.TaskMode;
import com.fundagent.core.routing.TaskRouteResult;
import com.fundagent.core.routing.TaskRouter;
import com.fundagent.core.tool.ToolDefinition;
import com.fundagent.core.tool.schema.ToolSchemaResolver;
import com.fundagent.core.tool.selection.ToolSelectionRequest;
import com.fundagent.core.tool.selection.ToolSelectionResult;
import com.fundagent.core.tool.selection.ToolSelector;
import com.fundagent.repo.entity.ConversationEntity;
import com.fundagent.repo.mapper.ConversationMapper;
import com.fundagent.repo.mapper.PostMapper;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.function.Consumer;

@Slf4j
@Service
public class ChatService {

    private final Orchestrator orchestrator;
    private final CapabilityDagPlanner capabilityDagPlanner;
    private final DagPlanValidator dagPlanValidator;
    private final ToolBinder toolBinder;
    private final GraphTaskPlanner graphTaskPlanner;
    private final GraphOrchestrator graphOrchestrator;
    private final TaskRouter taskRouter;
    private final ToolSelector toolSelector;
    private final ToolSchemaResolver toolSchemaResolver;
    private final SessionService sessionService;
    private final MemoryService memoryService;
    private final EntityMemoryService entityMemoryService;
    private final ConversationSummaryService conversationSummaryService;
    private final ConversationMapper conversationMapper;
    private final PostMapper postMapper;

    public ChatService(Orchestrator orchestrator, CapabilityDagPlanner capabilityDagPlanner,
                       DagPlanValidator dagPlanValidator, ToolBinder toolBinder, GraphTaskPlanner graphTaskPlanner,
                       GraphOrchestrator graphOrchestrator, TaskRouter taskRouter, ToolSelector toolSelector,
                       ToolSchemaResolver toolSchemaResolver,
                       SessionService sessionService,
                       MemoryService memoryService, EntityMemoryService entityMemoryService,
                       ConversationSummaryService conversationSummaryService,
                       ConversationMapper conversationMapper,
                       PostMapper postMapper) {
        this.orchestrator = orchestrator;
        this.capabilityDagPlanner = capabilityDagPlanner;
        this.dagPlanValidator = dagPlanValidator;
        this.toolBinder = toolBinder;
        this.graphTaskPlanner = graphTaskPlanner;
        this.graphOrchestrator = graphOrchestrator;
        this.taskRouter = taskRouter;
        this.toolSelector = toolSelector;
        this.toolSchemaResolver = toolSchemaResolver;
        this.sessionService = sessionService;
        this.memoryService = memoryService;
        this.entityMemoryService = entityMemoryService;
        this.conversationSummaryService = conversationSummaryService;
        this.conversationMapper = conversationMapper;
        this.postMapper = postMapper;
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

            Consumer<String> onToken = token -> {
                try {
                    emitter.send(SseEmitter.event()
                            .name("token")
                            .data(token));
                } catch (IOException e) {
                    log.error("SSE token send error", e);
                }
            };

            String finalAnswer = processRoutedMessage(ctx, memory, userId, message, listener, onToken);

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

            return processRoutedMessage(ctx, memory, userId, message, null, null);
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

    private String finishOrchestration(SessionContext ctx, Memory memory, OrchestrationResult result) {
        log.info("Orchestration done: posts.size={}, answer={}",
                result.getAllPosts() != null ? result.getAllPosts().size() : 0,
                result.getFinalAnswer());

        if (result.getAllPosts() != null) {
            result.getAllPosts().forEach(post -> entityMemoryService.rememberToolResultPost(memory, post));
        }

        int roundNum = memory.getCurrentRound() != null ? memory.getCurrentRound().getRoundNum() : 1;
        memoryService.savePosts(ctx.convId, result.getAllPosts(), roundNum);

        ConversationEntity conv = conversationMapper.selectById(ctx.convId);
        if (conv != null) {
            conv.setMessageCount(memory.getAllPosts().size());
            conversationMapper.updateById(conv);
        }
        conversationSummaryService.refreshSummaryIfNeeded(ctx.convId, memory);
        return result.getFinalAnswer();
    }

    private String processRoutedMessage(SessionContext ctx, Memory memory, String userId, String message,
                                        Consumer<OrchestrationEvent> listener,
                                        Consumer<String> onToken) {
        TaskRouteResult route = taskRouter.route(message);
        log.info("Task route: mode={}, confidence={}, reason={}, rules={}",
                route.getMode(), route.getConfidence(), route.getReason(), route.getMatchedRules());

        if (TaskMode.COMPLEX.equals(route.getMode())) {
            return processGraphMessage(ctx, memory, userId, message, route, listener);
        }

        OrchestrationResult result = orchestrator.processMessage(memory, message, listener, onToken);
        return finishOrchestration(ctx, memory, result);
    }

    private String processGraphMessage(SessionContext ctx, Memory memory, String userId, String message,
                                       TaskRouteResult route,
                                       Consumer<OrchestrationEvent> listener) {
        emit(listener, OrchestrationEventType.ROUND_START, "GraphOrchestrator", "开始执行复杂任务...");

        DagPlan capabilityDag = capabilityDagPlanner.plan(memory, message);
        log.info("Capability DAG planned: {}", JSON.toJSONString(capabilityDag));
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
        BoundDagPlan boundCapabilityDag = toolBinder.bind(capabilityDag);
        ToolBindingResult capabilityDagBinding = toolBinder.validate(boundCapabilityDag);
        log.info("Capability DAG tool binding: success={}, errorCode={}, message={}",
                capabilityDagBinding.isSuccess(),
                capabilityDagBinding.getErrorCode(),
                capabilityDagBinding.getMessage());
        emit(listener, OrchestrationEventType.AGENT_END, "ToolBinder",
                capabilityDagBinding.isSuccess()
                        ? "能力DAG工具绑定完成"
                        : "能力DAG工具绑定失败: " + capabilityDagBinding.getMessage());

        ToolSelectionResult toolSelection = selectTools(userId, ctx.conversationId, message);
        emit(listener, OrchestrationEventType.AGENT_END, "ToolSelector",
                "候选工具已筛选: " + String.join(", ", toolSelection.getCandidateToolNames()));

        List<ToolDefinition> candidateTools = toolSchemaResolver.resolve(toolSelection.getCandidateToolNames());
        log.info("Candidate tool definitions resolved: names={}",
                candidateTools.stream().map(ToolDefinition::getName).toList());

        TaskPlan taskPlan = graphTaskPlanner.plan(memory, message, candidateTools);
        emit(listener, OrchestrationEventType.AGENT_END, "GraphTaskPlanner", "复杂任务计划已生成");

        GraphResult graphResult = graphOrchestrator.execute(taskPlan, userId, ctx.conversationId, message);
        String finalAnswer = graphResult.getAnswer();

        if (graphResult.getState() != null && graphResult.getState().getObservations() != null) {
            graphResult.getState().getObservations().values()
                    .forEach(observation -> entityMemoryService.rememberObservation(memory, observation));
        }

        Round round = memory.newRound(message);
        Post userPost = Post.create("User", "GraphTaskPlanner", message);
        userPost.addAttachment("task_route", JSON.toJSONString(route));
        userPost.addAttachment("tool_selection", JSON.toJSONString(toolSelection));
        userPost.addAttachment("capability_dag", JSON.toJSONString(capabilityDag));
        userPost.addAttachment("capability_dag_validation", JSON.toJSONString(capabilityDagValidation));
        userPost.addAttachment("bound_capability_dag", JSON.toJSONString(boundCapabilityDag));
        userPost.addAttachment("capability_dag_binding", JSON.toJSONString(capabilityDagBinding));
        round.addPost(userPost);

        Post graphPost = Post.create("GraphOrchestrator", "User", finalAnswer);
        graphPost.addAttachment("task_plan", JSON.toJSONString(taskPlan));
        graphPost.addAttachment("candidate_tools",
                JSON.toJSONString(candidateTools.stream().map(ToolDefinition::getName).toList()));
        graphPost.addAttachment("graph_success", String.valueOf(graphResult.isSuccess()));
        graphPost.addAttachment("waiting_user_input", String.valueOf(graphResult.isWaitingUserInput()));
        round.addPost(graphPost);
        round.markCompleted();

        memoryService.savePosts(ctx.convId, round.getPosts(), round.getRoundNum());
        updateConversationMessageCount(ctx, memory);
        conversationSummaryService.refreshSummaryIfNeeded(ctx.convId, memory);

        emit(listener,
                graphResult.isSuccess() ? OrchestrationEventType.MESSAGE_END : OrchestrationEventType.ERROR,
                "GraphOrchestrator",
                finalAnswer);
        return finalAnswer;
    }

    private ToolSelectionResult selectTools(String userId, String conversationId, String message) {
        ToolSelectionResult selection = toolSelector.select(ToolSelectionRequest.builder()
                .userMessage(message)
                .userId(userId)
                .conversationId(conversationId)
                .maxCandidates(10)
                .build());
        log.info("Tool selection: domain={}, intents={}, candidates={}, confidence={}, rules={}",
                selection.getDomain(),
                selection.getIntents(),
                selection.getCandidateToolNames(),
                selection.getConfidence(),
                selection.getMatchedRules());
        return selection;
    }

    private void updateConversationMessageCount(SessionContext ctx, Memory memory) {
        ConversationEntity conv = conversationMapper.selectById(ctx.convId);
        if (conv != null) {
            conv.setMessageCount(memory.getAllPosts().size());
            conversationMapper.updateById(conv);
        }
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
