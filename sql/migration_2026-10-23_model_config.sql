-- 修复 t_model_config 与实际代码不一致：代码 / 管理端用的是 status、temperature、top_p、max_tokens
-- 旧表却是 enabled、temperature_default，且没有 top_p / max_tokens，
-- 导致 AiModelService 启动取配置时报 Unknown column 'status'。
-- 这里改名 + 补列，保留原有数据。重复执行会报 1054/1060，可忽略。

ALTER TABLE t_model_config
    CHANGE COLUMN enabled status TINYINT DEFAULT 1 COMMENT '1启用 0停用',
    CHANGE COLUMN temperature_default temperature DECIMAL(3,2) DEFAULT 0.70,
    ADD COLUMN top_p DECIMAL(3,2) DEFAULT 0.90 AFTER temperature,
    ADD COLUMN max_tokens INT DEFAULT 500 AFTER top_p;
