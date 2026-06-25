package com.fundagent.repo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fundagent.repo.entity.TraceEvidenceEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface TraceEvidenceMapper extends BaseMapper<TraceEvidenceEntity> {

    @Select("SELECT * FROM trace_evidence WHERE evidence_code = #{evidenceCode}")
    TraceEvidenceEntity findByEvidenceCode(@Param("evidenceCode") String evidenceCode);

    @Select("SELECT * FROM trace_evidence WHERE episode_id = #{episodeId} ORDER BY id ASC")
    List<TraceEvidenceEntity> findByEpisodeId(@Param("episodeId") Long episodeId);
}
