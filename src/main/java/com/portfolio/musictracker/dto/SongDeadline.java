package com.portfolio.musictracker.dto;

import java.time.LocalDate;

/**
 * 納期が解釈できた曲1件分の情報。一覧・カレンダー表示・リマインダー判定で使う。
 *
 * @param id           曲ID
 * @param title        曲名
 * @param deadlineRaw  元の納期文字列（例: "2026-06-04"、以前の自由入力なら "6/4"）
 * @param date         解釈後の納期日
 * @param daysUntil    今日から納期までの日数（負数なら納期を過ぎている、0なら当日）
 * @param done         納期の対応済みチェックが付いているか
 */
public record SongDeadline(Long id, String title, String deadlineRaw, LocalDate date, long daysUntil,
                           boolean done) {

    /** 納期を過ぎていて、まだ対応済みになっていない（警告の対象）。 */
    public boolean isOverdue() {
        return daysUntil < 0 && !done;
    }

    /** 今日から {@code withinDays} 日以内に納期が来る、未対応の曲か。 */
    public boolean isDueWithin(int withinDays) {
        return !done && daysUntil >= 0 && daysUntil <= withinDays;
    }
}
