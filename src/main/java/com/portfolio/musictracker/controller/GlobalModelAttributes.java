package com.portfolio.musictracker.controller;

import com.portfolio.musictracker.security.CustomUserDetails;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * すべての画面（Thymeleaf）に共通のモデル属性を供給する。
 * ヘッダーのユーザー名表示・ログアウト導線で使う {@code currentUsername} を渡す。
 */
@ControllerAdvice(basePackageClasses = SongController.class)
public class GlobalModelAttributes {

    @ModelAttribute("currentUsername")
    public String currentUsername(@AuthenticationPrincipal CustomUserDetails principal) {
        return (principal == null) ? null : principal.getUsername();
    }
}
