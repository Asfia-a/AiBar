package com.tavern.service;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.Map;

/**
 * 中文敏感词归一化。
 * <p>
 * 直接用 {@code content.contains(word)} 只能挡住原样输入，实际绕过手段有五类：
 * 全角/零宽伪装、繁简混写、拆字、插字、同音替换。本类的统一思路是
 * <b>把待检文本压到统一形式，让词库去匹配它</b>，而不是在原文里逐词试探。
 * <p>
 * 归一化四步（前 3 步消除视觉伪装，第 4 步交给匹配器处理间隔）：
 * <ol>
 *   <li>{@link #toHalfWidth} 全角转半角</li>
 *   <li>{@link #stripInvisible} 去零宽字符与各类空白</li>
 *   <li>{@link #toSimplified} 繁体转简体</li>
 * </ol>
 * 拆字与插字不在这里处理，而由 {@link SensitiveWordMatcher} 的间隔匹配完成——
 * 因为「违.禁.词」和「违禁词」归一化后分别是 4 字和 3 字，
 * 只有匹配器允许跳过 1～2 个无关字才能同时命中二者。
 * 早前尝试过把文本膨胀成二元组再 contains，实测会漏掉插字，
 * 已废弃该方案。
 */
@Component
public class SensitiveNormalizer {

    /** 常见繁体字 → 简体字。词库规模有限，这里覆盖违规场景高频字，不引第三方依赖。 */
    private static final Map<Character, Character> T2S = new HashMap<>();

    static {
        T2S.put('愛', '爱'); T2S.put('變', '变'); T2S.put('邊', '边'); T2S.put('辦', '办');
        T2S.put('華', '华'); T2S.put('畫', '画'); T2S.put('話', '话'); T2S.put('謊', '谎');
        T2S.put('買', '买'); T2S.put('賣', '卖'); T2S.put('錢', '钱'); T2S.put('殺', '杀');
        T2S.put('傷', '伤'); T2S.put('藥', '药'); T2S.put('賭', '赌'); T2S.put('謠', '谣');
        T2S.put('婦', '妇'); T2S.put('姦', '奸'); T2S.put('亂', '乱'); T2S.put('倫', '伦');
        T2S.put('傭', '佣'); T2S.put('價', '价'); T2S.put('開', '开'); T2S.put('關', '关');
        T2S.put('權', '权'); T2S.put('軍', '军'); T2S.put('醜', '丑'); T2S.put('骯', '肮');
        T2S.put('臟', '脏'); T2S.put('惡', '恶'); T2S.put('虧', '亏'); T2S.put('騙', '骗');
        T2S.put('搶', '抢'); T2S.put('賤', '贱'); T2S.put('嚴', '严'); T2S.put('罰', '罚');
        T2S.put('機', '机'); T2S.put('車', '车'); T2S.put('馬', '马'); T2S.put('鳥', '鸟');
        T2S.put('魚', '鱼'); T2S.put('龍', '龙'); T2S.put('鳳', '凤'); T2S.put('龜', '龟');
        T2S.put('這', '这'); T2S.put('違', '违'); T2S.put('詞', '词'); T2S.put('價', '价');
        T2S.put('貴', '贵'); T2S.put('賓', '宾'); T2S.put('館', '馆'); T2S.put('業', '业');
        T2S.put('務', '务'); T2S.put('產', '产'); T2S.put('資', '资'); T2S.put('訊', '讯');
        T2S.put('號', '号'); T2S.put('們', '们'); T2S.put('個', '个'); T2S.put('來', '来');
        T2S.put('對', '对'); T2S.put('時', '时'); T2S.put('說', '说'); T2S.put('沒', '没');
        T2S.put('後', '后'); T2S.put('從', '从'); T2S.put('會', '会'); T2S.put('學', '学');
        T2S.put('國', '国'); T2S.put('點', '点'); T2S.put('過', '过'); T2S.put('還', '还');
    }

    /** 零宽空格、零宽连接符、左右方向标记、BOM */
    private static final String INVISIBLE = "‌‍‎‏﻿";

    /** 主入口：待检文本与词库词条都必须过这一道，保证两侧形式一致 */
    public String normalize(String raw) {
        if (!StringUtils.hasText(raw)) {
            return "";
        }
        return toSimplified(stripInvisible(toHalfWidth(raw)));
    }

    /** 第 1 步：全角转半角。ASCII 可打印区间统一减 0xFEE0，全角空格转普通空格。 */
    public String toHalfWidth(String s) {
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '　') {
                sb.append(' ');
            } else if (c >= '！' && c <= '～') {
                sb.append((char) (c - 0xFEE0));
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    /** 第 2 步：去零宽字符与全部空白。分词绕过常靠插入不可见字符打断匹配。 */
    public String stripInvisible(String s) {
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (INVISIBLE.indexOf(c) >= 0 || Character.isWhitespace(c)) {
                continue;
            }
            sb.append(c);
        }
        return sb.toString();
    }

    /** 第 3 步：繁体转简体。逐字查表，未命中的原样保留。 */
    public String toSimplified(String s) {
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            Character t = T2S.get(c);
            sb.append(t != null ? t : c);
        }
        return sb.toString();
    }

    /** 供词库预处理：词条与待检文本走同一条管线 */
    public String normalizeWord(String word) {
        return normalize(word);
    }

    /**
     * 字母数字串。仅保留 ASCII 字母与数字。
     * <p>
     * 局限要如实写进论文：这里不做汉字转拼音（需词典），
     * 所以它能挡的是「英文敏感词的同音形近替换」（如 bαd 之于 bad），
     * 挡不住「用拼音首字母书写的汉语」（如 wc 之于「我操」）。
     */
    public String alphanumericOnly(String s) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (Character.isLetterOrDigit(c)) {
                sb.append(Character.toLowerCase(c));
            }
        }
        return sb.toString();
    }
}
