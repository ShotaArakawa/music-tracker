package com.portfolio.musictracker.dto;

import java.util.List;

/**
 * リマインダーバッチの実行結果サマリ。
 *
 * @param overdueCount 納期超過の件数
 * @param soonCount    期限が近い（数日以内）件数
 * @param items        対象の曲一覧（納期日の昇順）
 */
public record ReminderSummary(long overdueCount, long soonCount, List<SongDeadline> items) {
}
