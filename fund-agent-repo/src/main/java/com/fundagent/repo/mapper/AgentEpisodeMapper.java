package com.fundagent.repo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fundagent.repo.entity.AgentEpisodeEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface AgentEpisodeMapper extends BaseMapper<AgentEpisodeEntity> {

    int insertIdempotent(AgentEpisodeEntity entity);

    @Select("SELECT * FROM agent_episodes WHERE episode_code = #{episodeCode}")
    AgentEpisodeEntity findByEpisodeCode(@Param("episodeCode") String episodeCode);

    @Select("SELECT * FROM agent_episodes WHERE request_id = #{requestId}")
    AgentEpisodeEntity findByRequestId(@Param("requestId") String requestId);

    @Select("SELECT * FROM agent_episodes WHERE episode_code = #{episodeCode} FOR UPDATE")
    AgentEpisodeEntity lockByEpisodeCode(@Param("episodeCode") String episodeCode);

    @Select("SELECT * FROM agent_episodes WHERE request_id = #{requestId} FOR UPDATE")
    AgentEpisodeEntity lockByRequestId(@Param("requestId") String requestId);

    @Update("""
            UPDATE agent_episodes
            SET status = #{status},
                next_sequence_no = #{nextSequenceNo},
                last_event_hash = #{lastEventHash},
                event_count = #{eventCount},
                step_count = #{stepCount},
                model_call_count = #{modelCallCount},
                tool_call_count = #{toolCallCount},
                token_usage = #{tokenUsage},
                final_error_code = #{finalErrorCode},
                final_failure_stage = #{finalFailureStage},
                finished_at = #{finishedAt},
                elapsed_ms = #{elapsedMs},
                sealed = #{sealed},
                row_version = #{newRowVersion},
                updated_at = #{updatedAt},
                updated_by = #{updatedBy}
            WHERE id = #{id}
              AND row_version = #{expectedRowVersion}
            """)
    int updateProjection(@Param("id") Long id,
                         @Param("status") String status,
                         @Param("nextSequenceNo") Long nextSequenceNo,
                         @Param("lastEventHash") String lastEventHash,
                         @Param("eventCount") Long eventCount,
                         @Param("stepCount") Integer stepCount,
                         @Param("modelCallCount") Integer modelCallCount,
                         @Param("toolCallCount") Integer toolCallCount,
                         @Param("tokenUsage") Long tokenUsage,
                         @Param("finalErrorCode") String finalErrorCode,
                         @Param("finalFailureStage") String finalFailureStage,
                         @Param("finishedAt") java.time.LocalDateTime finishedAt,
                         @Param("elapsedMs") Long elapsedMs,
                         @Param("sealed") Boolean sealed,
                         @Param("newRowVersion") Long newRowVersion,
                         @Param("updatedAt") java.time.LocalDateTime updatedAt,
                         @Param("updatedBy") String updatedBy,
                         @Param("expectedRowVersion") Long expectedRowVersion);
}
