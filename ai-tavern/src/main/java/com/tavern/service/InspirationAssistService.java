package com.tavern.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tavern.common.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class InspirationAssistService {

    private final AiModelService aiModelService;
    private final ObjectMapper objectMapper;

    public Map<String, Object> polish(Map<String, Object> req) {
        String text = str(req.get("text"));
        if (!StringUtils.hasText(text)) {
            throw new BusinessException("请先写下要润色的灵感");
        }
        String title = str(req.get("title"));
        String tag = str(req.get("tag"));
        String style = str(req.get("style"));
        String intensity = str(req.get("intensity"));
        String length = str(req.get("length"));
        String language = str(req.get("language"));
        String customRules = str(req.get("customRules"));
        boolean keepMeaning = req.get("keepMeaning") == null || Boolean.TRUE.equals(req.get("keepMeaning"));

        String styleHint = switch (style == null ? "" : style) {
            case "colloquial" -> "口语化、自然、像随口说出来的念头";
            case "script" -> "剧本/对白感，适合角色扮演场景";
            case "poetic" -> "诗意、意象密集、留白感";
            case "literary" -> "文艺叙事，画面感强但不堆砌辞藻";
            default -> "贴合灵感记录场景，自然有质感";
        };
        String intensityHint = switch (intensity == null ? "" : intensity) {
            case "light" -> "轻度润色：只修通顺与节奏，尽量少改措辞";
            case "strong" -> "深度润色：可重构句式与意象，但不要写成完全无关的新文";
            default -> "中度润色：提升表达力与氛围，保留核心信息";
        };
        String lengthHint = switch (length == null ? "" : length) {
            case "shorten" -> "篇幅略压缩，去掉冗余";
            case "expand" -> "可适度扩写细节与氛围（约 1.3～1.8 倍）";
            default -> "篇幅与原文大致相当";
        };
        String meaningHint = keepMeaning
                ? "必须保留原意与关键事实/设定，不得篡改世界观硬设定。"
                : "允许在主题方向上做创意延伸，但仍要看得出与原文的关系。";
        String langHint = StringUtils.hasText(language)
                ? "输出语言：" + language.trim() + "。"
                : "输出语言与原文一致。";

        String system = """
                你是角色扮演创作助手，专门润色「灵感碎片」（人设、对白、剧情、世界观笔记）。
                只输出 JSON，不要 markdown：
                { "title": "可选，若用户提供标题可微调；否则可空字符串", "polished": "润色后的正文" }
                要求：
                - 文风：%s
                - 强度：%s
                - 篇幅：%s
                - %s
                - %s
                - 不要解释过程；不要加「润色后：」之类前缀。
                """.formatted(styleHint, intensityHint, lengthHint, meaningHint, langHint);

        if (StringUtils.hasText(customRules)) {
            system = system + "\n用户额外配置要求：\n" + customRules.trim();
        }

        StringBuilder user = new StringBuilder();
        if (StringUtils.hasText(tag)) user.append("分类：").append(tag).append('\n');
        if (StringUtils.hasText(title)) user.append("标题：").append(title).append('\n');
        user.append("原文：\n").append(text.trim());

        String raw = aiModelService.chatCompletion(
                List.of(
                        Map.of("role", "system", "content", system),
                        Map.of("role", "user", "content", user.toString())
                ),
                0.8,
                2200
        );

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("title", title);
        out.put("polished", text);
        try {
            String json = raw == null ? "" : raw.trim();
            if (json.startsWith("```")) {
                int s = json.indexOf('{');
                int e = json.lastIndexOf('}');
                if (s >= 0 && e > s) json = json.substring(s, e + 1);
            }
            var node = objectMapper.readTree(json);
            if (node.hasNonNull("polished") && StringUtils.hasText(node.get("polished").asText())) {
                out.put("polished", node.get("polished").asText().trim());
            } else if (StringUtils.hasText(raw)) {
                out.put("polished", raw.trim());
            }
            if (node.hasNonNull("title") && StringUtils.hasText(node.get("title").asText())) {
                out.put("title", node.get("title").asText().trim());
            }
        } catch (Exception e) {
            if (StringUtils.hasText(raw)) {
                out.put("polished", raw.trim());
            }
        }
        return out;
    }

    private static String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }
}
