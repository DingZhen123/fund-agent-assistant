package com.fundagent.server.service;

import com.fundagent.core.memory.Memory;
import com.fundagent.core.orchestration.OrchestrationEvent;
import com.fundagent.core.orchestration.OrchestrationResult;
import com.fundagent.core.orchestration.Orchestrator;
import com.fundagent.core.post.Post;
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
    private final SessionService sessionService;
    private final MemoryService memoryService;
    private final ConversationMapper conversationMapper;
    private final PostMapper postMapper;

    public ChatService(Orchestrator orchestrator, SessionService sessionService,
                       MemoryService memoryService, ConversationMapper conversationMapper,
                       PostMapper postMapper) {
        this.orchestrator = orchestrator;
        this.sessionService = sessionService;
        this.memoryService = memoryService;
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

            OrchestrationResult result = orchestrator.processMessage(memory, message, listener, onToken);
            String finalAnswer = finishOrchestration(ctx, memory, result);

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

            OrchestrationResult result = orchestrator.processMessage(memory, message, null, null);
            return finishOrchestration(ctx, memory, result);
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

        int roundNum = memory.getCurrentRound() != null ? memory.getCurrentRound().getRoundNum() : 1;
        memoryService.savePosts(ctx.convId, result.getAllPosts(), roundNum);

        ConversationEntity conv = conversationMapper.selectById(ctx.convId);
        if (conv != null) {
            conv.setMessageCount(memory.getAllPosts().size());
            conversationMapper.updateById(conv);
        }
        return result.getFinalAnswer();
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
