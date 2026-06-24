package com.fundagent.repo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("episode_seals")
public class EpisodeSealEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String sealCode;
    private Long episodeId;
    private Long finalSequenceNo;
    private String finalEventHash;
    private Long eventCount;
    private String finalStatus;
    private LocalDateTime sealedAt;
    private String signatureAlgorithm;
    private String signingKeyId;
    private String sealSignature;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String createdBy;
    private String updatedBy;
}
