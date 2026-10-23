package com.tavern.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tavern.common.BusinessException;
import com.tavern.config.BillingProperties;
import com.tavern.entity.CharacterCard;
import com.tavern.entity.ChatMessage;
import com.tavern.entity.ChatSession;
import com.tavern.entity.MessageSwipe;
import com.tavern.entity.User;
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
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 消息操作的一致性：多版本、编辑、权限。
 *
 * <p>这些逻辑最容易出的问题是「数据对不上」——比如第一次滑动时把原回答冲掉、
 * 编辑后会话列表摘要还是旧的、改的其实是别人的消息。这里都盯住。
 */
@DisplayName("消息操作：多版本 / 编辑 / 权限")
class ChatMessageOpsTest {

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
    private final BillingProperties billingProperties = new BillingProperties();
    private final RuntimeMetricsService metricsService = mock(RuntimeMetricsService.class);
    private final PromptEngine promptEngine = mock(PromptEngine.class);
    private final WorldInfoMapper worldInfoMapper = mock(WorldInfoMapper.class);

    private final ChatService service = new ChatService(
            sessionMapper, messageMapper, swipeMapper, characterMapper, tagMapper, userMapper,
            tokenLogMapper, collectMapper, aiModelService, contentAuditService,
            billingProperties, new ObjectMapper(), metricsService, promptEngine, worldInfoMapper);

    private ChatSession session(long userId) {
        ChatSession s = new ChatSession();
        s.setId(2L);
        s.setUserId(userId);
        s.setCharacterId(9L);
        s.setMessageCount(4);
        s.setTotalTokens(100L);
        return s;
    }

    private ChatMessage message(long id, long sessionId, String role, String content) {
        ChatMessage m = new ChatMessage();
        m.setId(id);
        m.setSessionId(sessionId);
        m.setRole(role);
        m.setContent(content);
        m.setTokens(30);
        m.setSwipeCount(1);
        m.setSwipeIndex(0);
        return m;
    }

    private void stubBilling() {
        User user = new User();
        user.setId(7L);
        user.setPoints(100);
        when(userMapper.findById(7L)).thenReturn(user);
        when(userMapper.deductPoints(eq(7L), anyInt())).thenReturn(1);
        when(characterMapper.findById(9L)).thenReturn(new CharacterCard());
    }

    @Test
    @DisplayName("第一次滑动：先把原回答存成第 0 版，再追加新版本并设为当前")
    void firstSwipeKeepsOriginal() {
        stubBilling();
        ChatSession s = session(7L);
        ChatMessage target = message(50L, 2L, "assistant", "旧回答");

        MessageSwipe first = new MessageSwipe();
        first.setIdx(0);
        first.setContent("旧回答");
        when(swipeMapper.findByMessage(50L)).thenReturn(List.of(), List.of(first));

        service.finishSwipe(7L, s, new CharacterCard(),
                new AiModelService.StreamResult("新回答", 100), target);

        ArgumentCaptor<MessageSwipe> captor = ArgumentCaptor.forClass(MessageSwipe.class);
        verify(swipeMapper, org.mockito.Mockito.times(2)).insert(captor.capture());
        List<MessageSwipe> inserted = captor.getAllValues();
        assertEquals(0, inserted.get(0).getIdx().intValue());
        assertEquals("旧回答", inserted.get(0).getContent());
        assertEquals(1, inserted.get(1).getIdx().intValue());
        assertEquals("新回答", inserted.get(1).getContent());

        verify(messageMapper).updateActive(50L, "新回答", 100, 1, 2);
    }

    @Test
    @DisplayName("滑动改的是最后一轮：会话摘要跟着更新")
    void swipeUpdatesSessionTail() {
        stubBilling();
        ChatSession s = session(7L);
        ChatMessage target = message(50L, 2L, "assistant", "旧回答");
        when(swipeMapper.findByMessage(50L)).thenReturn(List.of(new MessageSwipe()));
        when(messageMapper.countAfter(2L, 50L)).thenReturn(0);

        service.finishSwipe(7L, s, new CharacterCard(),
                new AiModelService.StreamResult("新回答", 100), target);

        assertEquals("新回答", s.getLastMessage());
        verify(sessionMapper).update(s);
    }

    @Test
    @DisplayName("编辑：只改文本，不重发生成、不动版本数")
    void editOnlyChangesText() {
        ChatMessage msg = message(60L, 2L, "user", "打错字了");
        when(messageMapper.findById(60L)).thenReturn(msg);
        when(sessionMapper.findById(2L)).thenReturn(session(7L));
        when(contentAuditService.auditSync(any())).thenReturn(List.of());
        when(messageMapper.countAfter(2L, 60L)).thenReturn(1);

        service.editMessage(7L, 60L, "  改好了  ");

        verify(messageMapper).updateContent(60L, "改好了");
        // 不是最后一轮，就不该改会话摘要
        verify(sessionMapper, never()).update(any());
    }

    @Test
    @DisplayName("编辑命中敏感词：直接拒，不落库")
    void editBlockedByAudit() {
        ChatMessage msg = message(60L, 2L, "user", "正常");
        when(messageMapper.findById(60L)).thenReturn(msg);
        when(sessionMapper.findById(2L)).thenReturn(session(7L));
        when(contentAuditService.auditSync(any())).thenReturn(List.of("违规词"));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.editMessage(7L, 60L, "包含违规词的内容"));

        assertTrue(ex.getMessage().contains("违规"));
        verify(messageMapper, never()).updateContent(any(), any());
    }

    @Test
    @DisplayName("编辑空内容：报错，不会把消息改成空壳")
    void blankEditRejected() {
        when(messageMapper.findById(60L)).thenReturn(message(60L, 2L, "user", "正常"));
        when(sessionMapper.findById(2L)).thenReturn(session(7L));

        assertThrows(BusinessException.class, () -> service.editMessage(7L, 60L, "   "));
    }

    @Test
    @DisplayName("别人的消息：改不了，先拦在入口")
    void cannotEditOthersMessage() {
        when(messageMapper.findById(60L)).thenReturn(message(60L, 2L, "user", "正常"));
        when(sessionMapper.findById(2L)).thenReturn(session(8L));

        assertThrows(BusinessException.class, () -> service.editMessage(7L, 60L, "改一下"));
        verify(messageMapper, never()).updateContent(any(), any());
    }

    @Test
    @DisplayName("只能对助手回复做滑动，用户消息不行")
    void swipeRejectsUserMessage() {
        when(messageMapper.findById(60L)).thenReturn(message(60L, 2L, "user", "我说的话"));
        when(sessionMapper.findById(2L)).thenReturn(session(7L));

        assertThrows(BusinessException.class, () -> service.swipe(7L, 60L));
    }
}
