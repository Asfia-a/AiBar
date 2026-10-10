package com.tavern.mapper;

import com.tavern.entity.SensitiveWord;
import com.tavern.entity.ViolationLog;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface ViolationMapper {

    @Insert("""
            INSERT INTO t_violation_log(user_id, session_id, message_id, content, words)
            VALUES(#{userId}, #{sessionId}, #{messageId}, #{content}, #{words})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(ViolationLog log);

    @Select("""
            SELECT v.*, u.username FROM t_violation_log v
            LEFT JOIN t_user u ON v.user_id=u.id ORDER BY v.id DESC
            """)
    List<ViolationLog> listAll();

    @Select("SELECT * FROM t_sensitive_word")
    List<SensitiveWord> listWords();

    @Insert("""
            INSERT INTO t_sensitive_word(word, level) VALUES(#{word}, #{level})
            ON DUPLICATE KEY UPDATE level = VALUES(level)
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertWord(SensitiveWord word);

    @Update("""
            UPDATE t_sensitive_word
            SET word = COALESCE(#{word}, word), level = COALESCE(#{level}, level)
            WHERE id = #{id}
            """)
    int updateWord(SensitiveWord word);

    @Delete("DELETE FROM t_sensitive_word WHERE id = #{id}")
    int deleteWord(Long id);
}
