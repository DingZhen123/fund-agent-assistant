package com.fundagent.repo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("trace_events")
public class TraceEventEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String eventCode;
    private Long episodeId;
    private Long sequenceNo;
    private Long parentEventId;
    private Long causationEventId;
    private String correlationId;
    private String eventType;
    private String stage;
    private String nodeId;
    private String capability;
    private String toolName;
    private String status;
    private String reasonCode;
    private String summary;
    private String payloadJson;
    private Integer payloadSchemaVersion;
    private String payloadHash;
    private String producerId;
    private LocalDateTime occurredAt;
    private LocalDateTime receivedAt;
    private LocalDateTime persistedAt;
    private String previousHash;
    private String eventHash;
    private String signatureAlgorithm;
    private String signingKeyId;
    private String eventSignature;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String createdBy;
    private String updatedBy;
}
