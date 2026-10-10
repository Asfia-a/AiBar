package com.tavern.mapper;

import com.tavern.entity.Rating;
import org.apache.ibatis.annotations.*;

@Mapper
public interface RatingMapper {

    @Select("SELECT * FROM t_rating WHERE user_id=#{userId} AND character_id=#{characterId}")
    Rating find(@Param("userId") Long userId, @Param("characterId") Long characterId);

    @Insert("INSERT INTO t_rating(user_id, character_id, score) VALUES(#{userId}, #{characterId}, #{score}) " +
            "ON DUPLICATE KEY UPDATE score=#{score}")
    int upsert(@Param("userId") Long userId, @Param("characterId") Long characterId, @Param("score") int score);

    @Select("SELECT IFNULL(AVG(score),0) FROM t_rating WHERE character_id=#{characterId}")
    Double avgScore(Long characterId);

    @Select("SELECT COUNT(*) FROM t_rating WHERE character_id=#{characterId}")
    int countByCharacter(Long characterId);

    @Delete("DELETE FROM t_rating WHERE character_id=#{characterId}")
    int deleteByCharacter(Long characterId);
}
