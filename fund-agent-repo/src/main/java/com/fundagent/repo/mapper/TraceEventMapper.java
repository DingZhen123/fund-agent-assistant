package com.fundagent.repo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fundagent.repo.entity.TraceEventEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface TraceEventMapper extends BaseMapper<TraceEventEntity> {

    @Select("SELECT * FROM trace_events WHERE event_code = #{eventCode}")
    TraceEventEntity findByEventCode(@Param("eventCode") String eventCode);

    @Select("""
            SELECT * FROM trace_events
            WHERE episode_id = #{episodeId}
            ORDER BY sequence_no ASC
            """)
    List<TraceEventEntity> findByEpisodeId(@Param("episodeId") Long episodeId);
}
