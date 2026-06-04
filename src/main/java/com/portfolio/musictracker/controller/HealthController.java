package com.portfolio.musictracker.controller;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * ヘルスチェック用エンドポイント。
 * <p>
 * Render / Aiven(MySQL) の無料枠はアイドルが続くとスリープするため、
 * UptimeRobot 等から定期的に叩いてアプリ本体と DB の寝落ちを防ぐ。
 * アプリだけでなく DB も起こすため、{@link JdbcTemplate} で
 * 超軽量な {@code SELECT 1} を実行して実際に DB へ触れる。
 */
@RestController
public class HealthController {

    private final JdbcTemplate jdbcTemplate;

    public HealthController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** アプリと DB を起こすためのヘルスチェック。認証不要（SecurityConfig で permitAll）。 */
    @GetMapping("/api/health")
    public String health() {
        // DB を起こすための超軽量クエリ。
        jdbcTemplate.queryForObject("SELECT 1", Integer.class);
        return "MusicTracker and Database are awake!";
    }
}
