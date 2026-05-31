package com.fundagent.server.service;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.fundagent.common.model.Message;
import com.fundagent.core.llm.LLMService;
import com.fundagent.core.memory.Memory;
import com.fundagent.repo.entity.ConversationSummaryEntity;
import com.fundagent.repo.mapper.ConversationSummaryMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;
import java.util.List;

@Slf4j
@Service
public class ConversationSummaryService {
    private static final String REDIS_PREFIX = "agent:conversation:summary:";
    private static final long SUMMARY_CACHE_MINUTES = 30;
    private static final int MIN_ROUNDS_TO_SUMMARIZE = 6;
    private static final int SUMMARY_REFRESH_ROUND_GAP = 4;

    private final ConversationSummaryMapper summaryMapper;
    private final RedisTemplate<String, Object> redisTemplate;
    private final LLMService llmService;

    public ConversationSummaryService(ConversationSummaryMapper summaryMapper,
                                      RedisTemplate<String, Object> redisTemplate,
                                      LLMService llmService) {
        this.summaryMapper = summaryMapper;
        this.redisTemplate = redisTemplate;
        this.llmService = llmService;
    }

    public String getSummary(Long conversationId) {
        String key = key(conversationId);
        Object cached = redisTemplate.opsForValue().get(key);
        if (cached instanceof String summary && !summary.isEmpty()) {
            redisTemplate.expire(key, SUMMARY_CACHE_MINUTES, TimeUnit.MINUTES);
            return summary;
        }

        ConversationSummaryEntity entity;
        try {
            entity = summaryMapper.findByConversationId(conversationId);
        } catch (Exception e) {
            log.error("Failed to load conversation summary, table may not exist: conversationId={}", conversationId, e);
            return null;
        }
        if (entity == null || entity.getSummary() == null || entity.getSummary().isEmpty()) {
            return null;
        }
        redisTemplate.opsForValue().set(key, entity.getSummary(), SUMMARY_CACHE_MINUTES, TimeUnit.MINUTES);
        return entity.getSummary();
    }

    public void saveSummary(Long conversationId, String summary, Integer coveredRoundNum) {
        if (conversationId == null || summary == null || summary.isEmpty()) return;

        ConversationSummaryEntity existing;
        try {
            existing = summaryMapper.findByConversationId(conversationId);
        } catch (Exception e) {
            log.warn("Failed to save conversation summary, table may not exist: conversationId={}", conversationId, e);
            return;
        }
        int version = existing != null && existing.getVersion() != null ? existing.getVersion() + 1 : 1;
        int rows = summaryMapper.updateByConversationId(conversationId, summary, coveredRoundNum, version);
        if (rows == 0) {
            ConversationSummaryEntity entity = new ConversationSummaryEntity();
            entity.setConversationId(conversationId);
            entity.setSummary(summary);
            entity.setCoveredRoundNum(coveredRoundNum != null ? coveredRoundNum : 0);
            entity.setVersion(version);
            summaryMapper.insert(entity);
        }

        redisTemplate.opsForValue().set(key(conversationId), summary, SUMMARY_CACHE_MINUTES, TimeUnit.MINUTES);
        log.info("Conversation summary saved: conversationId={}, coveredRoundNum={}, version={}",
                conversationId, coveredRoundNum, version);
    }

    public void refreshSummaryIfNeeded(Long conversationId, Memory memory) {
        if (conversationId == null || memory == null || memory.getTotalRounds() < MIN_ROUNDS_TO_SUMMARIZE) {
            return;
        }

        ConversationSummaryEntity existing;
        try {
            existing = summaryMapper.findByConversationId(conversationId);
        } catch (Exception e) {
            log.warn("Skip summary refresh because summary table is unavailable: conversationId={}", conversationId, e);
            return;
        }

        int coveredRoundNum = existing != null && existing.getCoveredRoundNum() != null
                ? existing.getCoveredRoundNum()
                : 0;
        if (memory.getTotalRounds() - coveredRoundNum < SUMMARY_REFRESH_ROUND_GAP) {
            return;
        }

        String summary = summarize(memory, existing != null ? existing.getSummary() : null);
        if (summary != null && !summary.isEmpty()) {
            memory.setCompressedSummary(summary);
            saveSummary(conversationId, summary, memory.getTotalRounds());
        }
    }

    private String summarize(Memory memory, String previousSummary) {
        String systemPrompt = """
                你是企业Agent的会话摘要器。
                请把对话历史压缩成稳定、简洁、可供后续Agent使用的摘要。
                重点保留：用户目标、已完成事项、关键业务实体、工具结果、待办事项。
                不要记录敏感密钥、密码、token、无关寒暄或内部实现细节。
                """;

        StringBuilder user = new StringBuilder();
        if (previousSummary != null && !previousSummary.isEmpty()) {
            user.append("[已有摘要]\n").append(previousSummary).append("\n\n");
        }
        user.append("[最近会话]\n").append(memory.toPromptContext(12));

        String raw = llmService.chatStructured(systemPrompt, List.of(), user.toString(),
                "conversation_summary", summarySchema());
        JSONObject json = JSON.parseObject(raw);
        return json.getString("summary");
    }

    private String summarySchema() {
        JSONObject root = new JSONObject();
        root.put("type", "object");
        root.put("additionalProperties", false);

        JSONObject properties = new JSONObject();
        JSONObject summary = new JSONObject();
        summary.put("type", "string");
        properties.put("summary", summary);

        root.put("properties", properties);
        root.put("required", List.of("summary"));
        return root.toJSONString();
    }

    private String key(Long conversationId) {
        return REDIS_PREFIX + conversationId;
    }
}
