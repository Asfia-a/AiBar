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
}
