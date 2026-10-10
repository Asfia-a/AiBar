package com.tavern.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tavern.common.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class WorldTreeAssistService {

    private static final Set<String> NODE_TYPES = Set.of(
            "world", "era", "region", "faction", "lore", "figure"
    );

    private final AiModelService aiModelService;
    private final ObjectMapper objectMapper;

    /**
     * 根据用户自然语言一键生成世界树。
     * language 为空时跟随用户想法所用语言（中文想法→中文树）。
     */
    public Map<String, Object> generateTree(String idea, String language, int depthHint) {
        if (!StringUtils.hasText(idea)) {
            throw new BusinessException("请描述你想要的世界观");
        }
        int depth = depthHint > 0 ? Math.min(depthHint, 5) : 3;
        String langRule = StringUtils.hasText(language)
                ? "全部节点名称与设定摘要必须使用「" + language.trim() + "」书写。"
                : "全部节点名称与设定摘要必须与用户想法使用同一种语言（用户用中文则输出中文，用英文则输出英文）。";

        String system = """
                你是世界观架构师，负责搭建「世界树」层级结构。只输出 JSON，不要 markdown。
                根结构：
                {
                  "tree": [
                    {
                      "label": "世界名",
                      "type": "world",
                      "summary": "核心法则与氛围",
                      "children": [
                        {
                          "label": "时代名",
                          "type": "era",
                          "summary": "...",
                          "children": [
                            { "label": "地域", "type": "region", "summary": "...", "children": [] },
                            { "label": "势力", "type": "faction", "summary": "...", "children": [] },
                            { "label": "法则/传说", "type": "lore", "summary": "...", "children": [] },
                            { "label": "人物线索", "type": "figure", "summary": "...", "children": [] }
                          ]
                        }
                      ]
                    }
                  ]
                }
                type 只能是：world / era / region / faction / lore / figure。
                要求：1 个 world 根；至少 1 个 era；era 下覆盖地域/势力/法则/人物线索；总层深约 %d；节点 8～20 个，摘要具体有画面感。
                %s
                """.formatted(depth, langRule);

        String raw = aiModelService.chatCompletion(
                List.of(
                        Map.of("role", "system", "content", system),
                        Map.of("role", "user", "content", "用户想法：\n" + idea.trim())
                ),
                0.85,
                3500
        );

        List<Map<String, Object>> tree = parseTree(raw);
        assignIds(tree);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("tree", tree);
        result.put("contextText", flattenContext(tree));
        result.put("language", language);
        return result;
    }

    /** 为已有节点生成子节点 */
    public Map<String, Object> expandNode(Map<String, Object> node, String hint, String language) {
        if (node == null || node.isEmpty()) {
            throw new BusinessException("请提供要扩展的节点");
        }
        String langRule = StringUtils.hasText(language)
                ? "使用「" + language.trim() + "」书写。"
                : "与父节点语言一致。";
        String system = """
                你是世界观架构师。根据父节点生成 2～5 个子节点，只输出 JSON：
                { "children": [ { "label":"...", "type":"region|faction|lore|figure|era", "summary":"...", "children":[] } ] }
                type 只能是 world/era/region/faction/lore/figure。%s
                """.formatted(langRule);
        String user = "父节点：\n" + toJsonSafe(node)
                + (StringUtils.hasText(hint) ? "\n扩展方向：" + hint.trim() : "");
        String raw = aiModelService.chatCompletion(
                List.of(
                        Map.of("role", "system", "content", system),
                        Map.of("role", "user", "content", user)
                ),
                0.85,
                2000
        );
        List<Map<String, Object>> children = parseChildren(raw);
        assignIds(children);
        return Map.of("children", children);
    }

    private List<Map<String, Object>> parseTree(String raw) {
        JsonNode root = readJson(raw);
        JsonNode treeNode = root.has("tree") ? root.get("tree") : root;
        if (treeNode != null && treeNode.isObject() && treeNode.has("label")) {
            return List.of(normalizeNode(treeNode));
        }
        if (treeNode == null || !treeNode.isArray() || treeNode.isEmpty()) {
            throw new BusinessException("世界树生成失败：结构无效");
        }
        List<Map<String, Object>> list = new ArrayList<>();
        for (JsonNode n : treeNode) {
            list.add(normalizeNode(n));
        }
        return list;
    }

    private List<Map<String, Object>> parseChildren(String raw) {
        JsonNode root = readJson(raw);
        JsonNode arr = root.has("children") ? root.get("children") : root;
        List<Map<String, Object>> list = new ArrayList<>();
        if (arr != null && arr.isArray()) {
            for (JsonNode n : arr) {
                list.add(normalizeNode(n));
            }
        }
        return list;
    }

    private JsonNode readJson(String raw) {
        String text = raw == null ? "" : raw.trim();
        if (text.startsWith("```")) {
            int start = text.indexOf('{');
            int end = text.lastIndexOf('}');
            if (start >= 0 && end > start) {
                text = text.substring(start, end + 1);
            }
        }
        try {
            return objectMapper.readTree(text);
        } catch (Exception e) {
            log.warn("世界树 JSON 解析失败: {}", e.getMessage());
            throw new BusinessException("世界树生成结果解析失败");
        }
    }

    private Map<String, Object> normalizeNode(JsonNode n) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("label", textOr(n, "label", "未命名"));
        String type = textOr(n, "type", "lore").toLowerCase(Locale.ROOT);
        if (!NODE_TYPES.contains(type)) {
            type = "lore";
        }
        map.put("type", type);
        map.put("summary", textOr(n, "summary", ""));
        List<Map<String, Object>> children = new ArrayList<>();
        if (n.has("children") && n.get("children").isArray()) {
            for (JsonNode c : n.get("children")) {
                children.add(normalizeNode(c));
            }
        }
        map.put("children", children);
        return map;
    }

    private void assignIds(List<Map<String, Object>> nodes) {
        if (nodes == null) return;
        for (Map<String, Object> n : nodes) {
            n.put("id", "w-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12));
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> children = (List<Map<String, Object>>) n.get("children");
            assignIds(children);
        }
    }

    private String flattenContext(List<Map<String, Object>> tree) {
        StringBuilder sb = new StringBuilder();
        walkFlat(tree, 0, sb);
        return sb.toString().trim();
    }

    @SuppressWarnings("unchecked")
    private void walkFlat(List<Map<String, Object>> nodes, int depth, StringBuilder sb) {
        if (nodes == null) return;
        for (Map<String, Object> n : nodes) {
            String type = Objects.toString(n.get("type"), "");
            String label = Objects.toString(n.get("label"), "");
            String summary = Objects.toString(n.get("summary"), "");
            sb.append("  ".repeat(depth)).append('[').append(type).append("] ").append(label);
            if (StringUtils.hasText(summary)) {
                sb.append(" — ").append(summary);
            }
            sb.append('\n');
            Object ch = n.get("children");
            if (ch instanceof List<?> list) {
                walkFlat((List<Map<String, Object>>) list, depth + 1, sb);
            }
        }
    }

    private String toJsonSafe(Object o) {
        try {
            return objectMapper.writeValueAsString(o);
        } catch (Exception e) {
            return String.valueOf(o);
        }
    }

    private static String textOr(JsonNode node, String field, String fallback) {
        JsonNode n = node.get(field);
        if (n == null || n.isNull()) return fallback;
        String t = n.asText();
        return t == null || t.isBlank() ? fallback : t.trim();
    }
}
