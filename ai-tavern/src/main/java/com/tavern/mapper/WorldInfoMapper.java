package com.tavern.mapper;

import com.tavern.entity.WorldInfoEntry;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/** 世界书条目读写。角色专属条目 + 全局条目（character_id 为空）一起返回。 */
@Mapper
public interface WorldInfoMapper {

    @Select("""
            SELECT * FROM t_world_info_entry
            WHERE enabled = 1 AND (character_id = #{characterId} OR character_id IS NULL)
            ORDER BY order_no ASC, id ASC
            """)
    List<WorldInfoEntry> findActiveForCharacter(@Param("characterId") Long characterId);

    @Select("SELECT * FROM t_world_info_entry WHERE character_id = #{characterId} ORDER BY order_no ASC, id ASC")
    List<WorldInfoEntry> listByCharacter(@Param("characterId") Long characterId);

    @Select("SELECT * FROM t_world_info_entry WHERE id = #{id}")
    WorldInfoEntry findById(@Param("id") Long id);

    @Insert("""
            INSERT INTO t_world_info_entry(character_id, user_id, title, `keys`, content, order_no,
                                           position, depth, probability, constant_on, enabled)
            VALUES(#{characterId}, #{userId}, #{title}, #{keys}, #{content}, #{orderNo},
                   #{position}, #{depth}, #{probability}, #{constantOn}, #{enabled})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(WorldInfoEntry entry);

    @Update("""
            UPDATE t_world_info_entry SET title=#{title}, `keys`=#{keys}, content=#{content},
                   order_no=#{orderNo}, position=#{position}, depth=#{depth},
                   probability=#{probability}, constant_on=#{constantOn}, enabled=#{enabled}
            WHERE id=#{id}
            """)
    int update(WorldInfoEntry entry);

    @Delete("DELETE FROM t_world_info_entry WHERE id=#{id}")
    int delete(@Param("id") Long id);
}
