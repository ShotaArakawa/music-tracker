package com.portfolio.musictracker.status;

import com.portfolio.musictracker.entity.CustomStatus;
import com.portfolio.musictracker.security.CustomUserDetails;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 曲一覧画面のステータス管理（追加・削除）API。操作できるのはログインユーザー自身が追加したステータスのみ。
 */
@RestController
@RequestMapping("/statuses")
public class StatusController {

    private final StatusService statusService;

    public StatusController(StatusService statusService) {
        this.statusService = statusService;
    }

    /** ステータスを追加する。リクエスト: {@code {"name": "ミックス中", "color": "PURPLE"}} */
    @PostMapping
    public Map<String, Object> create(@RequestBody Map<String, String> body,
                                      @AuthenticationPrincipal CustomUserDetails principal) {
        CustomStatus s = statusService.create(principal.getUser(), body.get("name"), body.get("color"));
        return Map.of("key", s.getKey(), "name", s.getName(), "colorClass", s.getColorClass());
    }

    /** 追加したステータスを削除する（このステータスだった曲は既定のステータスに戻る）。 */
    @DeleteMapping("/{id}")
    public Map<String, Object> delete(@PathVariable Long id,
                                      @AuthenticationPrincipal CustomUserDetails principal) {
        statusService.delete(principal.getUser(), id);
        return Map.of("status", "ok");
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleBadRequest(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
    }
}
