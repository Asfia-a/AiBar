package com.tavern.prompt;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("提示词模板：外置加载与占位符替换")
class PromptTemplateServiceTest {

    private final PromptTemplateService service = new PromptTemplateService();

    @Test
    @DisplayName("主提示从 classpath 读出来，且带有占位符")
    void loadsFromClasspath() {
        String main = service.get("main");
        assertFalse(main.isBlank());
        assertTrue(main.contains("{{char}}"));
        assertTrue(main.contains("{{persona_block}}"));
    }

    @Test
    @DisplayName("render 替换 {{name}} 占位符，未提供的保留")
    void renderReplaces() {
        String out = service.render("你好 {{user}}，我是 {{char}}。{{unknown}}",
                Map.of("user", "小董", "char", "林晚晴"));
        assertEquals("你好 小董，我是 林晚晴。{{unknown}}", out);
    }

    @Test
    @DisplayName("模板不存在时返回空串，不抛异常")
    void missingTemplate() {
        assertEquals("", service.get("definitely-not-exists"));
    }
}
