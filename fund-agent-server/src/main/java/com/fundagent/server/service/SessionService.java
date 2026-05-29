package com.fundagent.server.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

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

    public String getSession(String userId) {
        String key = prefix + userId;
        Object val = redisTemplate.opsForValue().get(key);
        if (val instanceof String sessionId) {
            redisTemplate.expire(key, timeoutMinutes, TimeUnit.MINUTES);
            log.debug("Session hit: key={}, conversationId={}", key, sessionId);
            return sessionId;
        }
        log.debug("Session miss: key={}, valueType={}", key, val != null ? val.getClass().getName() : "null");
        return null;
    }

    public void saveSession(String userId, String conversationId) {
        String key = prefix + userId;
        redisTemplate.opsForValue().set(key, conversationId, timeoutMinutes, TimeUnit.MINUTES);
        log.debug("Session saved: key={}, conversationId={}, timeoutMinutes={}", key, conversationId, timeoutMinutes);
    }

    public void refreshSession(String userId) {
        String key = prefix + userId;
        redisTemplate.expire(key, timeoutMinutes, TimeUnit.MINUTES);
        log.debug("Session refreshed: key={}, timeoutMinutes={}", key, timeoutMinutes);
    }
}
