package com.tavern.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tavern.common.BusinessException;
import com.tavern.dto.CharacterRequest;
import com.tavern.entity.Category;
import com.tavern.entity.CharacterCard;
import com.tavern.mapper.CategoryMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.Base64;

@Slf4j
@Service
@RequiredArgsConstructor
public class CharacterAssistService {

    private final AiModelService aiModelService;
    private final UploadService uploadService;
    private final CharacterService characterService;
    private final CategoryMapper categoryMapper;
    private final ObjectMapper objectMapper;

    private final OkHttpClient http = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .build();

    public Map<String, Object> generateCard(String idea, String worldContext, boolean withAvatar) {
        return generateCard(idea, worldContext, withAvatar, null);
    }

    public Map<String, Object> generateCard(String idea, String worldContext, boolean withAvatar,
                                            String modelName) {
        if (!StringUtils.hasText(idea)) {
            throw new BusinessException("请先描述你想要的角色");
        }
        String categoryOptions = categoryMapper.listAll().stream()
                .map(Category::getName)
                .filter(StringUtils::hasText)
                .reduce((a, b) -> a + "、" + b)
                .orElse("游戏动漫");
        String system = """
                你是 AI 角色酒馆的角色卡设计师。根据用户想法输出【仅 JSON，不要 markdown】，字段如下：
                {
                  "name": "角色名(≤50字)",
                  "description": "一句话简介(≤200字)",
                  "personality": "完整人设/性格/说话方式(可较长)",
                  "backgroundStory": "背景与世界观关系",
                  "greeting": "开场白(第一人称或剧本式)",
                  "exampleDialogue": "示例对话，可用 {{user}} 与角色名",
                  "category": "必须从下列分类中选一个（原样复制）：%s",
                  "tags": ["中文短标签1","中文短标签2","中文短标签3"],
                  "avatarPrompt": "英文立绘提示词，单人半身像，适合角色卡头像，无文字水印"
                }
                tags 用简洁中文性格/题材词（如温柔、神秘、傲娇），每个≤8字，3～6个。
                """.formatted(categoryOptions);
        StringBuilder user = new StringBuilder();
        user.append("用户想法：\n").append(idea.trim()).append("\n");
        if (StringUtils.hasText(worldContext)) {
            user.append("\n可参考的世界观/灵感：\n").append(worldContext.trim()).append("\n");
        }
        String raw = aiModelService.chatCompletion(
                List.of(
                        Map.of("role", "system", "content", system),
                        Map.of("role", "user", "content", user.toString())
                ),
                0.85,
                2500,
                modelName
        );
        Map<String, Object> card = parseCardJson(raw);
        Long categoryId = characterService.resolveCategoryId(null, Objects.toString(card.get("category"), null));
        card.put("categoryId", categoryId);
        if (withAvatar) {
            String prompt = Objects.toString(card.get("avatarPrompt"), "");
            if (!StringUtils.hasText(prompt)) {
                prompt = "anime character portrait, " + card.getOrDefault("name", "character")
                        + ", soft lighting, upper body, detailed face";
            }
            String avatarUrl = generateAndUploadAvatar(prompt);
            card.put("avatar", avatarUrl);
        }
        card.put("stCard", toStSpec(card));
        return card;
    }

    public Map<String, Object> generateAvatar(String prompt, String characterName) {
        if (!StringUtils.hasText(prompt)) {
            throw new BusinessException("请填写立绘提示词");
        }
        String p = prompt.trim();
        if (StringUtils.hasText(characterName)) {
            p = characterName + ", " + p;
        }
        String url = generateAndUploadAvatar(p);
        return Map.of("url", url, "prompt", p);
    }

    /** 生成卡并直接存为草稿角色 */
    public CharacterCard generateAndSave(Long userId, String idea, String worldContext, boolean withAvatar) {
        return generateAndSave(userId, idea, worldContext, withAvatar, null);
    }

    public CharacterCard generateAndSave(Long userId, String idea, String worldContext, boolean withAvatar,
                                         String modelName) {
        Map<String, Object> card = generateCard(idea, worldContext, withAvatar, modelName);
        CharacterRequest req = new CharacterRequest();
        req.setName(clip(Objects.toString(card.get("name"), "未命名"), 50));
        req.setAvatar(Objects.toString(card.get("avatar"), null));
        req.setDescription(Objects.toString(card.get("description"), ""));
        req.setPersonality(Objects.toString(card.get("personality"), ""));
        req.setBackgroundStory(Objects.toString(card.get("backgroundStory"), ""));
        req.setExampleDialogue(Objects.toString(card.get("exampleDialogue"), ""));
        req.setGreeting(Objects.toString(card.get("greeting"), ""));
        req.setCategoryName(Objects.toString(card.get("category"), defaultCategoryName()));
        List<String> tags = card.get("tags") instanceof List<?> list
                ? list.stream().map(String::valueOf).toList()
                : List.of();
        req.setTagNames(tags);
        req.setTemperature(new BigDecimal("0.70"));
        req.setTopP(new BigDecimal("0.90"));
        req.setMaxTokens(800);
        return characterService.create(userId, req);
    }

    private String generateAndUploadAvatar(String prompt) {
        AiModelService.ImageResult img = aiModelService.generateImage(prompt);
        byte[] bytes;
        String ext = ".png";
        if (StringUtils.hasText(img.b64Json())) {
            bytes = Base64.getDecoder().decode(img.b64Json());
        } else {
            bytes = download(img.url());
            if (img.url().toLowerCase().contains(".webp")) {
                ext = ".webp";
            } else if (img.url().toLowerCase().contains(".jpg") || img.url().toLowerCase().contains(".jpeg")) {
                ext = ".jpg";
            }
        }
        return uploadService.uploadBytes(bytes, ext);
    }

    private byte[] download(String url) {
        try {
            Request request = new Request.Builder().url(url).get().build();
            try (Response response = http.newCall(request).execute()) {
                if (!response.isSuccessful() || response.body() == null) {
                    throw new BusinessException("下载生图失败 HTTP " + response.code());
                }
                return response.body().bytes();
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException("下载生图失败: " + e.getMessage());
        }
    }

    private Map<String, Object> parseCardJson(String raw) {
        String text = raw == null ? "" : raw.trim();
        if (text.startsWith("```")) {
            int start = text.indexOf('{');
            int end = text.lastIndexOf('}');
            if (start >= 0 && end > start) {
                text = text.substring(start, end + 1);
            }
        }
        try {
            JsonNode node = objectMapper.readTree(text);
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("name", textOr(node, "name", "未命名角色"));
            map.put("description", textOr(node, "description", ""));
            map.put("personality", textOr(node, "personality", ""));
            map.put("backgroundStory", textOr(node, "backgroundStory", textOr(node, "scenario", "")));
            map.put("greeting", textOr(node, "greeting", textOr(node, "first_mes", "")));
            map.put("exampleDialogue", textOr(node, "exampleDialogue", textOr(node, "mes_example", "")));
            map.put("avatarPrompt", textOr(node, "avatarPrompt", ""));
            map.put("category", textOr(node, "category", defaultCategoryName()));
            List<String> tags = new ArrayList<>();
            if (node.has("tags") && node.get("tags").isArray()) {
                node.get("tags").forEach(t -> {
                    String n = t.asText();
                    if (n != null && !n.isBlank()) tags.add(n.trim());
                });
            }
            map.put("tags", tags);
            return map;
        } catch (Exception e) {
            log.warn("角色卡 JSON 解析失败，原文已降级: {}", e.getMessage());
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("name", "AI草稿");
            map.put("description", "由 AI 生成（解析失败时的降级稿）");
            map.put("personality", raw);
            map.put("backgroundStory", "");
            map.put("greeting", "你好。");
            map.put("exampleDialogue", "");
            map.put("avatarPrompt", "anime character portrait");
            map.put("category", defaultCategoryName());
            map.put("tags", List.of("AI生成"));
            return map;
        }
    }

    private String defaultCategoryName() {
        List<Category> list = categoryMapper.listAll();
        if (list == null || list.isEmpty()) return "游戏动漫";
        return list.stream()
                .map(Category::getName)
                .filter(n -> "游戏动漫".equals(n) || "治愈陪伴".equals(n) || "现代都市".equals(n))
                .findFirst()
                .orElse(list.get(0).getName());
    }

    private Map<String, Object> toStSpec(Map<String, Object> card) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("name", card.get("name"));
        data.put("description", card.get("personality"));
        data.put("personality", card.get("description"));
        data.put("scenario", card.get("backgroundStory"));
        data.put("first_mes", card.get("greeting"));
        data.put("mes_example", card.get("exampleDialogue"));
        data.put("creator_notes", card.get("description"));
        data.put("tags", card.get("tags"));
        data.put("avatar", card.get("avatar"));
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("spec", "chara_card_v2");
        root.put("spec_version", "2.0");
        root.put("data", data);
        return root;
    }

    private static String textOr(JsonNode node, String field, String fallback) {
        JsonNode n = node.get(field);
        if (n == null || n.isNull()) return fallback;
        String t = n.asText();
        return t == null || t.isBlank() ? fallback : t;
    }

    private static String clip(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max);
    }
}
