package com.fundagent.server.config;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ConversationSession {
    private String sessionId;
    private String conversationId;
    private LocalDateTime lastUpdateTime;
}
