package com.tavern.mapper;

import com.tavern.entity.WorldTree;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface WorldTreeMapper {

    @Select("""
            SELECT w.*, u.nickname AS creator_name
            FROM t_world_tree w
            LEFT JOIN t_user u ON w.creator_id = u.id
            WHERE w.id = #{id}
            """)
    WorldTree findById(Long id);

    @Insert("""
            INSERT INTO t_world_tree(name, cover, description, tree_json, node_count, creator_id, status)
            VALUES(#{name}, #{cover}, #{description}, #{treeJson}, #{nodeCount}, #{creatorId}, #{status})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(WorldTree tree);

    @Update("""
            UPDATE t_world_tree
            SET name=#{name}, cover=#{cover}, description=#{description},
                tree_json=#{treeJson}, node_count=#{nodeCount}, updated_at=NOW()
            WHERE id=#{id} AND creator_id=#{creatorId}
            """)
    int update(WorldTree tree);

    @Update("UPDATE t_world_tree SET status=#{status}, updated_at=NOW() WHERE id=#{id} AND creator_id=#{creatorId}")
    int updateStatus(@Param("id") Long id, @Param("creatorId") Long creatorId, @Param("status") int status);

    @Delete("DELETE FROM t_world_tree WHERE id=#{id} AND creator_id=#{creatorId}")
    int delete(@Param("id") Long id, @Param("creatorId") Long creatorId);

    @Update("UPDATE t_world_tree SET view_count = view_count + 1 WHERE id=#{id}")
    int incrViewCount(Long id);

    List<WorldTree> list(@Param("status") Integer status,
                         @Param("creatorId") Long creatorId,
                         @Param("keyword") String keyword);

    @Select("""
            SELECT w.id, w.name, w.cover, w.description, w.node_count, w.creator_id, w.status,
                   w.view_count, w.created_at, w.updated_at, u.nickname AS creator_name
            FROM t_world_tree w
            LEFT JOIN t_user u ON w.creator_id = u.id
            WHERE w.status = 2
            ORDER BY w.view_count DESC, w.updated_at DESC
            LIMIT #{limit}
            """)
    List<WorldTree> hot(@Param("limit") int limit);
}
