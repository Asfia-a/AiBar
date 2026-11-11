package com.tavern.service;

import com.tavern.common.BusinessException;
import com.tavern.dto.request.WorldInfoRequest;
import com.tavern.entity.CharacterCard;
import com.tavern.entity.WorldInfoEntry;
import com.tavern.mapper.CharacterMapper;
import com.tavern.mapper.WorldInfoMapper;
import com.tavern.prompt.WorldInfoService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 世界书管理侧的行为测试：权限、字段兜底、命中预演统计。
 *
 * <p>都是纯单测：mapper 用 mock 顶掉，不连库；预演走真实激活逻辑，
 * 所以测的是「管理页点一下会看到什么」。
 */
@DisplayName("世界书管理：权限 / 兜底 / 预演")
class WorldInfoAdminServiceTest {

    private final WorldInfoMapper mapper = mock(WorldInfoMapper.class);
    private final CharacterMapper characterMapper = mock(CharacterMapper.class);
    private final WorldInfoAdminService service =
            new WorldInfoAdminService(mapper, characterMapper, new WorldInfoService());

    private CharacterCard card(long id, long creatorId) {
        CharacterCard c = new CharacterCard();
        c.setId(id);
        c.setCreatorId(creatorId);
        return c;
    }

    private WorldInfoEntry entry(long id, String keys, String content) {
        WorldInfoEntry e = new WorldInfoEntry();
        e.setId(id);
        e.setCharacterId(1L);
        e.setUserId(7L);
        e.setTitle("条目" + id);
        e.setKeys(keys);
        e.setContent(content);
        e.setOrderNo(100);
        e.setPosition("after_char");
        e.setDepth(4);
        e.setProbability(100);
        e.setConstantOn(0);
        e.setEnabled(1);
        return e;
    }

    private WorldInfoRequest request(String keys, String content) {
        WorldInfoRequest r = new WorldInfoRequest();
        r.setTitle("测试条目");
        r.setKeys(keys);
        r.setContent(content);
        return r;
    }

    @Test
    @DisplayName("不是作者：改角色条目直接 403，先拦在入口")
    void updateByOtherUserIsForbidden() {
        when(mapper.findById(9L)).thenReturn(entry(9L, "钟楼", "钟楼每晚敲十三下"));
        when(characterMapper.findById(1L)).thenReturn(card(1L, 7L));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.update(8L, 9L, request("钟楼", "改一下")));
        assertEquals(403, ex.getCode());
    }

    @Test
    @DisplayName("非常量条目没填触发词：报错，不会静默存一条永远不命中的垃圾")
    void blankKeysRejected() {
        when(characterMapper.findById(1L)).thenReturn(card(1L, 7L));
        when(mapper.listByCharacter(1L)).thenReturn(List.of());

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.create(7L, 1L, request("  ", "内容")));
        assertTrue(ex.getMessage().contains("触发词"));
    }

    @Test
    @DisplayName("常量条目：清空触发词也算合法，它会无条件插入")
    void constantEntryNeedsNoKeys() {
        when(characterMapper.findById(1L)).thenReturn(card(1L, 7L));
        when(mapper.listByCharacter(1L)).thenReturn(List.of());

        WorldInfoRequest req = request("随便写点什么", "世界观：这是一座会下雨的城市");
        req.setConstant(true);
        service.create(7L, 1L, req);

        // 落库前应把触发词清掉：常量条目留着关键词只会让人误以为它还要触发
        ArgumentCaptor<WorldInfoEntry> captor = ArgumentCaptor.forClass(WorldInfoEntry.class);
        verify(mapper).insert(captor.capture());
        assertNull(captor.getValue().getKeys());
        assertEquals(1, captor.getValue().getConstantOn());
        assertEquals("after_char", captor.getValue().getPosition());
    }

    @Test
    @DisplayName("预演：只统计命中的条目，token 与预算一起返回")
    void previewCountsOnlyHits() {
        when(characterMapper.findById(1L)).thenReturn(card(1L, 7L));
        when(mapper.findActiveForCharacter(1L, 7L)).thenReturn(List.of(
                entry(1L, "钟楼", "钟楼每晚敲十三下，第十三下之后不要出门。"),
                entry(2L, "码头", "码头停着一条没有名字的船。")));

        Map<String, Object> out = service.preview(7L, 1L, "我走向钟楼", 1500);

        assertEquals(1, out.get("hitCount"));
        assertEquals(2, out.get("candidateCount"));
        assertEquals(1, out.get("missedCount"));
        assertFalse((Boolean) out.get("overBudget"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> hits = (List<Map<String, Object>>) out.get("hits");
        assertEquals("条目1", hits.get(0).get("title"));
        assertTrue((Integer) hits.get(0).get("tokens") > 0);
    }

    @Test
    @DisplayName("预演：预算很小时一条都塞不进，返回空列表而不是报错")
    void previewWithTinyBudget() {
        when(characterMapper.findById(1L)).thenReturn(card(1L, 7L));
        when(mapper.findActiveForCharacter(eq(1L), eq(7L))).thenReturn(List.of(
                entry(1L, "钟楼", "钟楼每晚敲十三下，第十三下之后不要出门，也不要回头看。")));

        Map<String, Object> out = service.preview(7L, 1L, "钟楼", 1);

        assertEquals(0, out.get("hitCount"));
        assertEquals(1, out.get("missedCount"));
    }

    @Test
    @DisplayName("导出：字段名与请求体对齐，条目里不带 id")
    void exportStripsIds() {
        when(characterMapper.findById(1L)).thenReturn(card(1L, 7L));
        when(mapper.listByCharacter(1L)).thenReturn(List.of(entry(9L, "钟楼", "钟楼每晚敲十三下")));

        Map<String, Object> out = service.export(7L, 1L);

        assertEquals("ai-tavern-worldbook", out.get("format"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) out.get("entries");
        assertEquals(1, items.size());
        assertFalse(items.get(0).containsKey("id"));
        assertEquals("钟楼每晚敲十三下", items.get(0).get("content"));
        assertEquals(Boolean.TRUE, items.get(0).get("enabled"));
    }

    @Test
    @DisplayName("导入：合法条目新建落库，缺正文的脏条目跳过")
    void importSkipsDirtyEntries() {
        when(characterMapper.findById(1L)).thenReturn(card(1L, 7L));
        when(mapper.listByCharacter(1L)).thenReturn(List.of());

        Map<String, Object> good = Map.of(
                "title", "钟楼", "keys", "钟楼", "content", "钟楼每晚敲十三下",
                "position", "after_char", "constant", false, "enabled", true);
        Map<String, Object> noContent = Map.of("title", "空条目");
        Map<String, Object> noKeys = Map.of("title", "没触发词", "content", "有内容");

        Map<String, Object> out = service.importEntries(7L, 1L,
                List.of(good, noContent, noKeys));

        assertEquals(1, out.get("imported"));
        assertEquals(2, out.get("skipped"));
        ArgumentCaptor<WorldInfoEntry> captor = ArgumentCaptor.forClass(WorldInfoEntry.class);
        verify(mapper).insert(captor.capture());
        assertEquals("钟楼每晚敲十三下", captor.getValue().getContent());
        assertEquals(1L, captor.getValue().getCharacterId());
        assertNull(captor.getValue().getId(), "导入的条目 id 一律由库重新分配");
    }

    @Test
    @DisplayName("导入：角色条目上限照常生效，多出来的算 skipped")
    void importRespectsCap() {
        when(characterMapper.findById(1L)).thenReturn(card(1L, 7L));
        // 已有 80 条 → 一条都导不进来
        List<WorldInfoEntry> full = new java.util.ArrayList<>();
        for (long i = 1; i <= 80; i++) {
            full.add(entry(i, "钥匙" + i, "内容" + i));
        }
        when(mapper.listByCharacter(1L)).thenReturn(full);

        Map<String, Object> out = service.importEntries(7L, 1L, List.of(
                Map.of("title", "新条目", "keys", "新", "content", "新内容")));

        assertEquals(0, out.get("imported"));
        assertEquals(1, out.get("skipped"));
        verify(mapper, org.mockito.Mockito.never()).insert(org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("排序：按前端给的新顺序重写 order_no，步长 10")
    void rewritesOrderNoByNewSequence() {
        when(characterMapper.findById(1L)).thenReturn(card(1L, 7L));
        when(mapper.listByCharacter(1L)).thenReturn(List.of(
                entry(1L, "钟楼", "内容"), entry(2L, "码头", "内容"), entry(3L, "灯塔", "内容")));

        service.reorder(7L, 1L, List.of(3L, 1L, 2L));

        verify(mapper).updateOrderNo(3L, 0);
        verify(mapper).updateOrderNo(1L, 10);
        verify(mapper).updateOrderNo(2L, 20);
    }

    @Test
    @DisplayName("排序：混入别人的条目 id 直接整体拒绝，一条都不写")
    void reorderRejectsForeignIds() {
        when(characterMapper.findById(1L)).thenReturn(card(1L, 7L));
        when(mapper.listByCharacter(1L)).thenReturn(List.of(entry(1L, "钟楼", "内容")));

        assertThrows(BusinessException.class,
                () -> service.reorder(7L, 1L, List.of(1L, 999L)));
        verify(mapper, org.mockito.Mockito.never()).updateOrderNo(org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    @DisplayName("导入 / 排序：不是角色作者直接 403")
    void importAndReorderRequireOwner() {
        when(characterMapper.findById(1L)).thenReturn(card(1L, 7L));

        assertThrows(BusinessException.class,
                () -> service.importEntries(8L, 1L, List.of(Map.of("content", "内容"))));
        assertThrows(BusinessException.class,
                () -> service.reorder(8L, 1L, List.of(1L)));
        verify(mapper, org.mockito.Mockito.never()).insert(org.mockito.ArgumentMatchers.any());
    }
}
