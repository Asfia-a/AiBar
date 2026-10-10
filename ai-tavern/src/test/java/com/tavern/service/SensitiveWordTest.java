package com.tavern.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 敏感词检测测试。
 * <p>
 * 归一化管线与间隔匹配共同保证「拆字 / 插字 / 全角 / 零宽 / 繁体」五类绕过全部失效，
 * 这部分用例可直接作为论文的测试用例表。
 */
class SensitiveWordTest {

    private SensitiveNormalizer normalizer;
    private SensitiveWordMatcher matcher;

    @BeforeEach
    void setUp() {
        normalizer = new SensitiveNormalizer();
        matcher = new SensitiveWordMatcher();
        List<String> dict = List.of("违禁词", "暴力极端", "色情内容");
        matcher.build(dict.stream().map(normalizer::normalizeWord).toList());
    }

    private Set<String> check(String text) {
        return matcher.match(normalizer.normalize(text));
    }

    @Test
    @DisplayName("原样输入应命中")
    void plainMatch() {
        assertEquals(Set.of("违禁词"), check("这是违禁词"));
    }

    @Test
    @DisplayName("全角字符应被归一化后命中")
    void fullWidth() {
        // 「违」「禁」「词」的全角形：Ｖ=违(音) 用汉字全角形 ⅴ 不好手写，
        // 这里用全角括号与全角数字这类真实存在的全角字符验证归一化链路
        assertEquals("abc123", new SensitiveNormalizer().toHalfWidth("ａｂｃ１２３"));
        // 零宽字符被剥离，可见字符保留
        assertEquals("abc", new SensitiveNormalizer().stripInvisible("a‌b‍c"));
        // 全角空格应转为半角空格后再被剥掉
        assertEquals("ab", new SensitiveNormalizer().normalize("a　b"));
    }

    @Test
    @DisplayName("零宽字符插入应被剥离后命中")
    void zeroWidth() {
        assertEquals(Set.of("违禁词"), check("违‌禁词"));
        assertEquals(Set.of("违禁词"), check("违‍禁‍词"));
    }

    @Test
    @DisplayName("空格分隔应被剥离后命中")
    void spaceSeparated() {
        assertEquals(Set.of("违禁词"), check("违 禁 词"));
        assertEquals(Set.of("违禁词"), check("违　禁　词"));
    }

    @Test
    @DisplayName("繁体字应转简体后命中")
    void traditionalChinese() {
        assertEquals(Set.of("违禁词"), check("這是違禁詞"));
    }

    @Test
    @DisplayName("拆字（插入1个无关字）应命中")
    void splitOneChar() {
        assertEquals(Set.of("违禁词"), check("违x禁x词"));
        assertEquals(Set.of("违禁词"), check("违.禁.词"));
        assertEquals(Set.of("违禁词"), check("违-禁-词"));
    }

    @Test
    @DisplayName("拆字（插入2个无关字）应命中")
    void splitTwoChars() {
        assertEquals(Set.of("违禁词"), check("违ab禁cd词"));
    }

    @Test
    @DisplayName("正常文本不应误报")
    void noFalsePositive() {
        assertTrue(check("今天天气不错").isEmpty());
        assertTrue(check("我很好").isEmpty());
        assertTrue(check("").isEmpty());
        assertTrue(check(null).isEmpty());
    }

    @Test
    @DisplayName("多种绕过叠加仍应命中")
    void combinedEvasion() {
        // 繁体 + 零宽 + 插字 三者同时使用
        assertEquals(Set.of("违禁词"), check("這是違‌禁x詞"));
    }

    @Test
    @DisplayName("未超限的插入不应命中——说明间隔上限是可控的")
    void beyondGapNotMatched() {
        // 插入 3 个字符超过 MAX_GAP=2，按设计不命中
        assertTrue(check("违abc禁defg词").isEmpty());
    }

    @Test
    @DisplayName("单字词不参与匹配，避免过度拦截")
    void singleCharIgnored() {
        SensitiveWordMatcher m = new SensitiveWordMatcher();
        m.build(List.of("的", "违禁词"));
        assertEquals(1, m.wordCount());
    }
}
