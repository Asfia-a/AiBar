package com.tavern.mapper;

import com.tavern.entity.User;
import org.apache.ibatis.annotations.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Mapper
public interface UserMapper {

    @Select("SELECT * FROM t_user WHERE id = #{id}")
    User findById(Long id);

    @Select("SELECT * FROM t_user WHERE username = #{username}")
    User findByUsername(String username);

    @Insert("""
            INSERT INTO t_user(username, password, nickname, avatar, token_balance, member_level, daily_free_token, points, role, status)
            VALUES(#{username}, #{password}, #{nickname}, #{avatar}, #{tokenBalance}, #{memberLevel}, #{dailyFreeToken}, #{points}, #{role}, #{status})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(User user);

    @Update("""
            UPDATE t_user SET nickname=#{nickname}, avatar=#{avatar}, email=#{email}, phone=#{phone}, updated_at=NOW()
            WHERE id=#{id}
            """)
    int updateProfile(User user);

    @Update("UPDATE t_user SET token_balance = token_balance - #{tokens} WHERE id = #{userId} AND token_balance >= #{tokens}")
    int deductToken(@Param("userId") Long userId, @Param("tokens") int tokens);

    @Update("UPDATE t_user SET token_balance = token_balance + #{tokens} WHERE id = #{userId}")
    int addToken(@Param("userId") Long userId, @Param("tokens") long tokens);

    @Update("UPDATE t_user SET points = points - #{points} WHERE id = #{userId} AND points >= #{points}")
    int deductPoints(@Param("userId") Long userId, @Param("points") int points);

    @Update("UPDATE t_user SET points = points + #{points} WHERE id = #{userId}")
    int addPoints(@Param("userId") Long userId, @Param("points") int points);

    @Update("""
            UPDATE t_user SET last_check_in=#{date}, consecutive_days=#{consecutive}, points=points+#{points},
            token_balance=token_balance+#{tokens} WHERE id=#{userId}
            """)
    int checkIn(@Param("userId") Long userId, @Param("date") LocalDate date,
                 @Param("consecutive") int consecutive, @Param("points") int points, @Param("tokens") long tokens);

    @Update("UPDATE t_user SET daily_free_token=#{tokens} WHERE member_level=#{level}")
    int resetDailyFreeByLevel(@Param("level") int level, @Param("tokens") long tokens);

    @Update("UPDATE t_user SET status=#{status} WHERE id=#{id}")
    int updateStatus(@Param("id") Long id, @Param("status") int status);

    @Update("UPDATE t_user SET member_level=#{level} WHERE id=#{id}")
    int updateMemberLevel(@Param("id") Long id, @Param("level") int level);

    List<User> list(@Param("keyword") String keyword);

    @Select("SELECT COUNT(*) FROM t_user")
    long countAll();

    @Select("SELECT COUNT(*) FROM t_user WHERE DATE(created_at) = CURDATE()")
    long countTodayNew();

    @Select("""
            SELECT DATE(created_at) AS day, COUNT(*) AS cnt FROM t_user
            WHERE created_at >= DATE_SUB(CURDATE(), INTERVAL #{days} DAY)
            GROUP BY DATE(created_at) ORDER BY day
            """)
    List<Map<String, Object>> userTrend(@Param("days") int days);
}
