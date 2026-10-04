package com.portfolio.musictracker.controller;

import com.portfolio.musictracker.demo.DemoAccountService;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class LandingController {

    private final DemoAccountService demoAccountService;

    public LandingController(DemoAccountService demoAccountService) {
        this.demoAccountService = demoAccountService;
    }

    @GetMapping("/")
    public String index(Authentication authentication, Model model) {
        if (authentication != null && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken)) {
            return "redirect:/songs";
        }
        // 「テストユーザーで試してみる」を表示するか
        model.addAttribute("demoEnabled", demoAccountService.isEnabled());
        return "landing";
    }
}
