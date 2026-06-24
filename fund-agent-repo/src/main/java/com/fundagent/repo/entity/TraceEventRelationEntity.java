package com.fundagent.repo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("trace_event_relations")
public class TraceEventRelationEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long episodeId;
    private Long sourceEventId;
    private Long targetEventId;
    private String relationType;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String createdBy;
    private String updatedBy;
}
