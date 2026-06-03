package com.portfolio.musictracker.dto;

import java.time.LocalDate;

/**
 * 納期が解釈できた曲1件分の情報。カレンダー表示・リマインダー判定の両方で使う。
 *
 * @param id           曲ID
 * @param title        曲名
 * @param deadlineRaw  元の納期文字列（例: "6/4"）
 * @param date         解釈後の納期日
 * @param daysUntil    今日から納期までの日数（負数なら納期超過、0なら当日）
 */
public record SongDeadline(Long id, String title, String deadlineRaw, LocalDate date, long daysUntil) {

    /** 納期を過ぎている。 */
    public boolean isOverdue() {
        return daysUntil < 0;
    }
}
