-- 用户人设 + 长对话记忆摘要（会话级）
ALTER TABLE t_chat_session
    ADD COLUMN persona_name     VARCHAR(64)  NULL COMMENT '用户人设称呼，覆盖 {{user}}',
    ADD COLUMN persona_desc     TEXT         NULL COMMENT '用户人设描述，注入提示词',
    ADD COLUMN memory_summary   MEDIUMTEXT   NULL COMMENT '被裁掉的早期剧情摘要',
    ADD COLUMN memory_until_id  BIGINT       NULL COMMENT '摘要已覆盖到的消息 id（含）',
    ADD COLUMN memory_enabled   TINYINT      NOT NULL DEFAULT 1 COMMENT '1=自动摘要，0=关闭';
