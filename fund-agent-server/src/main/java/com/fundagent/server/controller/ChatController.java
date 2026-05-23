package com.fundagent.server.controller;

import com.alibaba.fastjson2.JSONObject;
import com.fundagent.repo.entity.ConversationEntity;
import com.fundagent.repo.entity.PostEntity;
import com.fundagent.server.service.ChatService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/chat")
@CrossOrigin("*")
public class ChatController {

    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    @PostMapping(path = "/send", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter sendMessage(@RequestBody JSONObject request) {
        String userId = request.getString("userId");
        String message = request.getString("message");
        String conversationId = request.getString("conversationId");
        log.info("send: userId={}, conversationId={}, message={}", userId, conversationId, message);

        SseEmitter emitter = new SseEmitter(120000L);
        chatService.sendMessage(userId, message,
                "".equals(conversationId) ? null : conversationId, emitter);
        return emitter;
    }

    @GetMapping("/conversations/{userId}")
    public List<ConversationEntity> getConversations(@PathVariable String userId) {
        log.info("getConversations: userId={}", userId);
        return chatService.getConversations(userId);
    }

    @PostMapping("/select-conversation")
    public List<PostEntity> selectConversation(@RequestBody JSONObject request) {
        String userId = request.getString("userId");
        String conversationId = request.getString("conversationId");
        log.info("selectConversation: userId={}, conversationId={}", userId, conversationId);
        return chatService.loadConversation(userId, conversationId);
    }
}
