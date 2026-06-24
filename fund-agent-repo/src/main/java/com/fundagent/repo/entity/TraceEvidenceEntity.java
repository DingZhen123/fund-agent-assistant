package com.fundagent.repo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("trace_evidence")
public class TraceEvidenceEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String evidenceCode;
    private Long episodeId;
    private Long eventId;
    private String nodeId;
    private String evidenceType;
    private String sourceType;
    private String sourceReference;
    private String claim;
    private String expectedValue;
    private String actualValue;
    private String reliabilityLevel;
    private String verificationStatus;
    private String payloadJson;
    private String payloadHash;
    private LocalDateTime collectedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String createdBy;
    private String updatedBy;
}
