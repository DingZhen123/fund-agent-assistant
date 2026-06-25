package com.fundagent.repo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fundagent.repo.entity.TraceEventRelationEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface TraceEventRelationMapper extends BaseMapper<TraceEventRelationEntity> {

    @Select("SELECT * FROM trace_event_relations WHERE episode_id = #{episodeId} ORDER BY id ASC")
    List<TraceEventRelationEntity> findByEpisodeId(@Param("episodeId") Long episodeId);

    @Select("SELECT * FROM trace_event_relations WHERE target_event_id = #{targetEventId} ORDER BY id ASC")
    List<TraceEventRelationEntity> findByTargetEventId(@Param("targetEventId") Long targetEventId);
}
