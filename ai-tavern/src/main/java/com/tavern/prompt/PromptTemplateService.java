package com.tavern.prompt;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 提示词模板。
 *
 * <p>把提示词从 Java 字符串里挪到 {@code classpath:/prompts/*.md}，
 * 好处是改措辞不用重新编译，也方便非程序员参与调词。
 * 模板里的 <code>{{char}}</code> / <code>{{user}}</code> 等占位符由 {@link #render} 替换。
 */
@Slf4j
@Service
public class PromptTemplateService {

    /** 模板缺失时的兜底，保证不会因为文件丢失导致对话直接失败 */
    private static final Map<String, String> DEFAULTS = Map.ofEntries(
            Map.entry("main", "你正在扮演「{{char}}」，与「{{user}}」进行角色扮演。\n"
                    + "保持角色身份与语气，不要替「{{user}}」行动或说话。\n\n{{persona_block}}"),
            Map.entry("character", "## 身份\n{{description}}\n## 性格\n{{personality}}\n"
                    + "## 背景\n{{background}}\n## 示例对话\n{{example}}\n## 说话风格\n{{tags}}"),
            Map.entry("worldinfo_format", "【{{title}}】\n{{content}}"),
            Map.entry("author_note_format", "【作者注释】\n{{content}}"),
            Map.entry("user_persona_format", "【关于「{{user}}」】\n{{content}}"),
            Map.entry("memory_summary_format", "【此前剧情摘要】\n{{content}}"),
            Map.entry("memory_summarize", "把旧摘要与新对话合并成不超过 {{max_chars}} 字的中文剧情摘要。"
                    + "\n角色：{{char}}\n用户：{{user}}\n【旧摘要】\n{{old_summary}}\n【新增对话】\n{{new_dialogue}}"),
            Map.entry("post_history_format", "【收尾指令】\n{{content}}")
    );

    private final Map<String, String> cache = new ConcurrentHashMap<>();

    public String get(String name) {
        return cache.computeIfAbsent(name, n -> {
            ClassPathResource res = new ClassPathResource("prompts/" + n + ".md");
            if (!res.exists()) {
                return DEFAULTS.getOrDefault(n, "");
            }
            try (InputStream in = res.getInputStream()) {
                return new String(in.readAllBytes(), StandardCharsets.UTF_8).trim();
            } catch (IOException e) {
                log.warn("读取提示词模板 {} 失败，使用内置兜底: {}", n, e.getMessage());
                return DEFAULTS.getOrDefault(n, "");
            }
        });
    }

    /** 按 {{name}} 替换占位符；未提供的占位符原样保留，便于排查 */
    public String render(String template, Map<String, String> vars) {
        if (template == null || template.isEmpty()) {
            return "";
        }
        String out = template;
        for (Map.Entry<String, String> e : vars.entrySet()) {
            out = out.replace("{{" + e.getKey() + "}}", e.getValue() == null ? "" : e.getValue());
        }
        return out;
    }

    public String render(String templateName, Map<String, String> vars, boolean isName) {
        return render(get(templateName), vars);
    }

    /** 便捷构造器：常用变量一次性塞进去 */
    public static Map<String, String> vars(String charName, String userName) {
        Map<String, String> m = new HashMap<>();
        m.put("char", charName == null ? "角色" : charName);
        m.put("user", userName == null ? "你" : userName);
        return m;
    }
}
