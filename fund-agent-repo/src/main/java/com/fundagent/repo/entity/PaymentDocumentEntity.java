package com.fundagent.repo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("payment_documents")
public class PaymentDocumentEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String documentCode;
    private String documentType;
    private String status;
    private BigDecimal amount;
    private String payer;
    private String payee;
    private LocalDateTime payTime;
    private Integer hasReceipt;
    private String receiptFileName;
    private String receiptFileUrl;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
