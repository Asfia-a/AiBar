-- 助手回复的思考链（DeepSeek reasoning_content），与正文分开存，方便折叠展示
ALTER TABLE t_chat_message
    ADD COLUMN reasoning MEDIUMTEXT NULL COMMENT '思考过程，空表示无或不展示' AFTER content;

ALTER TABLE t_message_swipe
    ADD COLUMN reasoning MEDIUMTEXT NULL COMMENT '该版本的思考过程' AFTER content;
