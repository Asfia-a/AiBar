-- 采样参数补全：top_k / 频率惩罚 / 存在惩罚 / 重复惩罚 / 停止词
-- MySQL 8.0 不支持 ADD COLUMN IF NOT EXISTS，重复执行报 1060 可忽略
-- 只加列不动数据；schema.sql 与 ai-tavern/src/main/resources/schema.sql 已同步

ALTER TABLE t_character
    ADD COLUMN top_k               INT COMMENT 'top_k 采样；NULL=不下发（仅自建推理服务支持）' AFTER max_tokens,
    ADD COLUMN frequency_penalty   DECIMAL(3,2) COMMENT '-2~2，按词频惩罚，NULL=不下发' AFTER top_k,
    ADD COLUMN presence_penalty    DECIMAL(3,2) COMMENT '-2~2，鼓励新话题，NULL=不下发' AFTER frequency_penalty,
    ADD COLUMN repetition_penalty  DECIMAL(4,2) COMMENT '0.8~1.5，非 OpenAI 标准，NULL=不下发' AFTER presence_penalty,
    ADD COLUMN stop_words          VARCHAR(255) COMMENT '停止词，多个用 | 分隔' AFTER repetition_penalty;
