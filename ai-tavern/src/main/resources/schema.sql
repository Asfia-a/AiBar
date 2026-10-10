CREATE DATABASE IF NOT EXISTS ai_tavern DEFAULT CHARSET utf8mb4;
USE ai_tavern;

CREATE TABLE IF NOT EXISTS t_user (
    id          BIGINT PRIMARY KEY AUTO_INCREMENT,
    username    VARCHAR(50) NOT NULL UNIQUE,
    password    VARCHAR(100) NOT NULL COMMENT 'BCrypt',
    avatar      VARCHAR(255),
    nickname    VARCHAR(50),
    email       VARCHAR(100),
    phone       VARCHAR(20),
    token_balance     BIGINT DEFAULT 10000,
    member_level      TINYINT DEFAULT 0 COMMENT '0普�?1白银 2黄金 3钻石',
    daily_free_token  BIGINT DEFAULT 2000,
    last_check_in     DATE,
    consecutive_days  INT DEFAULT 0,
    points      INT DEFAULT 0,
    role        TINYINT DEFAULT 0 COMMENT '0用户 1管理�?,
    status      TINYINT DEFAULT 1 COMMENT '0封禁 1正常',
    created_at  DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS t_category (
    id          BIGINT PRIMARY KEY AUTO_INCREMENT,
    name        VARCHAR(50) NOT NULL UNIQUE,
    sort        INT DEFAULT 0,
    created_at  DATETIME DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS t_character (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    name            VARCHAR(50) NOT NULL,
    avatar          VARCHAR(255),
    description     TEXT,
    personality     TEXT,
    background_story TEXT,
    example_dialogue TEXT COMMENT 'JSON',
    greeting        TEXT,
    creator_id      BIGINT NOT NULL,
    category_id     BIGINT,
    status          TINYINT DEFAULT 0 COMMENT '0草稿 1审核�?2已上�?3已下�?4驳回',
    source_id       BIGINT COMMENT '复刻来源，NULL=原创',
    version         INT DEFAULT 1,
    temperature     DECIMAL(3,2) DEFAULT 0.70,
    top_p           DECIMAL(3,2) DEFAULT 0.90,
    max_tokens      INT DEFAULT 500,
    chat_count      INT DEFAULT 0,
    rating          DECIMAL(3,2) DEFAULT 0.00,
    rating_count    INT DEFAULT 0,
    favorite_count  INT DEFAULT 0,
    hot_score       DECIMAL(10,2) DEFAULT 0.00,
    created_at      DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_creator (creator_id),
    INDEX idx_category (category_id),
    INDEX idx_status (status),
    INDEX idx_hot_score (hot_score DESC)
);

CREATE TABLE IF NOT EXISTS t_tag (
    id      BIGINT PRIMARY KEY AUTO_INCREMENT,
    name    VARCHAR(30) NOT NULL UNIQUE,
    color   VARCHAR(10) DEFAULT '#409EFF',
    usage_count INT DEFAULT 0
);

CREATE TABLE IF NOT EXISTS t_character_tag (
    character_id BIGINT NOT NULL,
    tag_id       BIGINT NOT NULL,
    PRIMARY KEY (character_id, tag_id)
);

CREATE TABLE IF NOT EXISTS t_chat_session (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id         BIGINT NOT NULL,
    character_id    BIGINT NOT NULL,
    title           VARCHAR(100),
    last_message    TEXT,
    last_role       VARCHAR(10),
    message_count   INT DEFAULT 0,
    total_tokens    BIGINT DEFAULT 0,
    prompt_snapshot MEDIUMTEXT COMMENT '�Ự����ʱ����Ľ�ɫ����JSON',
    created_at      DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_user_character (user_id, character_id)
);

CREATE TABLE IF NOT EXISTS t_chat_message (
    id          BIGINT PRIMARY KEY AUTO_INCREMENT,
    session_id  BIGINT NOT NULL,
    role        VARCHAR(10) NOT NULL COMMENT 'user/assistant/system',
    content     TEXT NOT NULL,
    tokens      INT DEFAULT 0,
    created_at  DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_session (session_id)
);

CREATE TABLE IF NOT EXISTS t_token_log (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id         BIGINT NOT NULL,
    session_id      BIGINT,
    tokens_used     INT NOT NULL,
    balance_before  BIGINT NOT NULL,
    balance_after   BIGINT NOT NULL,
    type            TINYINT DEFAULT 1 COMMENT '1消费 2充�?3签到 4积分兑换',
    created_at      DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_user (user_id)
);

CREATE TABLE IF NOT EXISTS t_rating (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    character_id BIGINT NOT NULL,
    score TINYINT NOT NULL,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_user_character (user_id, character_id)
);

CREATE TABLE IF NOT EXISTS t_favorite (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    character_id BIGINT NOT NULL,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_user_character (user_id, character_id)
);

-- 评论：target_type 区分角色卡(1)与叙事作品(2)，两者共用这张表
CREATE TABLE IF NOT EXISTS t_comment (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    target_type TINYINT NOT NULL DEFAULT 1 COMMENT '1角色卡 2叙事作品',
    target_id BIGINT NULL COMMENT '目标ID，角色卡场景下与 character_id 相同',
    character_id BIGINT NULL COMMENT '冗余字段，保留以兼容既有角色卡查询',
    content TEXT NOT NULL,
    status TINYINT DEFAULT 1 COMMENT '0隐藏 1正常',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_target (target_type, target_id, status)
);

-- 点赞：角色卡与叙事作品共用
CREATE TABLE IF NOT EXISTS t_like (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    target_type TINYINT NOT NULL DEFAULT 1 COMMENT '1角色卡 2叙事作品',
    target_id BIGINT NOT NULL,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_user_target (user_id, target_type, target_id),
    INDEX idx_target (target_type, target_id)
);

-- 收藏：角色卡与叙事作品共用
CREATE TABLE IF NOT EXISTS t_collect (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    target_type TINYINT NOT NULL DEFAULT 1 COMMENT '1角色卡 2叙事作品',
    target_id BIGINT NOT NULL,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_user_target (user_id, target_type, target_id),
    INDEX idx_target (target_type, target_id)
);

CREATE TABLE IF NOT EXISTS t_check_in (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    check_date DATE NOT NULL,
    points INT,
    consecutive INT,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_user_date (user_id, check_date)
);

CREATE TABLE IF NOT EXISTS t_point_log (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    points INT NOT NULL,
    type TINYINT DEFAULT 1 COMMENT '1签到 2兑换 3系统',
    remark VARCHAR(200),
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_user (user_id)
);

CREATE TABLE IF NOT EXISTS t_recharge_order (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    amount DECIMAL(10,2) NOT NULL,
    tokens BIGINT NOT NULL,
    status TINYINT DEFAULT 1 COMMENT '0失败 1成功',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_user (user_id)
);

CREATE TABLE IF NOT EXISTS t_notification (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    type VARCHAR(30) NOT NULL COMMENT 'audit/recharge/violation/system',
    title VARCHAR(100) NOT NULL,
    content TEXT,
    is_read TINYINT DEFAULT 0,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_user_read (user_id, is_read)
);

CREATE TABLE IF NOT EXISTS t_violation_log (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    session_id BIGINT,
    message_id BIGINT,
    content TEXT,
    words VARCHAR(500),
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_user (user_id)
);

CREATE TABLE IF NOT EXISTS t_sensitive_word (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    word VARCHAR(50) NOT NULL UNIQUE,
    level TINYINT DEFAULT 1 COMMENT '1�?2�?3�?,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS t_model_config (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    provider VARCHAR(50) NOT NULL,
    api_key VARCHAR(255),
    base_url VARCHAR(255),
    model_name VARCHAR(100),
    context_window_size INT DEFAULT 20,
    temperature_default DECIMAL(3,2) DEFAULT 0.70,
    enabled TINYINT DEFAULT 1,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS t_message_collect (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    message_id BIGINT NOT NULL,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_user_message (user_id, message_id)
);

-- 叙事作品：把一场角色扮演对话的完整记录改写成小说/剧本/大纲
CREATE TABLE IF NOT EXISTS t_narrative_work (
    id                    BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id               BIGINT NOT NULL,
    session_id            BIGINT COMMENT '来源会话，作品保存后与源会话解绑，仅作追溯',
    character_id          BIGINT,
    title                 VARCHAR(100) NOT NULL,
    summary               VARCHAR(500) COMMENT '社区列表页摘要',
    content               MEDIUMTEXT NOT NULL,
    form_type             VARCHAR(20) DEFAULT 'novel' COMMENT 'novel小说 script剧本 outline大纲',
    style                 VARCHAR(20) DEFAULT 'literary',
    length                VARCHAR(20) DEFAULT 'medium',
    extra_instruction     VARCHAR(500) COMMENT '用户补充要求',
    word_count            INT DEFAULT 0,
    source_message_count  INT DEFAULT 0,
    tokens                INT DEFAULT 0,
    status                TINYINT DEFAULT 0 COMMENT '0草稿 1已发布',
    audit_status          TINYINT DEFAULT 0 COMMENT '0待审核 1通过 2未通过，仅发布后有效',
    created_at            DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at            DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_user (user_id),
    INDEX idx_session (session_id),
    INDEX idx_publish (status, audit_status)
);

-- 管理员密�?admin123 �?BCrypt
INSERT INTO t_user (username, password, nickname, token_balance, member_level, role, status)
VALUES ('admin', '$2a$10$C0rAhWRDSuRttGXAqa51K.im.cBpUP.wWPFe2tUijhunYIj8Ds9Zi', '管理�?, 999999, 3, 1, 1)
ON DUPLICATE KEY UPDATE username = username;

INSERT INTO t_category (name, sort) VALUES
('古风仙侠', 1),
('现代都市', 2),
('科幻未来', 3),
('西幻异世界', 4),
('治愈陪伴', 5),
('学习助手', 6),
('游戏动漫', 7),
('历史名人', 8)
ON DUPLICATE KEY UPDATE sort = VALUES(sort);

INSERT INTO t_tag (name, color) VALUES
('温柔', '#E6A23C'),
('傲娇', '#F56C6C'),
('理�?, '#409EFF'),
('幽默', '#67C23A'),
('神秘', '#909399'),
('热血', '#FF6B35')
ON DUPLICATE KEY UPDATE name = VALUES(name);

INSERT INTO t_sensitive_word (word, level) VALUES
('违禁词示�?, 1),
('暴力极端', 2),
('色情内容', 3)
ON DUPLICATE KEY UPDATE word = VALUES(word);

INSERT INTO t_model_config (provider, api_key, base_url, model_name, context_window_size, enabled)
VALUES ('deepseek', '', 'https://api.deepseek.com/v1/chat/completions', 'deepseek-chat', 20, 1);