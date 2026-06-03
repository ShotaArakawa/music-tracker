package com.portfolio.musictracker.controller;

import com.portfolio.musictracker.dto.SignupForm;
import com.portfolio.musictracker.service.UserService;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * ログイン画面・ユーザー登録画面を扱うコントローラ。
 * （ログイン処理自体は Spring Security が /login の POST を受け持つ）
 */
@Controller
public class AuthController {

    private final UserService userService;

    public AuthController(UserService userService) {
        this.userService = userService;
    }

    /** ログイン画面。 */
    @GetMapping("/login")
    public String login() {
        return "auth/login";
    }

    /** ユーザー登録画面。 */
    @GetMapping("/signup")
    public String signupForm(Model model) {
        if (!model.containsAttribute("signupForm")) {
            model.addAttribute("signupForm", new SignupForm());
        }
        return "auth/signup";
    }

    /** ユーザー登録の実行。 */
    @PostMapping("/signup")
    public String signup(@Valid @ModelAttribute("signupForm") SignupForm form,
                         BindingResult bindingResult,
                         RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            return "auth/signup";
        }
        try {
            userService.register(form);
        } catch (IllegalArgumentException e) {
            bindingResult.reject("signup.failed", e.getMessage());
            return "auth/signup";
        }
        redirectAttributes.addFlashAttribute("message", "登録が完了しました。ログインしてください。");
        return "redirect:/login";
    }
}
