package com.tavern.controller.admin;

import com.tavern.common.Result;
import com.tavern.entity.ModelConfig;
import com.tavern.service.*;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final AdminDashboardService dashboardService;
    private final CharacterService characterService;
    private final UserService userService;
    private final ContentAuditService contentAuditService;
    private final AiModelService aiModelService;

    @GetMapping("/dashboard/stats")
    public Result<?> stats() {
        return Result.ok(dashboardService.stats());
    }

    @GetMapping("/dashboard/trend")
    public Result<?> trend(@RequestParam(defaultValue = "7") int days) {
        return Result.ok(dashboardService.trend(days));
    }

    @GetMapping({"/dashboard/hot-characters", "/dashboard/popular-characters"})
    public Result<?> hot(@RequestParam(defaultValue = "10") int limit) {
        return Result.ok(dashboardService.hotCharacters(limit));
    }

    @GetMapping({"/characters/audit", "/characters"})
    public Result<?> auditList(@RequestParam(defaultValue = "1") int page,
                               @RequestParam(defaultValue = "20") int size,
                               @RequestParam(defaultValue = "1", name = "pageSize") int pageSize,
                               @RequestParam(required = false) String status) {
        int realSize = size > 0 ? size : pageSize;
        Integer st = parseCharacterStatus(status);
        return Result.ok(characterService.auditList(page, realSize, st));
    }

    /** 管理端查看完整角色卡（含待审核） */
    @GetMapping("/characters/{id}")
    public Result<?> characterDetail(@PathVariable Long id) {
        return Result.ok(characterService.adminDetail(id));
    }

    @PutMapping("/characters/{id}/audit")
    public Result<?> audit(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        int st = Integer.parseInt(body.get("status").toString());
        String reason = body.get("reason") == null ? null : body.get("reason").toString();
        characterService.audit(id, st, reason);
        return Result.ok();
    }

    private Integer parseCharacterStatus(String status) {
        if (status == null || status.isBlank() || "pending".equalsIgnoreCase(status)) {
            return 1;
        }
        if ("approved".equalsIgnoreCase(status) || "online".equalsIgnoreCase(status) || "published".equalsIgnoreCase(status)) {
            return 2;
        }
        if ("offline".equalsIgnoreCase(status)) {
            return 3;
        }
        if ("rejected".equalsIgnoreCase(status) || "reject".equalsIgnoreCase(status)) {
            return 4;
        }
        if ("draft".equalsIgnoreCase(status)) {
            return 0;
        }
        try {
            return Integer.parseInt(status);
        } catch (NumberFormatException e) {
            return 1;
        }
    }

    @PostMapping("/characters/{id}/approve")
    public Result<?> approve(@PathVariable Long id) {
        characterService.audit(id, 2, null);
        return Result.ok();
    }

    @PostMapping("/characters/{id}/reject")
    public Result<?> reject(@PathVariable Long id, @RequestBody(required = false) Map<String, Object> body) {
        String reason = body == null || body.get("reason") == null ? null : body.get("reason").toString();
        characterService.audit(id, 4, reason);
        return Result.ok();
    }

    @PostMapping("/characters/{id}/offline")
    public Result<?> offline(@PathVariable Long id) {
        characterService.audit(id, 3, "管理员下架");
        return Result.ok();
    }

    @GetMapping("/users")
    public Result<?> users(@RequestParam(required = false) String keyword,
                           @RequestParam(defaultValue = "1") int page,
                           @RequestParam(defaultValue = "20") int size) {
        return Result.ok(userService.adminList(keyword, page, size));
    }

    @PutMapping("/users/{id}/ban")
    public Result<?> ban(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        int st = Integer.parseInt(body.getOrDefault("status", 0).toString());
        userService.banUser(id, st);
        if (body.containsKey("memberLevel")) {
            userService.setMemberLevel(id, Integer.parseInt(body.get("memberLevel").toString()));
        }
        return Result.ok();
    }

    @PutMapping("/users/{id}/membership")
    public Result<?> membership(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        userService.setMemberLevel(id, Integer.parseInt(body.get("memberLevel").toString()));
        return Result.ok();
    }

    @GetMapping("/violations")
    public Result<?> violations() {
        return Result.ok(contentAuditService.listViolations());
    }

    @GetMapping("/model/config")
    public Result<?> getModelConfig() {
        return Result.ok(aiModelService.getConfig());
    }

    @PutMapping("/model/config")
    public Result<?> updateModelConfig(@RequestBody ModelConfig config) {
        aiModelService.updateConfig(config);
        return Result.ok();
    }
}
