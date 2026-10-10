package com.tavern.mapper;

import com.tavern.entity.ModelConfig;
import org.apache.ibatis.annotations.*;

@Mapper
public interface ModelConfigMapper {

    @Select("SELECT * FROM t_model_config WHERE status=1 ORDER BY id ASC LIMIT 1")
    ModelConfig findEnabled();

    @Select("SELECT * FROM t_model_config ORDER BY id ASC LIMIT 1")
    ModelConfig findOne();

    @Update("""
            UPDATE t_model_config SET provider=#{provider}, api_key=#{apiKey}, base_url=#{baseUrl},
            model_name=#{modelName}, temperature=#{temperature}, top_p=#{topP},
            max_tokens=#{maxTokens}, status=#{status}, updated_at=NOW()
            WHERE id=#{id}
            """)
    int update(ModelConfig config);
}
