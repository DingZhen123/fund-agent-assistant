package com.fundagent.repo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fundagent.repo.entity.ConversationSummaryEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ConversationSummaryMapper extends BaseMapper<ConversationSummaryEntity> {
    ConversationSummaryEntity findByConversationId(@Param("conversationId") Long conversationId);

    int updateByConversationId(@Param("conversationId") Long conversationId,
                               @Param("summary") String summary,
                               @Param("coveredRoundNum") Integer coveredRoundNum,
                               @Param("version") Integer version);
}
