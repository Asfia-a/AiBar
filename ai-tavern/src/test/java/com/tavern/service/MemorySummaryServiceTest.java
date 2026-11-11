package com.tavern.service;

import com.tavern.entity.ChatMessage;
import com.tavern.prompt.PromptTemplateService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("记忆摘要：触发条件、文本压缩与兜底")
class MemorySummaryServiceTest {

    private ChatMessage msg(long id, String role, String content) {
        ChatMessage m = new ChatMessage();
        m.setId(id);
        m.setRole(role);
        m.setContent(content);
        return m;
    }

    @Test
    @DisplayName("没有未覆盖消息时不刷新")
    void noRefreshWhenNothingUncovered() {
        assertFalse(MemorySummaryService.needsRefresh(10L, 20L, 0));
    }

    @Test
    @DisplayName("从未摘要且窗口前有消息时需要刷新")
    void refreshWhenNeverSummarized() {
        assertTrue(MemorySummaryService.needsRefresh(null, 50L, 12));
    }

    @Test
    @DisplayName("已覆盖点贴着窗口边缘时不再刷新")
    void noRefreshAtWindowEdge() {
        // until=19, oldestInWindow=20 → 开区间 (19,20) 为空
        assertFalse(MemorySummaryService.needsRefresh(19L, 20L, 0));
    }

    @Test
    @DisplayName("对白文本超长时保留较新的部分")
    void transcriptKeepsNewer() {
        List<ChatMessage> list = new ArrayList<>();
        list.add(msg(1, "user", "AAAA"));
        list.add(msg(2, "assistant", "BBBB"));
        list.add(msg(3, "user", "CCCC"));
        String t = MemorySummaryService.buildTranscript(list, 20);
        assertTrue(t.contains("CCCC") || t.contains("BBBB"));
        assertTrue(t.length() <= 20);
    }

    @Test
    @DisplayName("本地兜底：旧摘要与新对白合并，超长保尾")
    void fallbackMergesAndClamps() {
        String out = MemorySummaryService.fallbackCompress("旧事", "新事一二三四", 8);
        assertEquals(8, out.length());
        assertTrue(out.endsWith("三四") || out.contains("新事"));
    }

    @Test
    @DisplayName("模型成功时用模型输出；并剥掉 markdown 围栏")
    void compressUsesModelAndStripsFence() {
        AiModelService ai = mock(AiModelService.class);
        when(ai.chatCompletion(anyList(), anyDouble(), anyInt()))
                .thenReturn("```\n旅人与老板娘约定三日后再见。\n```");
        MemorySummaryService svc = new MemorySummaryService(ai, new PromptTemplateService());
        String out = svc.compress(null, List.of(msg(1, "user", "我三日后再来")), "林晚晴", "旅人");
        assertEquals("旅人与老板娘约定三日后再见。", out);
    }

    @Test
    @DisplayName("模型失败时回落本地兜底，不抛异常")
    void compressFallsBackOnError() {
        AiModelService ai = mock(AiModelService.class);
        when(ai.chatCompletion(anyList(), anyDouble(), anyInt()))
                .thenThrow(new RuntimeException("no key"));
        MemorySummaryService svc = new MemorySummaryService(ai, new PromptTemplateService());
        String out = svc.compress("旧摘要", List.of(msg(1, "user", "新对白")), "林晚晴", "旅人");
        assertTrue(out.contains("旧摘要"));
        assertTrue(out.contains("新对白"));
    }

    @Test
    @DisplayName("stripFence 处理无围栏文本原样返回")
    void stripFencePlain() {
        assertEquals("你好", MemorySummaryService.stripFence("你好"));
    }
}
