package com.tavern.config;

import com.tavern.service.ChatService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class PromptSnapshotMigrator implements ApplicationRunner {

    private final ChatService chatService;

    @Override
    public void run(ApplicationArguments args) {
        try {
            int n = chatService.backfillMissingSnapshots();
            if (n > 0) {
                log.info("已为 {} 个历史会话冻结角色人设快照", n);
            }
        } catch (Exception e) {
            log.warn("会话人设快照迁移跳过: {}", e.getMessage());
        }
    }
}
