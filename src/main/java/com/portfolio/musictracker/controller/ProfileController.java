package com.portfolio.musictracker.controller;

import com.portfolio.musictracker.dto.PasswordChangeForm;
import com.portfolio.musictracker.dto.ProfileForm;
import com.portfolio.musictracker.entity.User;
import com.portfolio.musictracker.security.CustomUserDetails;
import com.portfolio.musictracker.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * ログインユーザー自身のプロフィール設定（基本情報・パスワード変更）。
 */
@Controller
public class ProfileController {

    private final UserService userService;
    private final SecurityContextRepository securityContextRepository =
            new HttpSessionSecurityContextRepository();

    public ProfileController(UserService userService) {
        this.userService = userService;
    }

    /** 設定画面。現在のユーザー情報はDBから最新を読み込む。 */
    @GetMapping("/profile")
    public String profile(@AuthenticationPrincipal CustomUserDetails principal, Model model) {
        User user = userService.findById(principal.getId());
        model.addAttribute("user", user);
        if (!model.containsAttribute("profileForm")) {
            ProfileForm form = new ProfileForm();
            form.setUsername(user.getUsername());
            form.setEmail(user.getEmail());
            model.addAttribute("profileForm", form);
        }
        if (!model.containsAttribute("passwordForm")) {
            model.addAttribute("passwordForm", new PasswordChangeForm());
        }
        return "profile";
    }

    /** 基本情報（ユーザー名・メール）の更新。 */
    @PostMapping("/profile/update")
    public String updateProfile(@AuthenticationPrincipal CustomUserDetails principal,
                                @Valid @ModelAttribute("profileForm") ProfileForm profileForm,
                                BindingResult bindingResult,
                                HttpServletRequest request, HttpServletResponse response,
                                Model model, RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            return reRender(principal, model);
        }
        try {
            User updated = userService.updateProfile(principal.getId(), profileForm);
            // ユーザー名変更を即座に反映するため、セッションの認証情報を更新する
            refreshAuthentication(updated, request, response);
        } catch (IllegalArgumentException e) {
            bindingResult.reject("profile.update.failed", e.getMessage());
            return reRender(principal, model);
        }
        redirectAttributes.addFlashAttribute("message", "基本情報を更新しました。");
        return "redirect:/profile";
    }

    /** パスワードの変更。 */
    @PostMapping("/profile/password")
    public String changePassword(@AuthenticationPrincipal CustomUserDetails principal,
                                 @Valid @ModelAttribute("passwordForm") PasswordChangeForm passwordForm,
                                 BindingResult bindingResult,
                                 Model model, RedirectAttributes redirectAttributes) {
        if (!passwordForm.isConfirmed()) {
            bindingResult.rejectValue("confirmPassword", "password.mismatch",
                    "新しいパスワードと確認用が一致しません");
        }
        if (bindingResult.hasErrors()) {
            return reRenderPassword(principal, model);
        }
        try {
            userService.changePassword(principal.getId(), passwordForm);
        } catch (IllegalArgumentException e) {
            bindingResult.reject("password.change.failed", e.getMessage());
            return reRenderPassword(principal, model);
        }
        redirectAttributes.addFlashAttribute("message", "パスワードを変更しました。");
        return "redirect:/profile";
    }

    /** 基本情報フォームのエラー再表示（パスワードフォームは空で用意）。 */
    private String reRender(CustomUserDetails principal, Model model) {
        model.addAttribute("user", userService.findById(principal.getId()));
        model.addAttribute("passwordForm", new PasswordChangeForm());
        return "profile";
    }

    /** パスワードフォームのエラー再表示（基本情報フォームは現在値で用意）。 */
    private String reRenderPassword(CustomUserDetails principal, Model model) {
        User user = userService.findById(principal.getId());
        model.addAttribute("user", user);
        ProfileForm form = new ProfileForm();
        form.setUsername(user.getUsername());
        form.setEmail(user.getEmail());
        model.addAttribute("profileForm", form);
        return "profile";
    }

    /** 更新後のユーザーで認証情報を作り直し、セッションへ保存する。 */
    private void refreshAuthentication(User user, HttpServletRequest request, HttpServletResponse response) {
        CustomUserDetails newPrincipal = new CustomUserDetails(user);
        Authentication newAuth = new UsernamePasswordAuthenticationToken(
                newPrincipal, newPrincipal.getPassword(), newPrincipal.getAuthorities());
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(newAuth);
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, request, response);
    }
}
