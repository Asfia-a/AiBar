package com.tavern.controller;

import com.tavern.common.Result;
import com.tavern.interceptor.UserContext;
import com.tavern.service.CharacterAssistService;
import com.tavern.service.InspirationAssistService;
import com.tavern.service.WorldTreeAssistService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/workshop")
@RequiredArgsConstructor
public class WorkshopController {

    private final CharacterAssistService characterAssistService;
    private final WorldTreeAssistService worldTreeAssistService;
    private final InspirationAssistService inspirationAssistService;

    /** AI 生成角色卡草稿（可选生图），可直接填表或导出 ST JSON */
    @PostMapping("/character/generate")
    public Result<?> generate(@RequestBody Map<String, Object> body) {
        String idea = str(body.get("idea"));
        String worldContext = str(body.get("worldContext"));
        boolean withAvatar = body.get("withAvatar") == null || Boolean.TRUE.equals(body.get("withAvatar"));
        String modelName = str(body.get("modelName"));
        return Result.ok(characterAssistService.generateCard(idea, worldContext, withAvatar, modelName));
    }

    /** 仅生立绘并上传 OSS */
    @PostMapping("/character/avatar")
    public Result<?> avatar(@RequestBody Map<String, Object> body) {
        return Result.ok(characterAssistService.generateAvatar(str(body.get("prompt")), str(body.get("name"))));
    }

    /** 生成并保存为「我的角色」草稿 */
    @PostMapping("/character/save")
    public Result<?> save(@RequestBody Map<String, Object> body) {
        String idea = str(body.get("idea"));
        String worldContext = str(body.get("worldContext"));
        boolean withAvatar = body.get("withAvatar") == null || Boolean.TRUE.equals(body.get("withAvatar"));
        String modelName = str(body.get("modelName"));
        return Result.ok(characterAssistService.generateAndSave(
                UserContext.getUserId(), idea, worldContext, withAvatar, modelName));
    }

    /** AI 根据自然语言一键生成世界树 */
    @PostMapping("/world-tree/generate")
    public Result<?> generateWorldTree(@RequestBody Map<String, Object> body) {
        int depth = 3;
        if (body.get("depth") instanceof Number n) {
            depth = n.intValue();
        } else if (body.get("depth") != null) {
            try {
                depth = Integer.parseInt(String.valueOf(body.get("depth")));
            } catch (NumberFormatException ignored) {
                /* keep default */
            }
        }
        return Result.ok(worldTreeAssistService.generateTree(
                str(body.get("idea")),
                str(body.get("language")),
                depth));
    }

    /** AI 扩展世界树某一节点的子节点 */
    @PostMapping("/world-tree/expand")
    @SuppressWarnings("unchecked")
    public Result<?> expandWorldNode(@RequestBody Map<String, Object> body) {
        Map<String, Object> node = body.get("node") instanceof Map<?, ?> m
                ? (Map<String, Object>) m
                : Map.of();
        return Result.ok(worldTreeAssistService.expandNode(
                node,
                str(body.get("hint")),
                str(body.get("language"))));
    }

    /** AI 润色灵感文本（可带润色配置） */
    @PostMapping("/inspiration/polish")
    public Result<?> polishInspiration(@RequestBody Map<String, Object> body) {
        return Result.ok(inspirationAssistService.polish(body));
    }

    private static String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }
}
