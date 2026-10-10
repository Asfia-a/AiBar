package com.tavern.controller;

import com.tavern.common.Result;
import com.tavern.dto.CharacterRequest;
import com.tavern.interceptor.UserContext;
import com.tavern.service.CharacterService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class CharacterController {

    private final CharacterService characterService;

    @GetMapping("/characters")
    public Result<?> list(@RequestParam(required = false) Long categoryId,
                          @RequestParam(required = false) Long tagId,
                          @RequestParam(required = false) String keyword,
                          @RequestParam(required = false) Long creatorId,
                          @RequestParam(defaultValue = "1") int page,
                          @RequestParam(defaultValue = "12") int size) {
        Integer status = creatorId != null && creatorId.equals(UserContext.getUserId()) ? null : 2;
        if (creatorId != null && UserContext.getUserId() != null && creatorId.equals(UserContext.getUserId())) {
            status = null;
        } else if (creatorId == null) {
            status = 2;
        }
        return Result.ok(characterService.list(status, categoryId, tagId, keyword, creatorId, page, size));
    }

    @GetMapping("/characters/hot")
    public Result<?> hot(@RequestParam(defaultValue = "20") int limit) {
        return Result.ok(characterService.hot(limit));
    }

    @GetMapping("/characters/new")
    public Result<?> newest(@RequestParam(defaultValue = "20") int limit) {
        return Result.ok(characterService.newest(limit));
    }

    @GetMapping("/characters/search")
    public Result<?> search(@RequestParam String keyword,
                            @RequestParam(defaultValue = "1") int page,
                            @RequestParam(defaultValue = "12") int size) {
        return Result.ok(characterService.search(keyword, page, size));
    }

    @GetMapping("/characters/{id}")
    public Result<?> detail(@PathVariable Long id) {
        return Result.ok(characterService.detail(id, UserContext.getUserId()));
    }

    @PostMapping("/characters")
    public Result<?> create(@Valid @RequestBody CharacterRequest request) {
        return Result.ok(characterService.create(UserContext.getUserId(), request));
    }

    @PutMapping("/characters/{id}")
    public Result<?> update(@PathVariable Long id, @Valid @RequestBody CharacterRequest request) {
        return Result.ok(characterService.update(UserContext.getUserId(), id, request));
    }

    @PostMapping("/characters/{id}/publish")
    public Result<?> publish(@PathVariable Long id) {
        characterService.publish(UserContext.getUserId(), id);
        return Result.ok();
    }

    @DeleteMapping("/characters/{id}")
    public Result<?> delete(@PathVariable Long id) {
        characterService.delete(UserContext.getUserId(), id);
        return Result.ok();
    }

    @PostMapping("/characters/{id}/replica")
    public Result<?> replica(@PathVariable Long id) {
        return Result.ok(characterService.replica(UserContext.getUserId(), id));
    }

    @PostMapping("/characters/{id}/favorite")
    public Result<?> favorite(@PathVariable Long id) {
        return Result.ok(characterService.toggleFavorite(UserContext.getUserId(), id));
    }

    @PostMapping("/characters/{id}/rate")
    public Result<?> rate(@PathVariable Long id, @RequestBody Map<String, Integer> body) {
        return Result.ok(characterService.rate(UserContext.getUserId(), id, body.getOrDefault("score", 5)));
    }

    @GetMapping("/characters/{id}/comments")
    public Result<?> comments(@PathVariable Long id,
                              @RequestParam(defaultValue = "1") int page,
                              @RequestParam(defaultValue = "20") int size) {
        return Result.ok(characterService.comments(id, page, size));
    }

    @PostMapping("/characters/{id}/comments")
    public Result<?> addComment(@PathVariable Long id, @RequestBody Map<String, String> body) {
        return Result.ok(characterService.addComment(UserContext.getUserId(), id, body.get("content")));
    }

    @GetMapping("/favorites")
    public Result<?> favorites() {
        return Result.ok(characterService.favorites(UserContext.getUserId()));
    }

    @GetMapping("/categories")
    public Result<?> categories() {
        return Result.ok(characterService.categories());
    }

    @GetMapping("/tags")
    public Result<?> tags() {
        return Result.ok(characterService.tags());
    }
}
