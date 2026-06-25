package com.fundagent.repo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fundagent.repo.entity.EpisodeSealEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface EpisodeSealMapper extends BaseMapper<EpisodeSealEntity> {

    @Select("SELECT * FROM episode_seals WHERE episode_id = #{episodeId}")
    EpisodeSealEntity findByEpisodeId(@Param("episodeId") Long episodeId);
}
