package com.portfolio.musictracker.controller;

import com.portfolio.musictracker.entity.Tag;
import com.portfolio.musictracker.security.CustomUserDetails;
import com.portfolio.musictracker.service.TagService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 曲一覧画面のタグ管理（一覧・追加・削除）API。操作できるのはログインユーザー自身のタグのみ。
 */
@RestController
@RequestMapping("/tags")
public class TagController {

    private final TagService tagService;

    public TagController(TagService tagService) {
        this.tagService = tagService;
    }

    @GetMapping
    public List<Map<String, Object>> list(@AuthenticationPrincipal CustomUserDetails principal) {
        return tagService.findAll(principal.getUser()).stream().map(TagController::toMap).toList();
    }

    /** タグを追加する。リクエスト: {@code {"name": "アニソン"}} */
    @PostMapping
    public Map<String, Object> create(@RequestBody Map<String, String> body,
                                      @AuthenticationPrincipal CustomUserDetails principal) {
        return toMap(tagService.create(principal.getUser(), body.get("name")));
    }

    /** タグを削除する（付いている曲からも外れる）。 */
    @DeleteMapping("/{id}")
    public Map<String, Object> delete(@PathVariable Long id,
                                      @AuthenticationPrincipal CustomUserDetails principal) {
        tagService.delete(principal.getUser(), id);
        return Map.of("status", "ok");
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleBadRequest(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
    }

    private static Map<String, Object> toMap(Tag tag) {
        return Map.of("id", tag.getId(), "name", tag.getName());
    }
}
