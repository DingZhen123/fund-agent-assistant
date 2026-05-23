package com.fundagent.core.orchestration;

import com.fundagent.core.post.Post;
import lombok.Data;

import java.util.List;

@Data
public class OrchestrationResult {
    private String finalAnswer;
    private List<Post> allPosts;
    private long elapsedMs;
}
