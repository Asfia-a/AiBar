package com.tavern.mapper;

import com.tavern.entity.CheckIn;
import org.apache.ibatis.annotations.*;

import java.time.LocalDate;
import java.util.List;

@Mapper
public interface CheckInMapper {

    @Select("SELECT * FROM t_check_in WHERE user_id=#{userId} AND check_date=#{date}")
    CheckIn find(@Param("userId") Long userId, @Param("date") LocalDate date);

    @Insert("INSERT INTO t_check_in(user_id, check_date, points, consecutive) VALUES(#{userId}, #{checkDate}, #{points}, #{consecutive})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(CheckIn checkIn);

    @Select("SELECT * FROM t_check_in WHERE user_id=#{userId} ORDER BY check_date DESC LIMIT 30")
    List<CheckIn> listRecent(Long userId);
}
