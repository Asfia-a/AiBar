package com.tavern.mapper;

import com.tavern.entity.CharacterCard;
import com.tavern.entity.Favorite;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface FavoriteMapper {

    @Select("SELECT * FROM t_favorite WHERE user_id=#{userId} AND character_id=#{characterId}")
    Favorite find(@Param("userId") Long userId, @Param("characterId") Long characterId);

    @Insert("INSERT INTO t_favorite(user_id, character_id) VALUES(#{userId}, #{characterId})")
    int insert(@Param("userId") Long userId, @Param("characterId") Long characterId);

    @Delete("DELETE FROM t_favorite WHERE user_id=#{userId} AND character_id=#{characterId}")
    int delete(@Param("userId") Long userId, @Param("characterId") Long characterId);

    @Delete("DELETE FROM t_favorite WHERE character_id=#{characterId}")
    int deleteByCharacter(Long characterId);

    @Select("""
            SELECT c.*, u.nickname AS creator_name FROM t_favorite f
            INNER JOIN t_character c ON f.character_id=c.id
            LEFT JOIN t_user u ON c.creator_id=u.id
            WHERE f.user_id=#{userId} ORDER BY f.created_at DESC
            """)
    List<CharacterCard> listCharacters(Long userId);
}
