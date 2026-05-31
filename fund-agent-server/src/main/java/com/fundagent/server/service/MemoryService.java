package com.fundagent.server.service;

import com.fundagent.core.memory.Memory;
import com.fundagent.core.memory.Round;
import com.fundagent.core.post.Post;
import com.fundagent.repo.entity.PostEntity;
import com.fundagent.repo.mapper.ConversationMapper;
import com.fundagent.repo.mapper.PostMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Slf4j
@Service
public class MemoryService {

    private final Map<String, Memory> cache = new ConcurrentHashMap<>();
    private final PostMapper postMapper;
    private final ConversationMapper conversationMapper;
    private final ConversationSummaryService conversationSummaryService;

    public MemoryService(PostMapper postMapper, ConversationMapper conversationMapper,
                         ConversationSummaryService conversationSummaryService) {
        this.postMapper = postMapper;
        this.conversationMapper = conversationMapper;
        this.conversationSummaryService = conversationSummaryService;
    }

    public Memory getOrCreate(String conversationId) {
        return cache.computeIfAbsent(conversationId, Memory::new);
    }

    public Memory loadFromHistory(Long conversationId) {
        String key = conversationId.toString();
        if (cache.containsKey(key)) return cache.get(key);

        List<PostEntity> entities = postMapper.findByConversationId(conversationId);
        Memory memory = new Memory(key);
        memory.setCompressedSummary(conversationSummaryService.getSummary(conversationId));

        if (!entities.isEmpty()) {
            Map<Integer, List<PostEntity>> roundMap = entities.stream()
                    .collect(Collectors.groupingBy(PostEntity::getRoundNum));

            for (Map.Entry<Integer, List<PostEntity>> entry : roundMap.entrySet()) {
                String userQuery = entry.getValue().stream()
                        .filter(p -> "User".equals(p.getSendFrom()))
                        .map(PostEntity::getMessage)
                        .findFirst().orElse("");

                Round round = new Round(entry.getKey(), userQuery);
                for (PostEntity e : entry.getValue()) {
                    Post post = Post.create(e.getSendFrom(), e.getSendTo(), e.getMessage());
                    post.setTimestamp(e.getCreatedAt() != null
                            ? e.getCreatedAt().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                            : System.currentTimeMillis());
                    round.addPost(post);
                }
                round.markCompleted();
                memory.getRounds().add(round);
            }
            log.info("Loaded {} rounds for conversation {}", roundMap.size(), conversationId);
        }

        cache.put(key, memory);
        return memory;
    }

    public void savePosts(Long conversationId, List<Post> posts, int roundNum) {
        log.info("savePosts called: conversationId={}, posts.size={}, roundNum={}",
                conversationId, posts.size(), roundNum);
        for (Post post : posts) {
            PostEntity entity = new PostEntity();
            entity.setConversationId(conversationId);
            entity.setRoundNum(roundNum);
            entity.setSendFrom(post.getSendFrom());
            entity.setSendTo(post.getSendTo());
            entity.setMessage(post.getMessage());
            entity.setAttachments(post.getAttachments() != null ? post.getAttachments().toString() : null);
            int rows = postMapper.insert(entity);
            log.info("Inserted post: rows={}, generatedId={}", rows, entity.getId());
        }
    }

    public void evict(String conversationId) {
        cache.remove(conversationId);
    }
}
