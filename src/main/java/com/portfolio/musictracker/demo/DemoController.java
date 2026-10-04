package com.portfolio.musictracker.demo;

import com.portfolio.musictracker.entity.User;
import com.portfolio.musictracker.security.CustomUserDetails;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * ランディングページの「テストユーザーで試してみる」。
 * お試しアカウントを作ってそのままログインし、曲一覧へ移動する。
 */
@Controller
public class DemoController {

    private final DemoAccountService demoAccountService;
    private final SecurityContextRepository securityContextRepository = new HttpSessionSecurityContextRepository();

    public DemoController(DemoAccountService demoAccountService) {
        this.demoAccountService = demoAccountService;
    }

    @PostMapping("/demo/start")
    public String start(HttpServletRequest request, HttpServletResponse response,
                        RedirectAttributes redirectAttributes) {
        User user;
        try {
            user = demoAccountService.create();
        } catch (DemoAccountService.DemoUnavailableException e) {
            redirectAttributes.addFlashAttribute("demoError", e.getMessage());
            return "redirect:/";
        }
        // ログイン前のセッションを引き継がない（セッション固定攻撃の対策）
        if (request.getSession(false) != null) {
            request.changeSessionId();
        }
        CustomUserDetails principal = new CustomUserDetails(user);
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                principal, null, principal.getAuthorities()));
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, request, response);
        return "redirect:/songs";
    }
}
