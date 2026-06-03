package com.portfolio.musictracker.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class HomeController {

    /** トップページはダッシュボードへリダイレクトする。 */
    @GetMapping("/")
    public String index() {
        return "redirect:/songs";
    }
}
