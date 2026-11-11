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

    /**
     * 对话链路用的取数：角色自己的条目 + 全局条目。
     *
     * <p>全局条目按 user_id 隔离：引擎内置的（user_id 为空）人人可用，
     * 某个用户自建的（user_id 有值）只对他自己的对话生效，不会污染别人的上下文。
     */
    @Select("""
            SELECT * FROM t_world_info_entry
            WHERE enabled = 1
              AND (character_id = #{characterId}
                   OR (character_id IS NULL AND (user_id IS NULL OR user_id = #{userId})))
            ORDER BY order_no ASC, id ASC
            """)
    List<WorldInfoEntry> findActiveForCharacter(@Param("characterId") Long characterId,
                                                @Param("userId") Long userId);

    @Select("SELECT * FROM t_world_info_entry WHERE character_id = #{characterId} ORDER BY order_no ASC, id ASC")
    List<WorldInfoEntry> listByCharacter(@Param("characterId") Long characterId);

    /** 管理页用：不筛 enabled，作者要能看到被自己关掉的条目 */
    @Select("SELECT * FROM t_world_info_entry WHERE character_id IS NULL AND user_id = #{userId} ORDER BY order_no ASC, id ASC")
    List<WorldInfoEntry> listGlobalByUser(@Param("userId") Long userId);

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

    @Update("UPDATE t_world_info_entry SET enabled=#{enabled} WHERE id=#{id}")
    int updateEnabled(@Param("id") Long id, @Param("enabled") int enabled);

    /** 拖拽排序：order_no 步长 10，给中间插入留空隙 */
    @Update("UPDATE t_world_info_entry SET order_no=#{orderNo} WHERE id=#{id}")
    int updateOrderNo(@Param("id") Long id, @Param("orderNo") int orderNo);
}
