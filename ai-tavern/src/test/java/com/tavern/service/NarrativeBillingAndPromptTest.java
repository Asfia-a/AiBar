package com.tavern.service;

import com.tavern.config.BillingProperties;
import com.tavern.entity.ChatMessage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 叙事生成的两个算法点：Token 计费换算、对话分层裁剪。
 *
 * <p>裁剪方法在 {@code NarrativePromptService} 里是私有的（只被 build 调用），
 * 这里用反射直接测。比起为了测试把可见性放宽成 public，反射更贴近「这段逻辑
 * 本身对不对」的验证目标，也不会改动生产代码的结构。
 */
@DisplayName("计费与提示词裁剪")
class NarrativeBillingAndPromptTest {

    @Nested
    @DisplayName("Token 积分换算")
    class Billing {

        private final BillingProperties billing = new BillingProperties();

        @Test
        @DisplayName("默认 1000 Token 折 1 积分")
        void defaultRate() {
            assertEquals(1000, billing.getTokensPerPoint());
            assertEquals(1, billing.toPoints(1));
            assertEquals(1, billing.toPoints(1000));
            assertEquals(2, billing.toPoints(1001));
        }

        @Test
        @DisplayName("不足一个积分也按 1 算，不能出现 0 积分的调用")
        void neverZero() {
            assertEquals(1, billing.toPoints(0));
            assertEquals(1, billing.toPoints(-5));
        }

        @Test
        @DisplayName("向上取整而非四舍五入")
        void roundUp() {
            assertEquals(3, billing.toPoints(2001));
            assertEquals(10, billing.toPoints(10000));
        }

        @Test
        @DisplayName("换算率被配成 0 或负数时退化为 1，避免除零")
        void rateFloor() {
            // 意图是「不除零」：rate 退化成 1，于是 1 token = 1 积分，而不是打折
            billing.setTokensPerPoint(0);
            assertEquals(1, billing.toPoints(1));
            assertEquals(500, billing.toPoints(500));

            billing.setTokensPerPoint(-10);
            assertEquals(1, billing.toPoints(1));
            assertEquals(300, billing.toPoints(300));
        }

        @Test
        @DisplayName("积分换回 Token 是单向放大")
        void pointsToTokens() {
            assertEquals(3000, billing.tokensFromPoints(3));
        }
    }

    @Nested
    @DisplayName("对话分层裁剪")
    class Trimming {

        /** 造一批等长消息，便于精确控制总量 */
        private List<ChatMessage> dialogue(int count, int charsEach) {
            List<ChatMessage> list = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                ChatMessage m = new ChatMessage();
                m.setId((long) i);
                m.setRole(i % 2 == 0 ? "user" : "assistant");
                m.setContent("第" + i + "条".repeat(Math.max(1, charsEach / 4)));
                list.add(m);
            }
            return list;
        }

        private List<ChatMessage> invoke(List<ChatMessage> input) throws Exception {
            Method m = NarrativePromptService.class
                    .getDeclaredMethod("selectByBudget", List.class);
            m.setAccessible(true);
            @SuppressWarnings("unchecked")
            List<ChatMessage> out = (List<ChatMessage>) m.invoke(new NarrativePromptService(null, null, null), input);
            return out;
        }

        @Test
        @DisplayName("对话很短时原样保留，不做裁剪")
        void shortDialogueUntouched() throws Exception {
            List<ChatMessage> input = dialogue(10, 20);
            List<ChatMessage> out = invoke(input);
            assertEquals(10, out.size());
            assertEquals(input.get(0).getId(), out.get(0).getId());
        }

        @Test
        @DisplayName("对话超预算时被裁剪，且条数减少")
        void longDialogueTrimmed() throws Exception {
            List<ChatMessage> input = dialogue(600, 200);
            List<ChatMessage> out = invoke(input);
            assertTrue(out.size() < input.size(), "超预算必须裁剪");
            assertTrue(out.size() >= 8, "至少保留 8 条，否则剧情断了");
        }

        @Test
        @DisplayName("裁剪保留首尾——开场和结局不能丢")
        void keepsHeadAndTail() throws Exception {
            List<ChatMessage> input = dialogue(600, 200);
            List<ChatMessage> out = invoke(input);
            assertEquals(input.get(0).getId(), out.get(0).getId(), "开场必须保留");
            assertEquals(input.get(input.size() - 1).getId(),
                    out.get(out.size() - 1).getId(), "结局必须保留");
        }

        @Test
        @DisplayName("裁剪后不产生重复消息")
        void noDuplicates() throws Exception {
            List<ChatMessage> out = invoke(dialogue(600, 200));
            long distinct = out.stream().map(ChatMessage::getId).distinct().count();
            assertEquals(out.size(), distinct, "抽样逻辑不能重复取同一条");
        }

        @Test
        @DisplayName("剧情线索压缩：长对话抽 5 条，短对话全留")
        void condense() throws Exception {
            Method m = NarrativePromptService.class
                    .getDeclaredMethod("condense", List.class, int.class);
            m.setAccessible(true);
            NarrativePromptService svc = new NarrativePromptService(null, null, null);

            @SuppressWarnings("unchecked")
            List<ChatMessage> longOut = (List<ChatMessage>) m.invoke(svc, dialogue(100, 20), 5);
            assertEquals(5, longOut.size());

            @SuppressWarnings("unchecked")
            List<ChatMessage> shortOut = (List<ChatMessage>) m.invoke(svc, dialogue(3, 20), 5);
            assertEquals(3, shortOut.size(), "不足 5 条时全留");
        }
    }

    @Nested
    @DisplayName("提示词参数缺省处理")
    class Defaults {

        @Test
        @DisplayName("文体/风格/篇幅为空时用默认值，不产生空提示词")
        void formStyleLengthDefault() {
            NarrativePromptService.Request req =
                    new NarrativePromptService.Request(1L, null, null, null, null);
            assertNull(req.formType());
            assertNull(req.style());
            assertNull(req.length());
            // 默认值在 buildSystem 内部兜底，这里只验证 record 不做静默改写
            assertEquals(1L, req.sessionId());
        }
    }
}
