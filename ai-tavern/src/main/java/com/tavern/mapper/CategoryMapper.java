package com.tavern.mapper;

import com.tavern.entity.Category;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface CategoryMapper {

    /** 与线上表一致：列名为 sort（无 icon） */
    @Select("SELECT id, name, sort, created_at FROM t_category ORDER BY sort ASC, id ASC")
    List<Category> listAll();

    @Select("SELECT id, name, sort, created_at FROM t_category WHERE id=#{id}")
    Category findById(Long id);

    @Select("SELECT id, name, sort, created_at FROM t_category WHERE name=#{name} LIMIT 1")
    Category findByName(String name);
}
