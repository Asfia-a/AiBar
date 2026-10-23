package com.tavern.service;

import com.tavern.entity.ModelConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 采样参数的下发规则。
 *
 * <p>这组测试盯的是一个很容易翻车的地方：云端网关对不认识的字段是**报错**而不是忽略，
 * 所以「什么时候不该发 top_k」和「什么时候该发」同样重要。纯静态方法，不连网不连库。
 */
@DisplayName("采样参数：请求体组装与能力探测")
class AiModelServiceSamplingTest {

    private final List<Map<String, String>> messages = List.of(Map.of("role", "user", "content", "你好"));

    private ModelConfig cfg(String baseUrl) {
        ModelConfig cfg = new ModelConfig();
        cfg.setModelName("test-model");
        cfg.setBaseUrl(baseUrl);
        return cfg;
    }

    private AiModelService.ChatParams fullParams() {
        return new AiModelService.ChatParams(
                0.8, 0.9, 600,
                40, 0.3, 0.2, 1.1,
                AiModelService.parseStopWords("用户:|User:"));
    }

    @Test
    @DisplayName("标准参数始终下发，停止词按数组传")
    void standardParamsAlwaysSent() {
        Map<String, Object> body = AiModelService.buildRequestBody(
                cfg("https://api.deepseek.com/v1"), messages, "fallback",
                fullParams(), true, false);

        assertEquals("test-model", body.get("model"));
        assertEquals(0.8, body.get("temperature"));
        assertEquals(0.9, body.get("top_p"));
        assertEquals(600, body.get("max_tokens"));
        assertEquals(0.3, body.get("frequency_penalty"));
        assertEquals(0.2, body.get("presence_penalty"));
        assertEquals(List.of("用户:", "User:"), body.get("stop"));
        assertEquals(true, body.get("stream"));
    }

    @Test
    @DisplayName("云端网关：不发 top_k / repetition_penalty，避免被 400 拒掉")
    void extendedParamsSkippedOnCloud() {
        Map<String, Object> body = AiModelService.buildRequestBody(
                cfg("https://api.deepseek.com/v1"), messages, "fallback",
                fullParams(), true, false);

        assertFalse(body.containsKey("top_k"));
        assertFalse(body.containsKey("repetition_penalty"));
    }

    @Test
    @DisplayName("自建推理服务：top_k 与重复惩罚才下发")
    void extendedParamsSentOnSelfHosted() {
        Map<String, Object> body = AiModelService.buildRequestBody(
                cfg("http://127.0.0.1:11434/v1"), messages, "fallback",
                fullParams(), true, true);

        assertEquals(40, body.get("top_k"));
        assertEquals(1.1, body.get("repetition_penalty"));
    }

    @Test
    @DisplayName("参数没开（null）就不进请求体，老角色卡行为不变")
    void nullParamsOmitted() {
        Map<String, Object> body = AiModelService.buildRequestBody(
                cfg("http://127.0.0.1:11434/v1"), messages, "fallback",
                AiModelService.ChatParams.simple(0.7, 0.9, 500), false, true);

        assertFalse(body.containsKey("top_k"));
        assertFalse(body.containsKey("repetition_penalty"));
        assertFalse(body.containsKey("frequency_penalty"));
        assertFalse(body.containsKey("presence_penalty"));
        assertFalse(body.containsKey("stop"));
        assertEquals(false, body.get("stream"));
    }

    @Test
    @DisplayName("停止词：| 分隔、去空、最多 4 条、\\n 还原成换行")
    void parseStopWords() {
        assertEquals(List.of("用户:", "User:"),
                AiModelService.parseStopWords(" 用户: | User: | "));
        assertEquals(List.of("\nAssistant:"),
                AiModelService.parseStopWords("\\nAssistant:"));
        assertEquals(4, AiModelService.parseStopWords("a|b|c|d|e|f").size());
        assertTrue(AiModelService.parseStopWords(null).isEmpty());
        assertTrue(AiModelService.parseStopWords("  ").isEmpty());
    }

    @Test
    @DisplayName("是否自建服务：本机与内网算，公网域名不算")
    void selfHostedDetection() {
        assertTrue(AiModelService.isSelfHosted("http://127.0.0.1:11434/v1"));
        assertTrue(AiModelService.isSelfHosted("http://localhost:8000/v1"));
        assertTrue(AiModelService.isSelfHosted("http://192.168.1.9:8000/v1"));
        assertTrue(AiModelService.isSelfHosted("http://10.0.0.5:8000/v1"));
        assertTrue(AiModelService.isSelfHosted("http://172.16.3.2:8000/v1"));
        assertFalse(AiModelService.isSelfHosted("https://api.deepseek.com/v1"));
        assertFalse(AiModelService.isSelfHosted("https://172.32.0.1/v1"));
        assertFalse(AiModelService.isSelfHosted(null));
    }
}
