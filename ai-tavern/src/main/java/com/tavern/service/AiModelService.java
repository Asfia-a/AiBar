package com.tavern.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tavern.common.BusinessException;
import com.tavern.common.ErrorCode;
import com.tavern.entity.ModelConfig;
import com.tavern.mapper.ModelConfigMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiModelService {

    private final ModelConfigMapper modelConfigMapper;
    private final ObjectMapper objectMapper;

    @Value("${ai.model.api-key:}")
    private String defaultApiKey;

    @Value("${ai.model.base-url}")
    private String defaultBaseUrl;

    @Value("${ai.model.model-name}")
    private String defaultModelName;

    private final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(180, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build();

    @Value("${ai.image.api-key:}")
    private String imageApiKey;

    @Value("${ai.image.base-url:}")
    private String imageBaseUrl;

    @Value("${ai.image.model-name:gpt-image-2}")
    private String imageModelName;

    @Value("${ai.image.size:1024x1024}")
    private String imageSize;

    /**
     * 非标准采样参数（top_k / repetition_penalty）的下发策略：
     * <ul>
     *   <li>{@code auto}（默认）：只有 base_url 指向本机/局域网的自建推理服务才下发。
     *       OpenAI、DeepSeek 这类云端网关会把不认识的字段当成错误参数，直接用 400 拒掉；</li>
     *   <li>{@code always}：总是下发，适用于自建网关；</li>
     *   <li>{@code never}：从不下发。</li>
     * </ul>
     */
    @Value("${ai.model.extended-params:auto}")
    private String extendedParams;

    public record StreamResult(String content, int totalTokens) {}

    public record ImageResult(String url, String b64Json) {}

    /**
     * 一次对话请求的采样参数。
     *
     * <p>哪些字段可空，就表示「这个开关没开」：不下发，交给服务端默认值。
     * 这样老角色卡（只有 temperature/top_p/max_tokens）的行为完全不变。
     */
    public record ChatParams(double temperature, double topP, int maxTokens,
                             Integer topK, Double frequencyPenalty, Double presencePenalty,
                             Double repetitionPenalty, List<String> stop) {
        public ChatParams {
            stop = stop == null ? List.of() : List.copyOf(stop);
        }

        /** 工坊、叙事等内部调用：只用三家都认的三个参数 */
        public static ChatParams simple(double temperature, double topP, int maxTokens) {
            return new ChatParams(temperature, topP, maxTokens, null, null, null, null, List.of());
        }
    }

    /** 非流式对话，供工坊生成角色卡等 */
    public String chatCompletion(List<Map<String, String>> messages, double temperature, int maxTokens) {
        ModelConfig cfg = resolveConfig();
        String apiKey = StringUtils.hasText(cfg.getApiKey()) ? cfg.getApiKey() : defaultApiKey;
        if (!StringUtils.hasText(apiKey)) {
            throw new BusinessException(ErrorCode.AI_ERROR.getCode(), "未配置对话 API Key");
        }
        try {
            Map<String, Object> body = new java.util.LinkedHashMap<>();
            body.put("model", cfg.getModelName() != null ? cfg.getModelName() : defaultModelName);
            body.put("messages", messages);
            body.put("temperature", temperature);
            body.put("max_tokens", maxTokens);
            body.put("stream", false);
            String json = objectMapper.writeValueAsString(body);
            String url = chatCompletionsUrl(cfg.getBaseUrl());
            Request request = new Request.Builder()
                    .url(url)
                    .addHeader("Authorization", "Bearer " + apiKey)
                    .addHeader("Content-Type", "application/json")
                    .post(RequestBody.create(json, MediaType.parse("application/json")))
                    .build();
            try (Response response = client.newCall(request).execute()) {
                String resp = response.body() != null ? response.body().string() : "";
                if (!response.isSuccessful()) {
                    throw new BusinessException(ErrorCode.AI_ERROR.getCode(), "AI 调用失败: " + resp);
                }
                JsonNode root = objectMapper.readTree(resp);
                // 部分网关包一层 { success, data: { choices... } }
                if (root.has("data") && root.get("data").isObject() && root.get("data").has("choices")) {
                    root = root.get("data");
                }
                JsonNode message = root.path("choices").path(0).path("message");
                JsonNode content = message.path("content");
                if (content.isMissingNode() || content.isNull() || !StringUtils.hasText(content.asText())) {
                    // 兼容 content 为数组或多模态块
                    if (content.isArray() && content.size() > 0) {
                        StringBuilder sb = new StringBuilder();
                        for (JsonNode part : content) {
                            if (part.has("text")) sb.append(part.get("text").asText());
                            else if (part.isTextual()) sb.append(part.asText());
                        }
                        if (StringUtils.hasText(sb.toString())) {
                            return sb.toString();
                        }
                    }
                    throw new BusinessException(ErrorCode.AI_ERROR.getCode(), "AI 返回空内容");
                }
                return content.asText();
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("AI chat error", e);
            throw new BusinessException(ErrorCode.AI_ERROR.getCode(), e.getMessage());
        }
    }

    /** OpenAI 兼容生图：/images/generations */
    public ImageResult generateImage(String prompt) {
        String apiKey = StringUtils.hasText(imageApiKey) ? imageApiKey : defaultApiKey;
        if (!StringUtils.hasText(apiKey)) {
            throw new BusinessException(ErrorCode.AI_ERROR.getCode(), "未配置生图 API Key");
        }
        String base = StringUtils.hasText(imageBaseUrl) ? imageBaseUrl : defaultBaseUrl;
        String url = base.endsWith("/") ? base + "images/generations" : base + "/images/generations";
        try {
            Map<String, Object> body = new java.util.LinkedHashMap<>();
            body.put("model", imageModelName);
            body.put("prompt", prompt);
            body.put("n", 1);
            body.put("size", imageSize);
            String json = objectMapper.writeValueAsString(body);
            Request request = new Request.Builder()
                    .url(url)
                    .addHeader("Authorization", "Bearer " + apiKey)
                    .addHeader("Content-Type", "application/json")
                    .post(RequestBody.create(json, MediaType.parse("application/json")))
                    .build();
            try (Response response = client.newCall(request).execute()) {
                String resp = response.body() != null ? response.body().string() : "";
                if (!response.isSuccessful()) {
                    throw new BusinessException(ErrorCode.AI_ERROR.getCode(), "生图失败: " + resp);
                }
                JsonNode root = objectMapper.readTree(resp);
                if (root.has("data") && root.get("data").isObject() && !root.get("data").isArray()
                        && root.get("data").has("data")) {
                    root = root.get("data");
                }
                JsonNode data0 = root.path("data").path(0);
                String imgUrl = data0.hasNonNull("url") ? data0.get("url").asText() : null;
                String b64 = data0.hasNonNull("b64_json") ? data0.get("b64_json").asText() : null;
                if (!StringUtils.hasText(imgUrl) && !StringUtils.hasText(b64)) {
                    throw new BusinessException(ErrorCode.AI_ERROR.getCode(), "生图结果为空: " + resp);
                }
                return new ImageResult(imgUrl, b64);
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("AI image error", e);
            throw new BusinessException(ErrorCode.AI_ERROR.getCode(), e.getMessage());
        }
    }

    private String chatCompletionsUrl(String baseUrl) {
        String url = StringUtils.hasText(baseUrl) ? baseUrl : defaultBaseUrl;
        if (!url.contains("/chat/completions")) {
            url = url.endsWith("/") ? url + "chat/completions" : url + "/chat/completions";
        }
        return url;
    }

    public StreamResult streamChat(List<Map<String, String>> messages, ChatParams params,
                                   Consumer<String> onDelta) {
        ModelConfig cfg = resolveConfig();
        String apiKey = StringUtils.hasText(cfg.getApiKey()) ? cfg.getApiKey() : defaultApiKey;
        if (!StringUtils.hasText(apiKey)) {
            // 无 Key 时返回模拟流，便于本地联调
            String mock = "（演示模式）你好，我是酒馆里的角色。请配置 AI_API_KEY 以启用真实对话。";
            for (String part : mock.split("(?<=。)")) {
                if (!part.isBlank()) {
                    onDelta.accept(part);
                    try { Thread.sleep(40); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
                }
            }
            return new StreamResult(mock, 50);
        }

        try {
            Map<String, Object> body = buildRequestBody(cfg, messages, defaultModelName, params,
                    true, allowExtendedParams(cfg));
            String json = objectMapper.writeValueAsString(body);
            String url = chatCompletionsUrl(cfg.getBaseUrl());
            Request request = new Request.Builder()
                    .url(url)
                    .addHeader("Authorization", "Bearer " + apiKey)
                    .addHeader("Content-Type", "application/json")
                    .post(RequestBody.create(json, MediaType.parse("application/json")))
                    .build();

            StringBuilder full = new StringBuilder();
            int totalTokens = 0;
            try (Response response = client.newCall(request).execute()) {
                if (!response.isSuccessful() || response.body() == null) {
                    String err = response.body() != null ? response.body().string() : ("HTTP " + response.code());
                    throw new BusinessException(ErrorCode.AI_ERROR.getCode(), "AI 调用失败: " + err);
                }
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(response.body().byteStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        if (!line.startsWith("data:")) continue;
                        String data = line.substring(5).trim();
                        if ("[DONE]".equals(data)) break;
                        JsonNode node = objectMapper.readTree(data);
                        JsonNode usage = node.get("usage");
                        if (usage != null && usage.has("total_tokens")) {
                            totalTokens = usage.get("total_tokens").asInt();
                        }
                        JsonNode choices = node.get("choices");
                        if (choices != null && choices.isArray() && !choices.isEmpty()) {
                            JsonNode delta = choices.get(0).get("delta");
                            if (delta != null && delta.has("content") && !delta.get("content").isNull()) {
                                String content = delta.get("content").asText();
                                full.append(content);
                                onDelta.accept(content);
                            }
                        }
                    }
                }
            }
            if (totalTokens <= 0) {
                totalTokens = Math.max(20, full.length() / 2);
            }
            return new StreamResult(full.toString(), totalTokens);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("AI stream error", e);
            throw new BusinessException(ErrorCode.AI_ERROR.getCode(), e.getMessage());
        }
    }

    /**
     * 组装 /chat/completions 的请求体。
     *
     * <p>拆成静态方法是为了能单测：不连网、不读配置，只验证「该带什么、不该带什么」。
     * 这里的取舍很关键——云端网关对不认识的参数是直接报错，不是忽略，
     * 所以 top_k / repetition_penalty 只在自建推理服务上才发。
     */
    static Map<String, Object> buildRequestBody(ModelConfig cfg,
                                                List<Map<String, String>> messages,
                                                String fallbackModel,
                                                ChatParams params,
                                                boolean stream,
                                                boolean allowExtended) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", StringUtils.hasText(cfg.getModelName()) ? cfg.getModelName() : fallbackModel);
        body.put("messages", messages);
        body.put("temperature", params.temperature());
        body.put("top_p", params.topP());
        body.put("max_tokens", params.maxTokens());
        if (params.frequencyPenalty() != null) {
            body.put("frequency_penalty", params.frequencyPenalty());
        }
        if (params.presencePenalty() != null) {
            body.put("presence_penalty", params.presencePenalty());
        }
        if (!params.stop().isEmpty()) {
            body.put("stop", params.stop());
        }
        if (allowExtended) {
            if (params.topK() != null) {
                body.put("top_k", params.topK());
            }
            if (params.repetitionPenalty() != null) {
                body.put("repetition_penalty", params.repetitionPenalty());
            }
        }
        body.put("stream", stream);
        return body;
    }

    /** 停止词串（| 分隔）→ 列表；OpenAI 兼容接口最多接受 4 条 */
    public static List<String> parseStopWords(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        return Arrays.stream(raw.split("\\|"))
                // 输入框里没法直接敲换行，约定写 \n 表示换行（"\\n用户:" 这类停止词很常见）
                .map(s -> s.trim().replace("\\n", "\n").replace("\\t", "\t"))
                .filter(s -> !s.isEmpty())
                .limit(4)
                .toList();
    }

    private boolean allowExtendedParams(ModelConfig cfg) {
        if ("always".equalsIgnoreCase(extendedParams)) {
            return true;
        }
        if ("never".equalsIgnoreCase(extendedParams)) {
            return false;
        }
        String baseUrl = StringUtils.hasText(cfg.getBaseUrl()) ? cfg.getBaseUrl() : defaultBaseUrl;
        return isSelfHosted(baseUrl);
    }

    /** 本机 / 局域网地址 = 多半是 Ollama、vLLM、LM Studio，这些才认 top_k 与 repetition_penalty */
    static boolean isSelfHosted(String baseUrl) {
        if (!StringUtils.hasText(baseUrl)) {
            return false;
        }
        String lower = baseUrl.toLowerCase(Locale.ROOT);
        String host;
        try {
            host = URI.create(lower).getHost();
        } catch (IllegalArgumentException e) {
            host = null;
        }
        if (host == null) {
            host = lower;
        }
        return host.equals("localhost") || host.equals("127.0.0.1") || host.equals("0.0.0.0")
                || host.equals("::1") || host.equals("[::1]")
                || host.startsWith("192.168.") || host.startsWith("10.")
                || host.matches("172\\.(1[6-9]|2[0-9]|3[01])\\..*")
                || host.endsWith(".local");
    }

    public ModelConfig resolveConfig() {
        ModelConfig cfg = modelConfigMapper.findEnabled();
        if (cfg == null) {
            cfg = new ModelConfig();
            cfg.setStatus(1);
        }
        // 库表空字段回落到 application.yml，避免旧种子数据盖住本地配置
        if (!StringUtils.hasText(cfg.getApiKey())) {
            cfg.setApiKey(defaultApiKey);
        }
        if (!StringUtils.hasText(cfg.getBaseUrl())) {
            cfg.setBaseUrl(defaultBaseUrl);
        }
        if (!StringUtils.hasText(cfg.getModelName())) {
            cfg.setModelName(defaultModelName);
        }
        if (cfg.getContextWindowSize() == null) {
            cfg.setContextWindowSize(20);
        }
        return cfg;
    }

    public ModelConfig getConfig() {
        ModelConfig cfg = modelConfigMapper.findOne();
        if (cfg != null && cfg.getApiKey() != null && cfg.getApiKey().length() > 8) {
            cfg.setApiKey(cfg.getApiKey().substring(0, 4) + "****");
        }
        return cfg;
    }

    public void updateConfig(ModelConfig config) {
        ModelConfig existing = modelConfigMapper.findOne();
        if (existing == null) {
            throw new BusinessException("模型配置不存在");
        }
        if (config.getApiKey() != null && config.getApiKey().contains("****")) {
            config.setApiKey(existing.getApiKey());
        }
        config.setId(existing.getId());
        modelConfigMapper.update(config);
    }
}
