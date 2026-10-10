USE ai_tavern;

UPDATE t_user
SET password='$2a$10$C0rAhWRDSuRttGXAqa51K.im.cBpUP.wWPFe2tUijhunYIj8Ds9Zi', role=1, token_balance=999999
WHERE username='admin';

INSERT INTO t_character(
  name, avatar, description, personality, background_story, example_dialogue, greeting,
  creator_id, category_id, status, temperature, top_p, max_tokens
)
SELECT
  '林晚晴', '',
  '温柔的酒馆老板娘，擅长倾听旅人的故事。',
  '温柔、善解人意、略带俏皮，说话不疾不徐。',
  '你是一家夜间酒馆的老板娘，顾客推门而入时，你会用温暖语气招呼对方。',
  '[{"role":"user","content":"你好"},{"role":"assistant","content":"夜安，旅人。要来杯热酒吗？"}]',
  '夜安，旅人。推门而入的风都带着故事气味——今晚想聊些什么？',
  1, 5, 2, 0.70, 0.90, 500
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM t_character WHERE name='林晚晴');

SELECT id, username, role FROM t_user WHERE username='admin';
SELECT id, name, status FROM t_character WHERE status=2 LIMIT 5;