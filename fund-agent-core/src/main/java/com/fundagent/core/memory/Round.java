package com.fundagent.core.memory;

import com.fundagent.core.post.Post;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class Round {
    private int roundNum;
    private String userQuery;
    private List<Post> posts;
    private boolean completed;

    public Round(int roundNum, String userQuery) {
        this.roundNum = roundNum;
        this.userQuery = userQuery;
        this.posts = new ArrayList<>();
        this.completed = false;
    }

    public void addPost(Post post) {
        posts.add(post);
    }

    public void markCompleted() {
        this.completed = true;
    }

    public String toPromptString() {
        StringBuilder sb = new StringBuilder();
        sb.append("User: ").append(userQuery).append("\n");
        for (Post post : posts) {
            if (!"User".equals(post.getSendFrom())) {
                sb.append(post.getSendFrom()).append(": ")
                        .append(post.getMessage()).append("\n");
            }
        }
        return sb.toString();
    }
}
