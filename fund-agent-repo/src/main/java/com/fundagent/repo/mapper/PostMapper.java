package com.fundagent.repo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fundagent.repo.entity.PostEntity;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface PostMapper extends BaseMapper<PostEntity> {

    List<PostEntity> findByConversationId(Long conversationId);

    List<PostEntity> findByConversationIdAfterRound(Long conversationId, int minRoundNum);
}
