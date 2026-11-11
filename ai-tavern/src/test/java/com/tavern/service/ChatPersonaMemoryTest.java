package com.tavern.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tavern.common.BusinessException;
import com.tavern.config.BillingProperties;
import com.tavern.entity.CharacterCard;
import com.tavern.entity.ChatMessage;
import com.tavern.entity.ChatSession;
import com.tavern.entity.ModelConfig;
import com.tavern.mapper.CharacterMapper;
import com.tavern.mapper.ChatMessageMapper;
import com.tavern.mapper.ChatSessionMapper;
import com.tavern.mapper.MessageCollectMapper;
import com.tavern.mapper.MessageSwipeMapper;
import com.tavern.mapper.TagMapper;
import com.tavern.mapper.TokenLogMapper;
import com.tavern.mapper.UserMapper;
import com.tavern.mapper.WorldInfoMapper;
import com.tavern.prompt.PromptEngine;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("会话人设与记忆：读写与自动摘要触发")
class ChatPersonaMemoryTest {

    private final ChatSessionMapper sessionMapper = mock(ChatSessionMapper.class);
    private final ChatMessageMapper messageMapper = mock(ChatMessageMapper.class);
    private final MessageSwipeMapper swipeMapper = mock(MessageSwipeMapper.class);
    private final CharacterMapper characterMapper = mock(CharacterMapper.class);
    private final TagMapper tagMapper = mock(TagMapper.class);
    private final UserMapper userMapper = mock(UserMapper.class);
    private final TokenLogMapper tokenLogMapper = mock(TokenLogMapper.class);
    private final MessageCollectMapper collectMapper = mock(MessageCollectMapper.class);
    private final AiModelService aiModelService = mock(AiModelService.class);
    private final ContentAuditService contentAuditService = mock(ContentAuditService.class);
    private final RuntimeMetricsService metricsService = mock(RuntimeMetricsService.class);
    private final PromptEngine promptEngine = mock(PromptEngine.class);
    private final WorldInfoMapper worldInfoMapper = mock(WorldInfoMapper.class);
    private final MemorySummaryService memorySummaryService = mock(MemorySummaryService.class);

    private final ChatService service = new ChatService(
            sessionMapper, messageMapper, swipeMapper, characterMapper, tagMapper, userMapper,
            tokenLogMapper, collectMapper, aiModelService, contentAuditService,
            new BillingProperties(), new ObjectMapper(), metricsService, promptEngine, worldInfoMapper,
            memorySummaryService);

    private ChatSession session() {
        ChatSession s = new ChatSession();
        s.setId(2L);
        s.setUserId(7L);
        s.setCharacterId(9L);
        s.setMemoryEnabled(1);
        return s;
    }

    private void stubModelWindow(int size) {
        ModelConfig cfg = new ModelConfig();
        cfg.setContextWindowSize(size);
        when(aiModelService.resolveConfig()).thenReturn(cfg);
    }

    @Test
    @DisplayName("保存人设：写入后 active；称呼超长拒绝")
    void savePersona() {
        ChatSession before = session();
        ChatSession after = session();
        after.setPersonaName("沈渡");
        after.setPersonaDesc("游侠");
        when(sessionMapper.findById(2L)).thenReturn(before, after);
        when(sessionMapper.updatePersona(eq(2L), eq(7L), eq("沈渡"), eq("游侠"))).thenReturn(1);

        Map<String, Object> out = service.savePersona(7L, 2L, "沈渡", "游侠");
        assertEquals("沈渡", out.get("personaName"));
        assertTrue((Boolean) out.get("active"));

        when(sessionMapper.findById(2L)).thenReturn(session());
        assertThrows(BusinessException.class,
                () -> service.savePersona(7L, 2L, "x".repeat(40), "ok"));
    }

    @Test
    @DisplayName("关闭记忆：enabled=false 落库")
    void disableMemory() {
        ChatSession s = session();
        s.setMemorySummary("旧摘要");
        s.setMemoryUntilId(10L);
        ChatSession disabled = session();
        disabled.setMemorySummary("旧摘要");
        disabled.setMemoryUntilId(10L);
        disabled.setMemoryEnabled(0);
        when(sessionMapper.findById(2L)).thenReturn(s, disabled);
        when(sessionMapper.updateMemory(eq(2L), eq(7L), eq("旧摘要"), eq(10L), eq(0))).thenReturn(1);

        Map<String, Object> out = service.saveMemory(7L, 2L, null, false, false);
        verify(sessionMapper).updateMemory(2L, 7L, "旧摘要", 10L, 0);
        assertFalse((Boolean) out.get("enabled"));
    }

    @Test
    @DisplayName("立即刷新：压缩窗口外未覆盖消息并写回")
    void refreshMemoryCompressesUncovered() {
        stubModelWindow(20);
        ChatSession s = session();
        CharacterCard card = new CharacterCard();
        card.setId(9L);
        card.setName("林晚晴");
        when(characterMapper.findById(9L)).thenReturn(card);

        ChatMessage inWindow = new ChatMessage();
        inWindow.setId(30L);
        inWindow.setRole("user");
        inWindow.setContent("最近一句");
        when(messageMapper.listRecent(2L, 20)).thenReturn(List.of(inWindow));

        ChatMessage old1 = new ChatMessage();
        old1.setId(5L);
        old1.setRole("user");
        old1.setContent("很久以前");
        ChatMessage old2 = new ChatMessage();
        old2.setId(6L);
        old2.setRole("assistant");
        old2.setContent("那时候说过");
        when(messageMapper.listRange(eq(2L), isNull(), eq(30L), anyInt()))
                .thenReturn(List.of(old1, old2));
        when(memorySummaryService.compress(isNull(), anyList(), eq("林晚晴"), any()))
                .thenReturn("早期在酒馆见过面");

        ChatSession after = session();
        after.setMemorySummary("早期在酒馆见过面");
        after.setMemoryUntilId(6L);
        // requireOwnSession → ensure 内可能再读 → 最终 toMemoryMap
        when(sessionMapper.findById(2L)).thenReturn(s, s, after);

        Map<String, Object> out = service.refreshMemory(7L, 2L);
        verify(sessionMapper).updateMemory(2L, 7L, "早期在酒馆见过面", 6L, 1);
        assertEquals("早期在酒馆见过面", out.get("summary"));
        assertEquals(6L, out.get("untilId"));
    }

    @Test
    @DisplayName("没有未覆盖消息时强制刷新也不调模型")
    void refreshNoopWhenNothingUncovered() {
        stubModelWindow(20);
        ChatSession s = session();
        s.setMemoryEnabled(0);
        when(sessionMapper.findById(2L)).thenReturn(s);
        when(characterMapper.findById(9L)).thenReturn(new CharacterCard());
        when(messageMapper.listRecent(anyLong(), anyInt())).thenReturn(List.of());
        when(messageMapper.listRange(any(), any(), any(), any())).thenReturn(List.of());

        service.refreshMemory(7L, 2L);
        verify(memorySummaryService, never()).compress(any(), anyList(), any(), any());
    }

    @Test
    @DisplayName("分支：人设与记忆摘要一并带走")
    void branchCopiesPersonaAndMemory() {
        ChatSession src = session();
        src.setPersonaName("沈渡");
        src.setPersonaDesc("游侠");
        src.setMemorySummary("雾港见过面");
        src.setMemoryUntilId(12L);
        src.setMemoryEnabled(1);
        src.setPromptSnapshot("{}");

        ChatMessage pivot = new ChatMessage();
        pivot.setId(20L);
        pivot.setSessionId(2L);
        pivot.setRole("user");
        pivot.setContent("继续");

        CharacterCard card = new CharacterCard();
        card.setId(9L);
        card.setName("林晚晴");

        when(messageMapper.findById(20L)).thenReturn(pivot);
        when(sessionMapper.findById(2L)).thenReturn(src);
        when(characterMapper.findById(9L)).thenReturn(card);
        when(messageMapper.listBySession(2L)).thenReturn(List.of(pivot));
        when(sessionMapper.insert(any())).thenAnswer(inv -> {
            ChatSession s = inv.getArgument(0);
            s.setId(99L);
            return 1;
        });
        when(messageMapper.insert(any())).thenAnswer(inv -> {
            ChatMessage m = inv.getArgument(0);
            m.setId(1001L);
            return 1;
        });
        when(swipeMapper.findByMessage(anyLong())).thenReturn(List.of());

        ChatSession fresh = new ChatSession();
        fresh.setId(99L);
        fresh.setMessageCount(0);
        fresh.setTotalTokens(0L);
        when(sessionMapper.findById(99L)).thenReturn(fresh);

        service.branch(7L, 20L);

        verify(sessionMapper).updatePersona(99L, 7L, "沈渡", "游侠");
        verify(sessionMapper).updateMemory(99L, 7L, "雾港见过面", 12L, 1);
    }
}
