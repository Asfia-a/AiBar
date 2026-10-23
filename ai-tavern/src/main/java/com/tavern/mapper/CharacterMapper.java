package com.tavern.mapper;

import com.tavern.entity.CharacterCard;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface CharacterMapper {

    @Select("SELECT c.*, u.nickname AS creator_name, cat.name AS category_name FROM t_character c " +
            "LEFT JOIN t_user u ON c.creator_id=u.id LEFT JOIN t_category cat ON c.category_id=cat.id WHERE c.id=#{id}")
    CharacterCard findById(Long id);

    @Insert("""
            INSERT INTO t_character(name, avatar, description, personality, background_story, example_dialogue, greeting,
            creator_id, category_id, status, source_id, version, temperature, top_p, max_tokens,
            top_k, frequency_penalty, presence_penalty, repetition_penalty, stop_words)
            VALUES(#{name}, #{avatar}, #{description}, #{personality}, #{backgroundStory}, #{exampleDialogue}, #{greeting},
            #{creatorId}, #{categoryId}, #{status}, #{sourceId}, #{version}, #{temperature}, #{topP}, #{maxTokens},
            #{topK}, #{frequencyPenalty}, #{presencePenalty}, #{repetitionPenalty}, #{stopWords})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(CharacterCard card);

    @Update("""
            UPDATE t_character SET name=#{name}, avatar=#{avatar}, description=#{description}, personality=#{personality},
            background_story=#{backgroundStory}, example_dialogue=#{exampleDialogue}, greeting=#{greeting},
            category_id=#{categoryId}, temperature=#{temperature}, top_p=#{topP}, max_tokens=#{maxTokens},
            top_k=#{topK}, frequency_penalty=#{frequencyPenalty}, presence_penalty=#{presencePenalty},
            repetition_penalty=#{repetitionPenalty}, stop_words=#{stopWords},
            version=IFNULL(version,1)+1, updated_at=NOW()
            WHERE id=#{id} AND creator_id=#{creatorId}
            """)
    int update(CharacterCard card);

    @Update("UPDATE t_character SET status=#{status}, updated_at=NOW() WHERE id=#{id}")
    int updateStatus(@Param("id") Long id, @Param("status") int status);

    @Delete("DELETE FROM t_character WHERE id=#{id} AND creator_id=#{creatorId}")
    int deleteByCreator(@Param("id") Long id, @Param("creatorId") Long creatorId);

    List<CharacterCard> list(@Param("status") Integer status, @Param("categoryId") Long categoryId,
                             @Param("tagId") Long tagId, @Param("keyword") String keyword,
                             @Param("creatorId") Long creatorId);

    @Select("SELECT c.*, u.nickname AS creator_name FROM t_character c LEFT JOIN t_user u ON c.creator_id=u.id " +
            "WHERE c.status=2 ORDER BY c.hot_score DESC LIMIT #{limit}")
    List<CharacterCard> hot(@Param("limit") int limit);

    @Select("SELECT c.*, u.nickname AS creator_name FROM t_character c LEFT JOIN t_user u ON c.creator_id=u.id " +
            "WHERE c.status=2 ORDER BY c.created_at DESC LIMIT #{limit}")
    List<CharacterCard> newest(@Param("limit") int limit);

    @Select("SELECT c.*, u.nickname AS creator_name FROM t_character c LEFT JOIN t_user u ON c.creator_id=u.id " +
            "WHERE c.status=2 AND (c.name LIKE CONCAT('%',#{keyword},'%') OR c.description LIKE CONCAT('%',#{keyword},'%')) " +
            "ORDER BY c.hot_score DESC")
    List<CharacterCard> search(@Param("keyword") String keyword);

    @Update("UPDATE t_character SET chat_count = chat_count + 1 WHERE id=#{id}")
    int incrChatCount(Long id);

    @Update("UPDATE t_character SET favorite_count = favorite_count + #{delta} WHERE id=#{id}")
    int updateFavoriteCount(@Param("id") Long id, @Param("delta") int delta);

    @Update("UPDATE t_character SET rating=#{rating}, rating_count=#{count} WHERE id=#{id}")
    int updateRating(@Param("id") Long id, @Param("rating") double rating, @Param("count") int count);

    @Update("UPDATE t_character SET hot_score = chat_count*0.4 + rating*20*0.3 + favorite_count*0.3")
    int recalcAllHotScore();

    @Select("SELECT id, hot_score FROM t_character WHERE status=2")
    List<CharacterCard> listHotScores();

    @Select("SELECT COUNT(*) FROM t_character WHERE status=#{status}")
    long countByStatus(int status);

    @Select("""
            SELECT c.*, u.nickname AS creator_name FROM t_character c
            LEFT JOIN t_user u ON c.creator_id=u.id
            WHERE c.status=#{status} ORDER BY c.updated_at DESC
            """)
    List<CharacterCard> listByStatus(int status);
}
