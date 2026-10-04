package com.portfolio.musictracker.controller;

import com.portfolio.musictracker.demo.DemoAccountService;
import com.portfolio.musictracker.security.CustomUserDetails;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * すべての画面（Thymeleaf）に共通のモデル属性を供給する。
 * <ul>
 *     <li>{@code currentUsername}: ヘッダーのユーザー名表示・ログアウト導線</li>
 *     <li>{@code demoUser} / {@code demoTtlHours}: お試しアカウントで使っていることの表示</li>
 * </ul>
 */
@ControllerAdvice(basePackageClasses = SongController.class)
public class GlobalModelAttributes {

    private final DemoAccountService demoAccountService;

    public GlobalModelAttributes(DemoAccountService demoAccountService) {
        this.demoAccountService = demoAccountService;
    }

    @ModelAttribute("currentUsername")
    public String currentUsername(@AuthenticationPrincipal CustomUserDetails principal) {
        return (principal == null) ? null : principal.getUsername();
    }

    @ModelAttribute("demoUser")
    public boolean demoUser(@AuthenticationPrincipal CustomUserDetails principal) {
        return principal != null && principal.getUser().isDemo();
    }

    @ModelAttribute("demoTtlHours")
    public int demoTtlHours() {
        return demoAccountService.getTtlHours();
    }
}
