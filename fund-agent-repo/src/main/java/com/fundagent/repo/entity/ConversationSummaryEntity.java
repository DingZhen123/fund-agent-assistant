package com.fundagent.repo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("conversation_summaries")
public class ConversationSummaryEntity {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long conversationId;
    private String summary;
    private Integer coveredRoundNum;
    private Integer version;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
