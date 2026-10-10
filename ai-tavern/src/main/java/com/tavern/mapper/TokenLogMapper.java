package com.tavern.mapper;

import com.tavern.entity.TokenLog;
import org.apache.ibatis.annotations.*;

import java.util.List;
import java.util.Map;

@Mapper
public interface TokenLogMapper {

    @Insert("""
            INSERT INTO t_token_log(user_id, session_id, tokens_used, balance_before, balance_after, type)
            VALUES(#{userId}, #{sessionId}, #{tokensUsed}, #{balanceBefore}, #{balanceAfter}, #{type})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(TokenLog log);

    @Select("SELECT * FROM t_token_log WHERE user_id=#{userId} ORDER BY id DESC")
    List<TokenLog> listByUser(Long userId);

    @Select("""
            SELECT DATE(created_at) AS day, IFNULL(SUM(CASE WHEN type=1 THEN tokens_used ELSE 0 END),0) AS cnt
            FROM t_token_log WHERE created_at >= DATE_SUB(CURDATE(), INTERVAL #{days} DAY)
            GROUP BY DATE(created_at) ORDER BY day
            """)
    List<Map<String, Object>> tokenTrend(@Param("days") int days);

    /** Token 明细：按用途分组统计。type 1对话 2叙事生成 3其他消耗 */
    @Select("""
            SELECT type, COUNT(*) AS calls, IFNULL(SUM(tokens_used),0) AS tokens,
            IFNULL(AVG(tokens_used),0) AS avg_tokens
            FROM t_token_log WHERE type=1
            GROUP BY type
            """)
    List<Map<String, Object>> breakdownByType();

    /** 按会话聚合，能看出哪一场对话最费 token */
    @Select("""
            SELECT session_id, COUNT(*) AS calls, IFNULL(SUM(tokens_used),0) AS tokens
            FROM t_token_log WHERE session_id IS NOT NULL AND type=1
            GROUP BY session_id ORDER BY tokens DESC LIMIT #{limit}
            """)
    List<Map<String, Object>> topSessions(@Param("limit") int limit);

    @Select("SELECT IFNULL(SUM(tokens_used),0) FROM t_token_log WHERE type=1 AND DATE(created_at)=CURDATE()")
    long sumToday();
}
