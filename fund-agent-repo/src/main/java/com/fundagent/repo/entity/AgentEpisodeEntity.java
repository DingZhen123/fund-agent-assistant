package com.fundagent.repo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("agent_episodes")
public class AgentEpisodeEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String episodeCode;
    private String requestId;
    private String conversationId;
    private String userIdReference;
    private String agentVersion;
    private String originalGoal;
    private String riskLevel;
    private String status;
    private Long nextSequenceNo;
    private String lastEventHash;
    private Long eventCount;
    private Integer stepCount;
    private Integer modelCallCount;
    private Integer toolCallCount;
    private Long tokenUsage;
    private String finalErrorCode;
    private String finalFailureStage;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private Long elapsedMs;
    private Boolean sealed;
    private Long rowVersion;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String createdBy;
    private String updatedBy;
}
