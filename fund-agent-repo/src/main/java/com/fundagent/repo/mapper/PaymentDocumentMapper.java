package com.fundagent.repo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fundagent.repo.entity.PaymentDocumentEntity;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface PaymentDocumentMapper extends BaseMapper<PaymentDocumentEntity> {

    PaymentDocumentEntity findByDocumentCode(String documentCode);

    List<PaymentDocumentEntity> findByDocumentCodes(List<String> documentCodes);
}
