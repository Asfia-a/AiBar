-- 提示词引擎升级：世界书 + 作者注释
-- 已有库执行本文件即可，新库直接跑 schema.sql 就包含这些结构
-- 执行：mysql -u<user> -p ai_tavern < sql/migration_2026-10-23_prompt.sql

CREATE TABLE IF NOT EXISTS t_world_info_entry (
    id            BIGINT PRIMARY KEY AUTO_INCREMENT,
    character_id  BIGINT COMMENT 'NULL 表示全局条目',
    user_id       BIGINT,
    title         VARCHAR(100) NOT NULL COMMENT '备注名，不进 prompt',
    `keys`        VARCHAR(500) COMMENT '触发关键词，英文逗号分隔，/.../ 形式按正则',
    content       TEXT NOT NULL COMMENT '命中后插入的内容',
    order_no      INT DEFAULT 100 COMMENT '插入顺序，越大越靠近上下文末尾',
    position      VARCHAR(20) DEFAULT 'after_char' COMMENT 'before_char/after_char/in_chat',
    depth         INT DEFAULT 4 COMMENT 'position=in_chat 时插到倒数第几条之前',
    probability   INT DEFAULT 100 COMMENT '触发概率 0-100',
    constant_on   TINYINT DEFAULT 0 COMMENT '1=无条件插入',
    enabled       TINYINT DEFAULT 1,
    created_at    DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at    DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_character (character_id),
    INDEX idx_enabled (enabled)
);

-- MySQL 8.0 不支持 ADD COLUMN IF NOT EXISTS，重复执行会报 1060，可忽略
ALTER TABLE t_chat_session
    ADD COLUMN author_note  TEXT COMMENT '作者注释内容',
    ADD COLUMN an_depth     INT DEFAULT 0 COMMENT '0=历史末尾，N=倒数第 N 条之前',
    ADD COLUMN an_frequency INT DEFAULT 1 COMMENT '每 N 条用户消息插一次，0=不插';
