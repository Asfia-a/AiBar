package com.tavern.controller;

import com.tavern.common.Result;
import com.tavern.dto.request.WorldInfoRequest;
import com.tavern.interceptor.UserContext;
import com.tavern.service.WorldInfoAdminService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 世界书管理接口。
 *
 * <p>路径分两组：挂在角色下面的（角色条目）和挂在 /worldbook 下的（按条目 id 操作 + 全局条目）。
 * 所有写操作都会在 Service 里校验「这个角色/条目是不是你的」，Controller 只管收参。
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class WorldInfoController {

    private final WorldInfoAdminService worldInfoAdminService;

    @GetMapping("/characters/{characterId}/worldbook")
    public Result<?> list(@PathVariable Long characterId) {
        return Result.ok(worldInfoAdminService.list(UserContext.getUserId(), characterId));
    }

    @PostMapping("/characters/{characterId}/worldbook")
    public Result<?> create(@PathVariable Long characterId, @Valid @RequestBody WorldInfoRequest request) {
        return Result.ok(worldInfoAdminService.create(UserContext.getUserId(), characterId, request));
    }

    /** 命中预演：给一段文本，返回这一轮会插入哪些条目 */
    @PostMapping("/characters/{characterId}/worldbook/preview")
    public Result<?> preview(@PathVariable Long characterId, @RequestBody Map<String, Object> body) {
        String text = body.get("text") == null ? "" : body.get("text").toString();
        Integer budget = body.get("budget") == null ? null : Integer.valueOf(body.get("budget").toString());
        return Result.ok(worldInfoAdminService.preview(UserContext.getUserId(), characterId, text, budget));
    }

    /** 导出：按角色打包条目，前端下载成 JSON 文件 */
    @GetMapping("/characters/{characterId}/worldbook/export")
    public Result<?> export(@PathVariable Long characterId) {
        return Result.ok(worldInfoAdminService.export(UserContext.getUserId(), characterId));
    }

    /** 导入：吃本系统导出的 JSON 结构；脏条目跳过，返回导入 / 跳过计数 */
    @PostMapping("/characters/{characterId}/worldbook/import")
    @SuppressWarnings("unchecked")
    public Result<?> importEntries(@PathVariable Long characterId, @RequestBody Map<String, Object> body) {
        Object raw = body.get("entries");
        // 数组里混进非对象元素（数字/字符串）直接当脏数据，别让强转炸整批
        List<Map<String, Object>> items = raw instanceof List<?> list
                ? list.stream().filter(v -> v instanceof Map).map(v -> (Map<String, Object>) v).toList()
                : List.of();
        return Result.ok(worldInfoAdminService.importEntries(UserContext.getUserId(), characterId, items));
    }

    /** 拖拽排序：ids 按界面上的新顺序给出 */
    @PutMapping("/characters/{characterId}/worldbook/reorder")
    public Result<?> reorder(@PathVariable Long characterId, @RequestBody Map<String, Object> body) {
        Object raw = body.get("ids");
        List<Long> ids = raw instanceof List<?> list
                ? list.stream().map(v -> Long.valueOf(String.valueOf(v))).toList()
                : List.of();
        worldInfoAdminService.reorder(UserContext.getUserId(), characterId, ids);
        return Result.ok();
    }

    @PostMapping("/worldbook/global")
    public Result<?> createGlobal(@Valid @RequestBody WorldInfoRequest request) {
        return Result.ok(worldInfoAdminService.createGlobal(UserContext.getUserId(), request));
    }

    @PutMapping("/worldbook/{id}")
    public Result<?> update(@PathVariable Long id, @Valid @RequestBody WorldInfoRequest request) {
        return Result.ok(worldInfoAdminService.update(UserContext.getUserId(), id, request));
    }

    @PostMapping("/worldbook/{id}/toggle")
    public Result<?> toggle(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        boolean enabled = body.get("enabled") == null || Boolean.parseBoolean(body.get("enabled").toString());
        worldInfoAdminService.toggle(UserContext.getUserId(), id, enabled);
        return Result.ok();
    }

    @DeleteMapping("/worldbook/{id}")
    public Result<?> delete(@PathVariable Long id) {
        worldInfoAdminService.delete(UserContext.getUserId(), id);
        return Result.ok();
    }
}
