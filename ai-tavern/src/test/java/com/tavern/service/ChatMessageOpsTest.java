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
import static org.mockito.ArgumentMatchers.anyList;
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
    private final MemorySummaryService memorySummaryService = mock(MemorySummaryService.class);

    private final ChatService service = new ChatService(
            sessionMapper, messageMapper, swipeMapper, characterMapper, tagMapper, userMapper,
            tokenLogMapper, collectMapper, aiModelService, contentAuditService,
            billingProperties, new ObjectMapper(), metricsService, promptEngine, worldInfoMapper,
            memorySummaryService);

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

        verify(messageMapper).updateActive(50L, "新回答", null, 100, 1, 2);
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

    @Test
    @DisplayName("删除消息：级联清掉历史版本与收藏，并重算会话摘要")
    void deleteMessagesCascades() {
        when(sessionMapper.findById(2L)).thenReturn(session(7L));
        when(messageMapper.deleteByIds(eq(2L), anyList())).thenReturn(2);
        when(messageMapper.findLast(2L)).thenReturn(message(40L, 2L, "assistant", "剩下的最后一条"));
        when(messageMapper.countBySession(2L)).thenReturn(3);

        java.util.Map<String, Object> res = service.deleteMessages(7L, 2L, List.of(50L, 51L));

        assertEquals(2, res.get("deleted"));
        assertEquals(3, res.get("messageCount"));
        assertEquals("剩下的最后一条", res.get("lastMessage"));
        // 消息删了，它的多版本与收藏不能留成孤儿
        verify(swipeMapper).deleteByMessageIds(List.of(50L, 51L));
        verify(collectMapper).deleteByMessageIds(List.of(50L, 51L));
        verify(sessionMapper).update(any());
        // total_tokens 不回退：积分已经扣了
        verify(sessionMapper, never()).delete(any(), any());
    }

    @Test
    @DisplayName("删除消息：别人的会话直接拒绝")
    void deleteRejectsOthersSession() {
        when(sessionMapper.findById(2L)).thenReturn(session(8L));

        assertThrows(BusinessException.class, () -> service.deleteMessages(7L, 2L, List.of(50L)));
        verify(messageMapper, never()).deleteByIds(any(), anyList());
    }

    @Test
    @DisplayName("删除消息：没选中的时候不产生任何 SQL")
    void deleteRequiresIds() {
        when(sessionMapper.findById(2L)).thenReturn(session(7L));

        assertThrows(BusinessException.class, () -> service.deleteMessages(7L, 2L, List.of()));
        verify(messageMapper, never()).deleteByIds(any(), anyList());
    }

    @Test
    @DisplayName("删除消息：id 不属于这个会话时整体失败，不误删")
    void deleteRejectsForeignMessageIds() {
        when(sessionMapper.findById(2L)).thenReturn(session(7L));
        when(messageMapper.deleteByIds(eq(2L), anyList())).thenReturn(0);

        assertThrows(BusinessException.class, () -> service.deleteMessages(7L, 2L, List.of(999L)));
        // 一条都没删掉，就不该再去动 swipe / 收藏
        verify(swipeMapper, never()).deleteByMessageIds(anyList());
    }

    @Test
    @DisplayName("删除本轮：点在用户消息上，连同下一条助手一起删")
    void deleteRoundFromUserRemovesPair() {
        when(sessionMapper.findById(2L)).thenReturn(session(7L));
        when(messageMapper.listBySession(2L)).thenReturn(List.of(
                message(10L, 2L, "user", "你好"),
                message(11L, 2L, "assistant", "嗨"),
                message(12L, 2L, "user", "下一轮"),
                message(13L, 2L, "assistant", "继续")
        ));
        when(messageMapper.deleteByIds(eq(2L), anyList())).thenReturn(2);
        when(messageMapper.findLast(2L)).thenReturn(message(13L, 2L, "assistant", "继续"));
        when(messageMapper.countBySession(2L)).thenReturn(2);

        ArgumentCaptor<List<Long>> idsCap = ArgumentCaptor.forClass(List.class);
        java.util.Map<String, Object> res = service.deleteRound(7L, 2L, 10L);

        verify(messageMapper).deleteByIds(eq(2L), idsCap.capture());
        assertEquals(List.of(10L, 11L), idsCap.getValue());
        assertEquals(List.of(10L, 11L), res.get("roundIds"));
        assertEquals(2, res.get("deleted"));
    }

    @Test
    @DisplayName("删除本轮：点在助手消息上，连同上一条用户一起删")
    void deleteRoundFromAssistantRemovesPair() {
        when(sessionMapper.findById(2L)).thenReturn(session(7L));
        when(messageMapper.listBySession(2L)).thenReturn(List.of(
                message(10L, 2L, "user", "你好"),
                message(11L, 2L, "assistant", "嗨")
        ));
        when(messageMapper.deleteByIds(eq(2L), anyList())).thenReturn(2);
        when(messageMapper.findLast(2L)).thenReturn(null);
        when(messageMapper.countBySession(2L)).thenReturn(0);

        ArgumentCaptor<List<Long>> idsCap = ArgumentCaptor.forClass(List.class);
        service.deleteRound(7L, 2L, 11L);

        verify(messageMapper).deleteByIds(eq(2L), idsCap.capture());
        assertEquals(List.of(10L, 11L), idsCap.getValue());
    }

    @Test
    @DisplayName("删除本轮：没有配对时只删锚点这一条")
    void deleteRoundOrphanOnlySelf() {
        when(sessionMapper.findById(2L)).thenReturn(session(7L));
        when(messageMapper.listBySession(2L)).thenReturn(List.of(
                message(10L, 2L, "user", "只有用户，助手还没回")
        ));
        when(messageMapper.deleteByIds(eq(2L), anyList())).thenReturn(1);
        when(messageMapper.findLast(2L)).thenReturn(null);
        when(messageMapper.countBySession(2L)).thenReturn(0);

        ArgumentCaptor<List<Long>> idsCap = ArgumentCaptor.forClass(List.class);
        java.util.Map<String, Object> res = service.deleteRound(7L, 2L, 10L);

        verify(messageMapper).deleteByIds(eq(2L), idsCap.capture());
        assertEquals(List.of(10L), idsCap.getValue());
        assertEquals(List.of(10L), res.get("roundIds"));
    }

    private MessageSwipe swipeRow(int idx, String content) {
        return swipeRow(idx, content, null);
    }

    private MessageSwipe swipeRow(int idx, String content, String reasoning) {
        MessageSwipe s = new MessageSwipe();
        s.setMessageId(30L);
        s.setIdx(idx);
        s.setContent(content);
        s.setReasoning(reasoning);
        s.setTokens(30);
        return s;
    }

    @Test
    @DisplayName("分支：多版本一起复制进新会话，版本号与内容对得上")
    void branchCopiesSwipeVersions() {
        when(messageMapper.findById(30L)).thenReturn(message(30L, 2L, "assistant", "当前版本回答"));
        when(sessionMapper.findById(2L)).thenReturn(session(7L));
        CharacterCard card = new CharacterCard();
        card.setId(9L);
        card.setName("林晚晴");
        when(characterMapper.findById(9L)).thenReturn(card);

        ChatMessage greet = message(10L, 2L, "assistant", "开场白");
        ChatMessage user = message(20L, 2L, "user", "问题");
        ChatMessage ai = message(30L, 2L, "assistant", "当前版本回答");
        ai.setReasoning("当前版思考");
        ai.setSwipeCount(2);
        ai.setSwipeIndex(1);
        when(messageMapper.listBySession(2L)).thenReturn(List.of(greet, user, ai));

        // 用 Answer 模拟自增主键：插一条发一个新 id
        java.util.concurrent.atomic.AtomicLong seq = new java.util.concurrent.atomic.AtomicLong(1000);
        when(messageMapper.insert(any())).thenAnswer(inv -> {
            ChatMessage m = inv.getArgument(0);
            m.setId(seq.incrementAndGet());
            return 1;
        });
        when(swipeMapper.findByMessage(org.mockito.ArgumentMatchers.anyLong())).thenAnswer(inv -> {
            long id = inv.getArgument(0, Long.class);
            return id == 30L
                    ? List.of(swipeRow(0, "第一版回答", "第一版思考"), swipeRow(1, "第二版回答", "第二版思考"))
                    : List.of();
        });
        // copy.getId() 依赖自增主键回填，mock 下是 null：会话查回来的是新分支
        ChatSession fresh = new ChatSession();
        fresh.setId(99L);
        fresh.setMessageCount(0);
        fresh.setTotalTokens(0L);
        when(sessionMapper.findById(org.mockito.ArgumentMatchers.isNull())).thenReturn(fresh);

        ChatSession created = service.branch(7L, 30L);

        assertEquals(99L, created.getId());
        assertEquals(3, created.getMessageCount());
        assertEquals("当前版本回答", created.getLastMessage());

        ArgumentCaptor<ChatMessage> msgCaptor = ArgumentCaptor.forClass(ChatMessage.class);
        verify(messageMapper, org.mockito.Mockito.times(3)).insert(msgCaptor.capture());
        ChatMessage copiedAi = msgCaptor.getAllValues().get(2);
        assertEquals(2, copiedAi.getSwipeCount(), "版本数要跟源消息一致");
        assertEquals(1, copiedAi.getSwipeIndex(), "当前展示的版本也要跟源消息一致");
        assertEquals("当前版思考", copiedAi.getReasoning(), "思考链要随消息带走");

        ArgumentCaptor<MessageSwipe> swipeCaptor = ArgumentCaptor.forClass(MessageSwipe.class);
        verify(swipeMapper, org.mockito.Mockito.times(2)).insert(swipeCaptor.capture());
        List<MessageSwipe> inserted = swipeCaptor.getAllValues();
        // 新消息 id 是第三个自增值（1001 开场白、1002 用户消息、1003 助手回复）
        assertEquals(1003L, inserted.get(0).getMessageId());
        assertEquals(0, inserted.get(0).getIdx());
        assertEquals("第一版回答", inserted.get(0).getContent());
        assertEquals("第一版思考", inserted.get(0).getReasoning());
        assertEquals(1003L, inserted.get(1).getMessageId());
        assertEquals(1, inserted.get(1).getIdx());
        assertEquals("第二版回答", inserted.get(1).getContent());
        assertEquals("第二版思考", inserted.get(1).getReasoning());
    }

    @Test
    @DisplayName("分支：分支点之后的消息不复制")
    void branchOnlyCopiesUpToCutoff() {
        when(messageMapper.findById(20L)).thenReturn(message(20L, 2L, "user", "问题"));
        when(sessionMapper.findById(2L)).thenReturn(session(7L));
        when(characterMapper.findById(9L)).thenReturn(new CharacterCard());
        when(messageMapper.listBySession(2L)).thenReturn(List.of(
                message(10L, 2L, "assistant", "开场白"),
                message(20L, 2L, "user", "问题"),
                message(30L, 2L, "assistant", "不该被复制走")));

        java.util.concurrent.atomic.AtomicLong seq = new java.util.concurrent.atomic.AtomicLong(1000);
        when(messageMapper.insert(any())).thenAnswer(inv -> {
            ChatMessage m = inv.getArgument(0);
            m.setId(seq.incrementAndGet());
            return 1;
        });
        when(swipeMapper.findByMessage(org.mockito.ArgumentMatchers.anyLong())).thenReturn(List.of());
        ChatSession fresh = new ChatSession();
        fresh.setId(99L);
        fresh.setMessageCount(0);
        fresh.setTotalTokens(0L);
        when(sessionMapper.findById(org.mockito.ArgumentMatchers.isNull())).thenReturn(fresh);

        ChatSession created = service.branch(7L, 20L);

        assertEquals(2, created.getMessageCount());
        ArgumentCaptor<ChatMessage> msgCaptor = ArgumentCaptor.forClass(ChatMessage.class);
        verify(messageMapper, org.mockito.Mockito.times(2)).insert(msgCaptor.capture());
        assertEquals("问题", msgCaptor.getAllValues().get(1).getContent());
    }
}
