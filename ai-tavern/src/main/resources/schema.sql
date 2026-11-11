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
    member_level      TINYINT DEFAULT 0 COMMENT '0普通 1白银 2黄金 3钻石',
    daily_free_token  BIGINT DEFAULT 2000,
    last_check_in     DATE,
    consecutive_days  INT DEFAULT 0,
    points      INT DEFAULT 0,
    role        TINYINT DEFAULT 0 COMMENT '0用户 1管理员',
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
    post_history    TEXT COMMENT 'Post-History Instructions，收尾强指令；空=不追加',
    greeting        TEXT,
    creator_id      BIGINT NOT NULL,
    category_id     BIGINT,
    status          TINYINT DEFAULT 0 COMMENT '0草稿 1审核中 2已上架 3已下架 4驳回',
    source_id       BIGINT COMMENT '复刻来源，NULL=原创',
    version         INT DEFAULT 1,
    temperature     DECIMAL(3,2) DEFAULT 0.70,
    top_p           DECIMAL(3,2) DEFAULT 0.90,
    max_tokens      INT DEFAULT 500,
    top_k               INT COMMENT 'top_k 采样；NULL=不下发（仅自建推理服务支持）',
    frequency_penalty   DECIMAL(3,2) COMMENT '-2~2，按词频惩罚，NULL=不下发',
    presence_penalty    DECIMAL(3,2) COMMENT '-2~2，鼓励新话题，NULL=不下发',
    repetition_penalty  DECIMAL(4,2) COMMENT '0.8~1.5，非 OpenAI 标准，NULL=不下发',
    stop_words          VARCHAR(255) COMMENT '停止词，多个用 | 分隔',
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
    prompt_snapshot MEDIUMTEXT COMMENT '会话创建时冻结的角色人设JSON，创作者改卡不影响已有对话',
    created_at      DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_user_character (user_id, character_id)
);

CREATE TABLE IF NOT EXISTS t_chat_message (
    id          BIGINT PRIMARY KEY AUTO_INCREMENT,
    session_id  BIGINT NOT NULL,
    role        VARCHAR(10) NOT NULL COMMENT 'user/assistant/system',
    content     TEXT NOT NULL,
    reasoning   MEDIUMTEXT NULL COMMENT '思考过程（DeepSeek reasoning_content）',
    tokens      INT DEFAULT 0,
    swipe_count INT DEFAULT 1 COMMENT '共几条备选回复（含当前这条）',
    swipe_index INT DEFAULT 0 COMMENT '当前展示第几条，从 0 开始',
    created_at  DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_session (session_id)
);

-- 同一轮回复的多个版本：滑动手势在 t_message_swipe 里左右切
CREATE TABLE IF NOT EXISTS t_message_swipe (
    id          BIGINT PRIMARY KEY AUTO_INCREMENT,
    message_id  BIGINT NOT NULL,
    idx         INT NOT NULL DEFAULT 0 COMMENT '版本序号，0 是第一次生成',
    content     TEXT NOT NULL,
    reasoning   MEDIUMTEXT NULL COMMENT '该版本的思考过程',
    tokens      INT DEFAULT 0,
    created_at  DATETIME DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_message_idx (message_id, idx),
    INDEX idx_message (message_id)
);

CREATE TABLE IF NOT EXISTS t_token_log (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id         BIGINT NOT NULL,
    session_id      BIGINT,
    tokens_used     INT NOT NULL,
    balance_before  BIGINT NOT NULL,
    balance_after   BIGINT NOT NULL,
    type            TINYINT DEFAULT 1 COMMENT '1消费 2充值 3签到 4积分兑换',
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
    order_no VARCHAR(64) NOT NULL UNIQUE,
    user_id BIGINT NOT NULL,
    amount DECIMAL(10,2) NOT NULL,
    token_count BIGINT NOT NULL,
    pay_type VARCHAR(20) DEFAULT 'mock',
    status TINYINT DEFAULT 1 COMMENT '0待支付/失败 1成功',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    paid_at DATETIME NULL,
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
    level TINYINT DEFAULT 1 COMMENT '1低 2中 3高',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS t_model_config (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    provider VARCHAR(50) NOT NULL,
    api_key VARCHAR(255),
    base_url VARCHAR(255),
    model_name VARCHAR(100),
    context_window_size INT DEFAULT 20,
    temperature DECIMAL(3,2) DEFAULT 0.70,
    top_p DECIMAL(3,2) DEFAULT 0.90,
    max_tokens INT DEFAULT 500,
    status TINYINT DEFAULT 1 COMMENT '1启用 0停用',
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

-- 世界书（对标 SillyTavern World Info）：只在对话命中关键词时才把设定插进 prompt
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

-- 作者注释是会话级设置：插在历史对话里的强指令
ALTER TABLE t_chat_session
    ADD COLUMN author_note  TEXT COMMENT '作者注释内容',
    ADD COLUMN an_depth     INT DEFAULT 0 COMMENT '0=历史末尾，N=倒数第 N 条之前',
    ADD COLUMN an_frequency INT DEFAULT 1 COMMENT '每 N 条用户消息插一次，0=不插';

-- 会话级模型选择（密钥不进库，只在 application.yml / 环境变量）
ALTER TABLE t_chat_session
    ADD COLUMN model_name VARCHAR(64) NULL COMMENT '本会话使用的模型名，空则用配置默认值';

-- 用户人设 + 长对话记忆摘要
ALTER TABLE t_chat_session
    ADD COLUMN persona_name     VARCHAR(64)  NULL COMMENT '用户人设称呼，覆盖 {{user}}',
    ADD COLUMN persona_desc     TEXT         NULL COMMENT '用户人设描述，注入提示词',
    ADD COLUMN memory_summary   MEDIUMTEXT   NULL COMMENT '被裁掉的早期剧情摘要',
    ADD COLUMN memory_until_id  BIGINT       NULL COMMENT '摘要已覆盖到的消息 id（含）',
    ADD COLUMN memory_enabled   TINYINT      NOT NULL DEFAULT 1 COMMENT '1=自动摘要，0=关闭';

-- 管理员密码 admin123 的 BCrypt
INSERT INTO t_user (username, password, nickname, token_balance, member_level, role, status)
VALUES ('admin', '$2a$10$C0rAhWRDSuRttGXAqa51K.im.cBpUP.wWPFe2tUijhunYIj8Ds9Zi', '管理员', 999999, 3, 1, 1)
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
('理性', '#409EFF'),
('幽默', '#67C23A'),
('神秘', '#909399'),
('热血', '#FF6B35')
ON DUPLICATE KEY UPDATE name = VALUES(name);

INSERT INTO t_sensitive_word (word, level) VALUES
('违禁词示例', 1),
('暴力极端', 2),
('色情内容', 3)
ON DUPLICATE KEY UPDATE word = VALUES(word);

INSERT INTO t_model_config (provider, api_key, base_url, model_name, context_window_size, enabled)
VALUES ('deepseek', '', 'https://api.deepseek.com/v1/chat/completions', 'deepseek-chat', 20, 1);