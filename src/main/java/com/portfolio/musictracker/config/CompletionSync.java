package com.portfolio.musictracker.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 「完了」チェック（deadline_done）をステータス「完了」（RELEASED）にそろえる、起動時の整合処理。
 * <p>
 * 連動させる前に作られたデータ（ステータスは「完了」だがチェックなし、など）を直す。
 * そろっている行は更新しないため、何度実行しても安全。
 */
@Component
@Order(3)
public class CompletionSync implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(CompletionSync.class);

    private final JdbcTemplate jdbc;

    public CompletionSync(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void run(ApplicationArguments args) {
        int fixed = jdbc.update("UPDATE songs SET deadline_done = (status = 'RELEASED') "
                + "WHERE deadline_done <> (status = 'RELEASED')");
        if (fixed > 0) {
            log.info("[Migration] 完了チェックをステータスに合わせて {} 曲分そろえました", fixed);
        }
    }
}
