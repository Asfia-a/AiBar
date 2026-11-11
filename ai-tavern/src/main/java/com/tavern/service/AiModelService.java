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
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.stream.Collectors;

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

    /**
     * 聊天页可选模型（claudex.org 网关当前开放的一批）。
     * id 必须与网关 /chat/completions 的 model 字段一致；label 给前端展示。
     */
    public static final List<Map<String, String>> CHAT_MODELS = List.of(
            Map.of("id", "deepseek-v4.1-flash", "label", "DeepSeek V4.1 Flash",
                    "description", "默认推荐，更快更省"),
            Map.of("id", "deepseek-v4-flash", "label", "DeepSeek V4 Flash",
                    "description", "上一代 Flash"),
            Map.of("id", "deepseek-v4-pro", "label", "DeepSeek V4 Pro",
                    "description", "更强能力，适合复杂剧情"),
            Map.of("id", "deepseek-v3.2", "label", "DeepSeek V3.2",
                    "description", "经典 DeepSeek"),
            Map.of("id", "gemini-3-flash", "label", "Gemini 3 Flash",
                    "description", "Google，速度快"),
            Map.of("id", "gemini-3.8-flash", "label", "Gemini 3.8 Flash",
                    "description", "Google 新版 Flash"),
            Map.of("id", "gemini-4-argon", "label", "Gemini 4 Argon",
                    "description", "Google 更强一档"),
            Map.of("id", "claude-opus-4-6", "label", "Claude Opus 4.6",
                    "description", "Anthropic，文笔细"),
            Map.of("id", "glm-5.3", "label", "GLM 5.3",
                    "description", "智谱"),
            Map.of("id", "glm-5.3-flash", "label", "GLM 5.3 Flash",
                    "description", "智谱快速版"),
            Map.of("id", "kimi-k3", "label", "Kimi K3",
                    "description", "月之暗面")
    );

    private static final Set<String> CHAT_MODEL_IDS = CHAT_MODELS.stream()
            .map(m -> m.get("id"))
            .collect(Collectors.toUnmodifiableSet());

    public record StreamResult(String content, String reasoning, int totalTokens) {
        public StreamResult(String content, int totalTokens) {
            this(content, null, totalTokens);
        }
    }

    public record ImageResult(String url, String b64Json) {}

    /** 流式片段：正文与思考链分开推，前端各自渲染 */
    public record StreamChunk(Kind kind, String text) {
        public enum Kind { CONTENT, REASONING }
        public static StreamChunk content(String text) {
            return new StreamChunk(Kind.CONTENT, text);
        }
        public static StreamChunk reasoning(String text) {
            return new StreamChunk(Kind.REASONING, text);
        }
    }

    /**
     * 一次对话请求的采样参数。
     *
     * <p>哪些字段可空，就表示「这个开关没开」：不下发，交给服务端默认值。
     * 这样老角色卡（只有 temperature/top_p/max_tokens）的行为完全不变。
     *
     * <p>{@code thinkingEnabled}：DeepSeek 思考模式；null=不传（走服务端默认），
     * true/false 显式下发 {@code thinking.type=enabled/disabled}。
     */
    public record ChatParams(double temperature, double topP, int maxTokens,
                             Integer topK, Double frequencyPenalty, Double presencePenalty,
                             Double repetitionPenalty, List<String> stop,
                             Boolean thinkingEnabled) {
        public ChatParams {
            stop = stop == null ? List.of() : List.copyOf(stop);
        }

        public ChatParams(double temperature, double topP, int maxTokens,
                          Integer topK, Double frequencyPenalty, Double presencePenalty,
                          Double repetitionPenalty, List<String> stop) {
            this(temperature, topP, maxTokens, topK, frequencyPenalty, presencePenalty,
                    repetitionPenalty, stop, null);
        }

        /** 工坊、叙事等内部调用：只用三家都认的三个参数 */
        public static ChatParams simple(double temperature, double topP, int maxTokens) {
            return new ChatParams(temperature, topP, maxTokens, null, null, null, null, List.of(), null);
        }

        public ChatParams withThinking(Boolean enabled) {
            return new ChatParams(temperature, topP, maxTokens, topK, frequencyPenalty,
                    presencePenalty, repetitionPenalty, stop, enabled);
        }
    }

    /** 聊天页可选模型列表（不含任何密钥） */
    public List<Map<String, String>> listChatModels() {
        return CHAT_MODELS;
    }

    public String defaultChatModel() {
        return normalizeModelName(defaultModelName);
    }

    /** 只接受白名单模型；非法值回落默认 */
    public String normalizeModelName(String raw) {
        if (StringUtils.hasText(raw) && CHAT_MODEL_IDS.contains(raw.trim())) {
            return raw.trim();
        }
        if (StringUtils.hasText(defaultModelName) && CHAT_MODEL_IDS.contains(defaultModelName.trim())) {
            return defaultModelName.trim();
        }
        return CHAT_MODELS.get(0).get("id");
    }

    /** 非流式对话，供工坊生成角色卡等 */
    public String chatCompletion(List<Map<String, String>> messages, double temperature, int maxTokens) {
        return chatCompletion(messages, temperature, maxTokens, null);
    }

    /** @param modelOverride 可选；空则用配置默认模型 */
    public String chatCompletion(List<Map<String, String>> messages, double temperature, int maxTokens,
                                 String modelOverride) {
        ModelConfig cfg = resolveConfig(modelOverride);
        String apiKey = cfg.getApiKey();
        if (!StringUtils.hasText(apiKey)) {
            throw new BusinessException(ErrorCode.AI_ERROR.getCode(), "未配置对话 API Key");
        }
        try {
            Map<String, Object> body = new java.util.LinkedHashMap<>();
            body.put("model", cfg.getModelName());
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
                                   Consumer<StreamChunk> onChunk) {
        return streamChat(messages, params, null, onChunk);
    }

    /**
     * @param modelOverride 会话选定的模型；空则用配置默认值。API Key / Base URL 始终来自配置文件。
     */
    public StreamResult streamChat(List<Map<String, String>> messages, ChatParams params,
                                   String modelOverride, Consumer<StreamChunk> onChunk) {
        ModelConfig cfg = resolveConfig(modelOverride);
        String apiKey = cfg.getApiKey();
        if (!StringUtils.hasText(apiKey)) {
            // 无 Key 时返回模拟流，便于本地联调
            String mock = "（演示模式）你好，我是酒馆里的角色。请配置 AI_API_KEY 以启用真实对话。";
            for (String part : mock.split("(?<=。)")) {
                if (!part.isBlank()) {
                    onChunk.accept(StreamChunk.content(part));
                    try { Thread.sleep(40); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
                }
            }
            return new StreamResult(mock, 50);
        }

        try {
            Map<String, Object> body = buildRequestBody(cfg, messages, defaultChatModel(), params,
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
            StringBuilder reasoning = new StringBuilder();
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
                            applyDelta(choices.get(0).get("delta"), full, reasoning, onChunk);
                        }
                    }
                }
            }
            if (totalTokens <= 0) {
                totalTokens = Math.max(20, (full.length() + reasoning.length()) / 2);
            }
            String reasoningText = reasoning.isEmpty() ? null : reasoning.toString();
            return new StreamResult(full.toString(), reasoningText, totalTokens);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("AI stream error", e);
            throw new BusinessException(ErrorCode.AI_ERROR.getCode(), e.getMessage());
        }
    }

    /**
     * 解析流式 delta：DeepSeek 思考链走 {@code reasoning_content}，正文走 {@code content}。
     * 拆成静态方法方便单测，不连网。
     */
    static void applyDelta(JsonNode delta, StringBuilder content, StringBuilder reasoning,
                           Consumer<StreamChunk> onChunk) {
        if (delta == null || delta.isNull()) {
            return;
        }
        JsonNode reasoningNode = delta.get("reasoning_content");
        if (reasoningNode == null || reasoningNode.isNull()) {
            reasoningNode = delta.get("reasoning");
        }
        if (reasoningNode != null && !reasoningNode.isNull()) {
            String text = reasoningNode.asText();
            if (StringUtils.hasText(text)) {
                reasoning.append(text);
                if (onChunk != null) {
                    onChunk.accept(StreamChunk.reasoning(text));
                }
            }
        }
        JsonNode contentNode = delta.get("content");
        if (contentNode != null && !contentNode.isNull()) {
            String text = contentNode.asText();
            if (StringUtils.hasText(text)) {
                content.append(text);
                if (onChunk != null) {
                    onChunk.accept(StreamChunk.content(text));
                }
            }
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
        // 思考模式只对 DeepSeek 下发；Gemini/Claude/GLM 等遇不认识字段可能直接 400
        String modelName = String.valueOf(body.get("model"));
        if (params.thinkingEnabled() != null && supportsThinking(modelName)) {
            Map<String, String> thinking = new LinkedHashMap<>();
            thinking.put("type", params.thinkingEnabled() ? "enabled" : "disabled");
            body.put("thinking", thinking);
        }
        body.put("stream", stream);
        return body;
    }

    /** claudex 上 DeepSeek 系才认 thinking / reasoning_content */
    static boolean supportsThinking(String modelName) {
        if (!StringUtils.hasText(modelName)) {
            return false;
        }
        return modelName.toLowerCase(Locale.ROOT).startsWith("deepseek");
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
        return isSelfHosted(cfg.getBaseUrl());
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
        return resolveConfig(null);
    }

    /**
     * 解析实际调用用的模型配置。
     *
     * <p>API Key 与 Base URL <b>始终</b>来自 application.yml / 环境变量，
     * 故意不读库表里的密钥，避免管理端改密、前端泄露。模型名优先用会话覆盖。
     */
    public ModelConfig resolveConfig(String modelOverride) {
        ModelConfig cfg = modelConfigMapper.findEnabled();
        if (cfg == null) {
            cfg = new ModelConfig();
            cfg.setStatus(1);
        }
        cfg.setApiKey(defaultApiKey);
        cfg.setBaseUrl(defaultBaseUrl);
        // 模型名：会话覆盖 > 配置默认；不再读库表里的旧全局模型
        cfg.setModelName(normalizeModelName(modelOverride));
        if (cfg.getContextWindowSize() == null) {
            cfg.setContextWindowSize(20);
        }
        return cfg;
    }

    /** 管理端只读：脱敏后的配置摘要（密钥已迁到配置文件，不再支持在线改） */
    public ModelConfig getConfig() {
        ModelConfig cfg = new ModelConfig();
        cfg.setProvider("deepseek");
        cfg.setBaseUrl(defaultBaseUrl);
        cfg.setModelName(defaultChatModel());
        cfg.setApiKey(maskKey(defaultApiKey));
        cfg.setStatus(1);
        return cfg;
    }

    private static String maskKey(String key) {
        if (!StringUtils.hasText(key)) {
            return "";
        }
        if (key.length() <= 8) {
            return "****";
        }
        return key.substring(0, 4) + "****";
    }

    /** @deprecated 密钥与模型选择已迁出管理端；保留方法以免旧调用方编译失败 */
    @Deprecated
    public void updateConfig(ModelConfig config) {
        throw new BusinessException("模型密钥请改 application.yml / 环境变量；聊天模型请在用户端会话里选择");
    }
}
