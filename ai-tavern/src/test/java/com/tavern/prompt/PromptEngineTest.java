package com.tavern.prompt;

import com.tavern.entity.WorldInfoEntry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("提示词引擎：拼装顺序、注入位置与预算")
class PromptEngineTest {

    private final PromptEngine engine =
            new PromptEngine(new PromptTemplateService(), new WorldInfoService());

    private WorldInfoEntry entry(long id, String keys, String content, String position, int depth) {
        WorldInfoEntry e = new WorldInfoEntry();
        e.setId(id);
        e.setTitle("设定" + id);
        e.setKeys(keys);
        e.setContent(content);
        e.setOrderNo(100);
        e.setPosition(position);
        e.setDepth(depth);
        e.setProbability(100);
        e.setConstantOn(0);
        e.setEnabled(1);
        return e;
    }

    private List<PromptInput.Turn> turns(int n) {
        List<PromptInput.Turn> list = new ArrayList<>();
        for (int i = 1; i <= n; i++) {
            list.add(new PromptInput.Turn(i % 2 == 1 ? "user" : "assistant", "第" + i + "句"));
        }
        return list;
    }

    @Test
    @DisplayName("system 在最前，历史按原顺序跟在后面")
    void basicOrder() {
        PromptInput in = PromptInput.builder()
                .character("林晚晴", "酒馆老板娘", "温柔", "开了七年酒馆", "", "古风")
                .user("旅人")
                .history(turns(2))
                .budget(6000, 500)
                .build();

        PromptBundle bundle = engine.build(in);
        List<Map<String, String>> messages = bundle.messages();
        assertEquals("system", messages.get(0).get("role"));
        assertTrue(messages.get(0).get("content").contains("林晚晴"));
        assertEquals("第1句", messages.get(1).get("content"));
        assertEquals("第2句", messages.get(2).get("content"));
        assertEquals(2, bundle.usedRounds());
    }

    @Test
    @DisplayName("世界书命中后以 system 消息插在角色设定之后")
    void worldInfoInserted() {
        PromptInput in = PromptInput.builder()
                .character("林晚晴", "酒馆老板娘", "", "", "", "")
                .history(List.of(new PromptInput.Turn("user", "雾港今晚有船吗")))
                .world(List.of(entry(1, "雾港", "雾港每七天来一班船。", "after_char", 4)), 4)
                .budget(6000, 500)
                .build();

        PromptBundle bundle = engine.build(in);
        assertEquals(1, bundle.activatedEntries().size());
        // 前端「本轮命中」直接展示标题，所以这里必须是可读名而不是空串 / id
        assertEquals("设定1", bundle.activatedEntries().get(0));
        assertTrue(bundle.messages().stream().anyMatch(m -> m.get("content").contains("每七天来一班船")));
        assertTrue(bundle.worldInfoChars() > 0);
    }

    @Test
    @DisplayName("作者注释按 depth 插到倒数第 N 条之前")
    void authorNoteDepth() {
        PromptInput in = PromptInput.builder()
                .character("林晚晴", "", "", "", "", "")
                .history(turns(4))
                .authorNote("请用短句", 2, 1, 1)
                .budget(6000, 500)
                .build();

        PromptBundle bundle = engine.build(in);
        List<Map<String, String>> m = bundle.messages();
        // 4 条历史 + 1 条注入
        assertEquals(6, m.size()); // system + 5
        assertTrue(m.get(3).get("content").contains("请用短句"));
    }

    @Test
    @DisplayName("作者注释 depth=0：挂在历史末尾，不会被丢掉")
    void authorNoteAtTail() {
        PromptInput in = PromptInput.builder()
                .character("林晚晴", "", "", "", "", "")
                .history(turns(2))
                .authorNote("请用短句", 0, 1, 1)
                .budget(6000, 500)
                .build();

        PromptBundle bundle = engine.build(in);
        List<Map<String, String>> m = bundle.messages();
        // system + 2 条历史 + 末尾的注释
        assertEquals(4, m.size());
        assertEquals("system", m.get(3).get("role"));
        assertTrue(m.get(3).get("content").contains("请用短句"));
        assertTrue(bundle.authorNoteChars() > 0);
    }

    @Test
    @DisplayName("Post-History：作为最后一条 system 追加在全部历史之后")
    void postHistoryAppendedAtEnd() {
        PromptInput in = PromptInput.builder()
                .character("林晚晴", "", "", "", "", "")
                .history(turns(2))
                .postHistory("保持第二人称叙事，不要替你做决定")
                .budget(6000, 500)
                .build();

        PromptBundle bundle = engine.build(in);
        List<Map<String, String>> m = bundle.messages();
        // system + 2 条历史 + 收尾指令
        assertEquals(4, m.size());
        assertEquals("system", m.get(3).get("role"));
        assertTrue(m.get(3).get("content").contains("不要替你做决定"));
        // 收尾指令必须排在所有历史之后，它是约束力最强的一句话
        assertEquals("第2句", m.get(2).get("content"));
    }

    @Test
    @DisplayName("Post-History：没填就不追加，不产生空消息")
    void blankPostHistoryIgnored() {
        PromptInput in = PromptInput.builder()
                .character("林晚晴", "", "", "", "", "")
                .history(turns(2))
                .postHistory("   ")
                .budget(6000, 500)
                .build();

        PromptBundle bundle = engine.build(in);
        assertEquals(3, bundle.messages().size());
        assertEquals("第2句", bundle.messages().get(2).get("content"));
    }

    @Test
    @DisplayName("Post-History：排在 in_chat 注入的作者注释之后，不会被挤走")
    void postHistoryComesAfterInjections() {
        PromptInput in = PromptInput.builder()
                .character("林晚晴", "", "", "", "", "")
                .history(turns(2))
                .authorNote("请用短句", 0, 1, 1) // depth=0 → 历史末尾
                .postHistory("不要替你做决定")
                .budget(6000, 500)
                .build();

        List<Map<String, String>> m = engine.build(in).messages();
        // 顺序应为：system → 历史×2 → 作者注释 → 收尾指令
        assertEquals(5, m.size());
        assertTrue(m.get(3).get("content").contains("请用短句"));
        assertTrue(m.get(4).get("content").contains("不要替你做决定"));
    }

    @Test
    @DisplayName("频率没到就不插作者注释")
    void authorNoteFrequency() {
        PromptInput in = PromptInput.builder()
                .character("林晚晴", "", "", "", "", "")
                .history(turns(2))
                .authorNote("请用短句", 0, 3, 1) // 第 1 句，频率 3 → 不插
                .budget(6000, 500)
                .build();
        assertEquals(0, engine.build(in).authorNoteChars());
    }

    @Test
    @DisplayName("预算不够时先丢最早的历史")
    void budgetDropsOldest() {
        List<PromptInput.Turn> history = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            history.add(new PromptInput.Turn("user", "这是一句比较长的历史消息".repeat(20)));
        }
        PromptInput in = PromptInput.builder()
                .character("林晚晴", "", "", "", "", "")
                .history(history)
                .budget(600, 100)
                .build();

        PromptBundle bundle = engine.build(in);
        assertTrue(bundle.usedRounds() < 10);
        assertTrue(bundle.droppedRounds() > 0);
        // 保留下来的应该是最新的那些
        String lastDialogue = bundle.messages().get(bundle.messages().size() - 1).get("content");
        assertEquals(history.get(9).content(), lastDialogue);
    }

    @Test
    @DisplayName("角色卡写得很长时，历史对话也不能被挤成 0 条")
    void longCardStillKeepsHistory() {
        // 回归用例：曾经因为「剩余预算 = 总预算 - 固定部分 - 输出预留」，
        // 长卡把预算吃光后 historyBudget 变成 0，模型看不到对话，
        // 会回出「How can I help you?」这种完全跳出人设的话。
        String hugeDescription = "很长的人设描述".repeat(800); // 约 5600 token
        List<PromptInput.Turn> history = turns(6);

        PromptInput in = PromptInput.builder()
                .character("绘梨衣", hugeDescription, "", "", "", "")
                .history(history)
                .budget(8000, 500)
                .build();

        PromptBundle bundle = engine.build(in);
        assertTrue(bundle.usedRounds() > 0, "历史不能被全部丢弃");
        String last = bundle.messages().get(bundle.messages().size() - 1).get("content");
        assertEquals(history.get(history.size() - 1).content(), last);
    }

    @Test
    @DisplayName("模板里的 {{char}} / {{user}} 被替换成真实名字")
    void macrosReplaced() {
        PromptInput in = PromptInput.builder()
                .character("林晚晴", "老板娘", "", "", "", "")
                .user("小董")
                .budget(6000, 500)
                .build();
        String system = engine.build(in).messages().get(0).get("content");
        assertTrue(system.contains("林晚晴"));
        assertTrue(system.contains("小董"));
        assertTrue(!system.contains("{{char}}"));
    }

    @Test
    @DisplayName("用户人设插在角色设定之后、历史之前")
    void userPersonaBeforeHistory() {
        PromptInput in = PromptInput.builder()
                .character("林晚晴", "老板娘", "", "", "", "")
                .user("沈渡")
                .userPersona("二十出头的游侠，左手缺一指")
                .history(turns(2))
                .budget(6000, 500)
                .build();

        PromptBundle bundle = engine.build(in);
        List<Map<String, String>> m = bundle.messages();
        // system(主) + 人设 + 历史×2
        assertEquals(4, m.size());
        assertTrue(m.get(1).get("content").contains("游侠"));
        assertTrue(m.get(1).get("content").contains("沈渡"));
        assertEquals("第1句", m.get(2).get("content"));
        assertTrue(bundle.personaChars() > 0);
    }

    @Test
    @DisplayName("空白人设不注入，不产生空消息")
    void blankPersonaIgnored() {
        PromptInput in = PromptInput.builder()
                .character("林晚晴", "", "", "", "", "")
                .userPersona("   ")
                .history(turns(1))
                .budget(6000, 500)
                .build();
        assertEquals(0, engine.build(in).personaChars());
        assertEquals(2, engine.build(in).messages().size());
    }

    @Test
    @DisplayName("剧情摘要插在人设之后、历史之前")
    void memorySummaryBeforeHistory() {
        PromptInput in = PromptInput.builder()
                .character("林晚晴", "", "", "", "", "")
                .user("沈渡")
                .userPersona("游侠")
                .memorySummary("两人曾在雾港码头交换过信物")
                .history(turns(1))
                .budget(6000, 500)
                .build();

        List<Map<String, String>> m = engine.build(in).messages();
        // 主提示 → 人设 → 摘要 → 历史
        assertEquals(4, m.size());
        assertTrue(m.get(1).get("content").contains("游侠"));
        assertTrue(m.get(2).get("content").contains("雾港码头"));
        assertEquals("第1句", m.get(3).get("content"));
        assertTrue(engine.build(in).memoryChars() > 0);
    }

    @Test
    @DisplayName("空白摘要不注入")
    void blankMemoryIgnored() {
        PromptInput in = PromptInput.builder()
                .character("林晚晴", "", "", "", "", "")
                .memorySummary("  ")
                .history(turns(1))
                .budget(6000, 500)
                .build();
        assertEquals(0, engine.build(in).memoryChars());
    }
}
