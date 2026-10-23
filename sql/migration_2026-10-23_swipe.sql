-- 消息操作：多版本回复（swipe）+ 编辑 + 分支
-- MySQL 8.0 不支持 ADD COLUMN IF NOT EXISTS，重复执行报 1060 可忽略
-- 只加表加列，不动数据；两份 schema.sql 已同步

ALTER TABLE t_chat_message
    ADD COLUMN swipe_count INT DEFAULT 1 COMMENT '共几条备选回复（含当前这条）' AFTER tokens,
    ADD COLUMN swipe_index INT DEFAULT 0 COMMENT '当前展示第几条，从 0 开始' AFTER swipe_count;

CREATE TABLE IF NOT EXISTS t_message_swipe (
    id          BIGINT PRIMARY KEY AUTO_INCREMENT,
    message_id  BIGINT NOT NULL,
    idx         INT NOT NULL DEFAULT 0 COMMENT '版本序号，0 是第一次生成',
    content     TEXT NOT NULL,
    tokens      INT DEFAULT 0,
    created_at  DATETIME DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_message_idx (message_id, idx),
    INDEX idx_message (message_id)
);
