package com.tavern.mapper;

import com.tavern.entity.Tag;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface TagMapper {

    @Select("SELECT * FROM t_tag ORDER BY usage_count DESC")
    List<Tag> listAll();

    @Select("SELECT * FROM t_tag WHERE name=#{name} LIMIT 1")
    Tag findByName(String name);

    @Insert("INSERT INTO t_tag(name, color, usage_count) VALUES(#{name}, #{color}, 0)")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(Tag tag);

    @Select("SELECT t.* FROM t_tag t INNER JOIN t_character_tag ct ON t.id=ct.tag_id WHERE ct.character_id=#{characterId}")
    List<Tag> findByCharacterId(Long characterId);

    @Insert("INSERT IGNORE INTO t_character_tag(character_id, tag_id) VALUES(#{characterId}, #{tagId})")
    int bind(@Param("characterId") Long characterId, @Param("tagId") Long tagId);

    @Delete("DELETE FROM t_character_tag WHERE character_id=#{characterId}")
    int clearBindings(Long characterId);

    @Update("UPDATE t_tag SET usage_count = usage_count + 1 WHERE id=#{id}")
    int incrUsage(Long id);
}
