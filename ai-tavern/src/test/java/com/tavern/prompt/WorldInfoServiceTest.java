package com.tavern.prompt;

import com.tavern.entity.WorldInfoEntry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("世界书激活：只在关键词命中时插入")
class WorldInfoServiceTest {

    private final WorldInfoService service = new WorldInfoService();

    private WorldInfoEntry entry(long id, String keys, String content) {
        WorldInfoEntry e = new WorldInfoEntry();
        e.setId(id);
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

    @Test
    @DisplayName("命中关键词才插入")
    void keywordActivation() {
        List<WorldInfoEntry> entries = List.of(
                entry(1, "雾港", "雾港常年起雾，夜里只有灯塔亮着。"),
                entry(2, "龙族", "龙族已经绝迹三百年。")
        );
        List<WorldInfoEntry> hit = service.activate(entries, "我想去雾港看看", 1000);
        assertEquals(1, hit.size());
        assertEquals(1L, hit.get(0).getId());
    }

    @Test
    @DisplayName("没命中就一条都不插")
    void noKeywordNoEntry() {
        List<WorldInfoEntry> entries = List.of(entry(1, "雾港", "设定"));
        assertTrue(service.activate(entries, "今天天气不错", 1000).isEmpty());
    }

    @Test
    @DisplayName("常量条目无条件插入，且排在最前")
    void constantFirst() {
        WorldInfoEntry constant = entry(1, "", "世界观：所有故事发生在雾港。");
        constant.setConstantOn(1);
        constant.setOrderNo(50);
        WorldInfoEntry keywordEntry = entry(2, "灯塔", "灯塔守夜人叫老周。");

        List<WorldInfoEntry> hit = service.activate(List.of(constant, keywordEntry), "灯塔还亮着吗", 1000);
        assertEquals(2, hit.size());
        assertEquals(1L, hit.get(0).getId());
    }

    @Test
    @DisplayName("超出预算的条目不再插入")
    void budgetStops() {
        WorldInfoEntry big = entry(1, "雾港", "雾".repeat(300)); // 约 200 token
        List<WorldInfoEntry> hit = service.activate(List.of(big), "雾港", 50);
        assertTrue(hit.isEmpty());
    }

    @Test
    @DisplayName("递归一层：已入条目正文提到别的关键词时再插入")
    void recursionOneLevel() {
        List<WorldInfoEntry> entries = List.of(
                entry(1, "贝丝", "贝丝是一头牛，和鲁弗斯是朋友。"),
                entry(2, "鲁弗斯", "鲁弗斯是一条狗。")
        );
        List<WorldInfoEntry> hit = service.activate(entries, "贝丝今天怎么样", 1000);
        assertEquals(2, hit.size());
    }

    @Test
    @DisplayName("keys 支持 /正则/flags 写法")
    void regexKey() {
        WorldInfoEntry e = entry(1, "/下.*雨/i", "下雨天酒馆不营业。");
        assertEquals(1, service.activate(List.of(e), "外面下着大雨", 1000).size());
    }

    @Test
    @DisplayName("停用的条目不参与匹配")
    void disabledSkipped() {
        WorldInfoEntry e = entry(1, "雾港", "设定");
        e.setEnabled(0);
        assertTrue(service.activate(List.of(e), "雾港", 1000).isEmpty());
    }

    @Test
    @DisplayName("逗号分隔的多个 key 任一命中即可")
    void multiKeys() {
        WorldInfoEntry e = entry(1, "酒馆, 酒吧, 客栈", "城里有三家酒馆。");
        assertFalse(service.activate(List.of(e), "那家客栈还开着", 1000).isEmpty());
    }
}
