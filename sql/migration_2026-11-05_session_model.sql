-- 会话级模型选择：用户在聊天页切换，API Key / Base URL 仍只在服务端配置文件
ALTER TABLE t_chat_session
    ADD COLUMN model_name VARCHAR(64) NULL COMMENT '本会话使用的模型名，空则用 ai.model.model-name 默认值'
    AFTER prompt_snapshot;
