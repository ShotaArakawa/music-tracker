package com.portfolio.musictracker.config;

import com.portfolio.musictracker.entity.Song;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Key・BPM が未設定の曲に既定値（C メジャー・120）を入れる、起動時の整合処理。
 * <p>
 * 既定値を持たせる前に作られた曲のための処理。未設定の行だけを更新するため、何度実行しても安全。
 */
@Component
@Order(4)
public class SongDefaultsMigration implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SongDefaultsMigration.class);

    private final JdbcTemplate jdbc;

    public SongDefaultsMigration(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void run(ApplicationArguments args) {
        int bpm = jdbc.update("UPDATE songs SET bpm = ? WHERE bpm IS NULL", Song.DEFAULT_BPM);
        int key = jdbc.update("UPDATE songs SET music_key = ? WHERE music_key IS NULL OR TRIM(music_key) = ''",
                Song.DEFAULT_KEY);
        if (bpm > 0 || key > 0) {
            log.info("[Migration] 未設定の BPM {} 曲・Key {} 曲に既定値を入れました", bpm, key);
        }
    }
}
