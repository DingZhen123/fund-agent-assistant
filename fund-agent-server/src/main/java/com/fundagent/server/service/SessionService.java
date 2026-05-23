package com.fundagent.server.service;

import com.fundagent.server.config.ConversationSession;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class SessionService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final String prefix;
    private final int timeoutMinutes;

    public SessionService(RedisTemplate<String, Object> redisTemplate,
                          @Value("${agent.session.redis-prefix:agent:session:}") String prefix,
                          @Value("${agent.session.timeout-minutes:30}") int timeoutMinutes) {
        this.redisTemplate = redisTemplate;
        this.prefix = prefix;
        this.timeoutMinutes = timeoutMinutes;
    }

    public ConversationSession getSession(String userId) {
        Object val = redisTemplate.opsForValue().get(prefix + userId);
        if (val instanceof ConversationSession session) {
            session.setLastUpdateTime(LocalDateTime.now());
            return session;
        }
        return null;
    }

    public void saveSession(String userId, String conversationId) {
        ConversationSession session = new ConversationSession();
        session.setSessionId(userId);
        session.setConversationId(conversationId);
        session.setLastUpdateTime(LocalDateTime.now());
        redisTemplate.opsForValue().set(prefix + userId, session, timeoutMinutes, TimeUnit.MINUTES);
    }

    public void refreshSession(String userId) {
        redisTemplate.expire(prefix + userId, timeoutMinutes, TimeUnit.MINUTES);
    }
}
