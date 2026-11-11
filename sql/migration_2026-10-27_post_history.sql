-- Post-History Instructions：拼在全部对话历史之后的收尾强指令（对标 SillyTavern 同名配置）
-- MySQL 8.0 不支持 ADD COLUMN IF NOT EXISTS，重复执行报 1060 可忽略
-- 只加列不动数据；schema.sql 与 ai-tavern/src/main/resources/schema.sql 已同步

ALTER TABLE t_character
    ADD COLUMN post_history TEXT COMMENT 'Post-History Instructions，收尾强指令；空=不追加' AFTER example_dialogue;
