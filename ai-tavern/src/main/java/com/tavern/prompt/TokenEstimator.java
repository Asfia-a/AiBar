package com.tavern.prompt;

/**
 * Token 估算。
 *
 * <p>没有本地分词器，用「字符数 / 系数」近似：
 * 中文一个汉字约 1~2 token，取 1.5 字/token 作折中；
 * 英文按 4 字符/token，这里统一按字符折算，误差在可接受范围内。
 * 真正的 token 数以模型返回的 usage 为准，这里只用于预算分配。
 */
public final class TokenEstimator {

    /** 中文等宽字符折算系数：1.5 个字符 ≈ 1 token */
    public static final double CHARS_PER_TOKEN = 1.5;

    private TokenEstimator() {
    }

    public static int estimate(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        return (int) Math.ceil(text.length() / CHARS_PER_TOKEN);
    }

    public static int estimateAll(Iterable<String> texts) {
        int sum = 0;
        for (String t : texts) {
            sum += estimate(t);
        }
        return sum;
    }
}
