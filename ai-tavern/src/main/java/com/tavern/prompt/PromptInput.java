package com.tavern.prompt;

import com.tavern.entity.WorldInfoEntry;

import java.util.List;

/**
 * 一次提示词构建的输入。
 *
 * <p>刻意做成「数据进、数据出」：引擎不碰数据库、不碰 Spring 容器，
 * 这样才能用普通单测覆盖，不会因为没连 MySQL 就跑不起来。
 */
public record PromptInput(
        String charName,
        String userName,
        String description,
        String personality,
        String background,
        String exampleDialogue,
        String tags,
        List<Turn> history,
        /** 作者注释内容，空表示这轮不插 */
        String authorNote,
        /** 插到倒数第几条消息之前，0 = 末尾 */
        int authorNoteDepth,
        /** 每 N 条用户消息插一次，0 = 从不 */
        int authorNoteFrequency,
        /** 用户已经说了多少句，用于算频率 */
        long userTurnCount,
        List<WorldInfoEntry> worldEntries,
        /** 世界书扫描最近几条消息 */
        int scanDepth,
        /** 输入侧 token 预算 */
        int maxInputTokens,
        /** 输出预留 token */
        int maxOutputTokens
) {
    public record Turn(String role, String content) {
    }

    public static Builder builder() {
        return new Builder();
    }

    /** 参数多，给个 builder，调用处读起来清楚些 */
    public static final class Builder {
        private String charName;
        private String userName;
        private String description;
        private String personality;
        private String background;
        private String exampleDialogue;
        private String tags;
        private List<Turn> history = List.of();
        private String authorNote;
        private int authorNoteDepth = 0;
        private int authorNoteFrequency = 1;
        private long userTurnCount = 1;
        private List<WorldInfoEntry> worldEntries = List.of();
        private int scanDepth = 4;
        private int maxInputTokens = 6000;
        private int maxOutputTokens = 800;

        public Builder character(String name, String desc, String persona, String bg,
                                 String example, String tags) {
            this.charName = name;
            this.description = desc;
            this.personality = persona;
            this.background = bg;
            this.exampleDialogue = example;
            this.tags = tags;
            return this;
        }

        public Builder user(String name) {
            this.userName = name;
            return this;
        }

        public Builder history(List<Turn> h) {
            this.history = h == null ? List.of() : h;
            return this;
        }

        public Builder authorNote(String note, int depth, int frequency, long userTurnCount) {
            this.authorNote = note;
            this.authorNoteDepth = depth;
            this.authorNoteFrequency = frequency;
            this.userTurnCount = userTurnCount;
            return this;
        }

        public Builder world(List<WorldInfoEntry> entries, int scanDepth) {
            this.worldEntries = entries == null ? List.of() : entries;
            this.scanDepth = scanDepth;
            return this;
        }

        public Builder budget(int maxInputTokens, int maxOutputTokens) {
            this.maxInputTokens = maxInputTokens;
            this.maxOutputTokens = maxOutputTokens;
            return this;
        }

        public PromptInput build() {
            return new PromptInput(charName, userName, description, personality, background,
                    exampleDialogue, tags, history, authorNote, authorNoteDepth,
                    authorNoteFrequency, userTurnCount, worldEntries, scanDepth,
                    maxInputTokens, maxOutputTokens);
        }
    }
}
