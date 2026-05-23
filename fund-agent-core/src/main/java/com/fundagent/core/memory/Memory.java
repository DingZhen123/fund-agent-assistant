package com.fundagent.core.memory;

import com.fundagent.core.post.Post;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Data
public class Memory {

    private String conversationId;
    private String sessionId;
    private String compressedSummary;
    private List<Round> rounds;
    private SharedMemory sharedMemory;

    public Memory(String conversationId) {
        this.conversationId = conversationId;
        this.rounds = new ArrayList<>();
        this.sharedMemory = new SharedMemory();
    }

    public Memory(String conversationId, String compressedSummary, List<Round> rounds) {
        this.conversationId = conversationId;
        this.compressedSummary = compressedSummary;
        this.rounds = new ArrayList<>(rounds);
        this.sharedMemory = new SharedMemory();
    }

    public Round newRound(String userQuery) {
        Round round = new Round(rounds.size() + 1, userQuery);
        rounds.add(round);
        return round;
    }

    public Round getCurrentRound() {
        if (rounds.isEmpty()) return null;
        return rounds.get(rounds.size() - 1);
    }

    public int getTotalRounds() {
        return rounds.size();
    }

    public String toPromptContext(int maxRecentRounds) {
        StringBuilder sb = new StringBuilder();

        if (compressedSummary != null && !compressedSummary.isEmpty()) {
            sb.append("[历史对话摘要]\n").append(compressedSummary).append("\n\n");
        }

        int start = Math.max(0, rounds.size() - maxRecentRounds);
        for (int i = start; i < rounds.size(); i++) {
            sb.append(rounds.get(i).toPromptString()).append("\n");
        }

        return sb.toString();
    }

    public List<Post> getAllPosts() {
        return rounds.stream()
                .flatMap(r -> r.getPosts().stream())
                .collect(Collectors.toList());
    }
}
