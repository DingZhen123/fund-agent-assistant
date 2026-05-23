package com.fundagent.core.post;

import lombok.Builder;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Data
@Builder
public class Post {
    private String id;
    private String sendFrom;
    private String sendTo;
    private String message;
    @Builder.Default
    private List<Attachment> attachments = new ArrayList<>();
    private Long timestamp;
    private String roundId;

    public static Post create(String sendFrom, String sendTo, String message) {
        return Post.builder()
                .id(UUID.randomUUID().toString())
                .sendFrom(sendFrom)
                .sendTo(sendTo)
                .message(message)
                .attachments(new ArrayList<>())
                .timestamp(System.currentTimeMillis())
                .build();
    }

    public void addAttachment(String type, String content) {
        if (attachments == null) {
            attachments = new ArrayList<>();
        }
        attachments.add(new Attachment(type, content));
    }

    @Data
    @lombok.AllArgsConstructor
    public static class Attachment {
        private String type;
        private String content;
    }
}
