package com.portfolio.musictracker.task;

import com.portfolio.musictracker.dto.ReminderSummary;
import com.portfolio.musictracker.dto.SongDeadline;
import com.portfolio.musictracker.service.ScheduleService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 納期リマインダーの定期バッチ。
 * <p>
 * 納期が迫っている／超過している曲を抽出してログ出力する。
 * 実運用向けに毎日9時の cron を設定しつつ、起動・動作確認用に
 * アプリ起動直後にも一度だけ走るデモ実行を併設している。
 * 手動実行は {@code GET /reminders/run}（{@link com.portfolio.musictracker.controller.ReminderController}）から行える。
 */
@Component
public class ReminderTask {

    private static final Logger log = LoggerFactory.getLogger(ReminderTask.class);

    private final ScheduleService scheduleService;

    public ReminderTask(ScheduleService scheduleService) {
        this.scheduleService = scheduleService;
    }

    /** 毎日 9:00 に実行（実運用向け）。 */
    @Scheduled(cron = "0 0 9 * * *")
    public void daily() {
        run();
    }

    /** 起動確認・デモ用：起動3秒後に1回、その後10分ごとに実行。 */
    @Scheduled(initialDelay = 3000, fixedRate = 600000)
    public void demo() {
        run();
    }

    /** 全ユーザー横断でリマインダー対象を集計してログ出力し、サマリを返す（サーバーバッチ）。 */
    public ReminderSummary run() {
        List<SongDeadline> alerts = scheduleService.findAlertsGlobal();
        long overdue = alerts.stream().filter(SongDeadline::isOverdue).count();
        long soon = alerts.size() - overdue;
        log.info("[Reminder] 納期チェック実行: 超過 {} 件 / {}日以内 {} 件",
                overdue, ScheduleService.ALERT_WITHIN_DAYS, soon);
        for (SongDeadline a : alerts) {
            log.info("[Reminder]   - {} (納期: {}, {})",
                    a.title(), a.deadlineRaw(),
                    a.isOverdue() ? (-a.daysUntil()) + "日超過" : a.daysUntil() + "日後");
        }
        return new ReminderSummary(overdue, soon, alerts);
    }
}
