package com.portfolio.musictracker.service;

import java.time.LocalDate;
import java.util.Optional;

/**
 * 自由入力の納期文字列（「6/4」「2026/6/4」「2026-06-04」「6月4日」など）を
 * {@link LocalDate} に解釈するためのユーティリティ。
 * <p>
 * 年が省略された「月/日」形式の場合は、基準日（today）の年を採用する。
 * ただし半年以上過去になる場合は「来年の同じ月日」とみなす（年末またぎ対策）。
 */
public final class DeadlineParser {

    private DeadlineParser() {
    }

    public static Optional<LocalDate> parse(String raw, LocalDate today) {
        if (raw == null) {
            return Optional.empty();
        }
        String s = raw.trim();
        if (s.isEmpty()) {
            return Optional.empty();
        }
        // 日本語表記を区切り文字へ正規化（例: 2026年6月4日 → 2026/6/4）
        s = s.replace("年", "/").replace("月", "/").replace("日", "").trim();
        if (s.endsWith("/")) {
            s = s.substring(0, s.length() - 1);
        }
        String[] parts = s.split("[/\\-.]");
        try {
            if (parts.length == 3) {
                int year = Integer.parseInt(parts[0].trim());
                int month = Integer.parseInt(parts[1].trim());
                int day = Integer.parseInt(parts[2].trim());
                if (year < 100) {
                    year += 2000;
                }
                return Optional.of(LocalDate.of(year, month, day));
            }
            if (parts.length == 2) {
                int month = Integer.parseInt(parts[0].trim());
                int day = Integer.parseInt(parts[1].trim());
                LocalDate candidate = LocalDate.of(today.getYear(), month, day);
                // すでに半年以上過去なら、来年の納期とみなす
                if (candidate.isBefore(today.minusMonths(6))) {
                    candidate = candidate.plusYears(1);
                }
                return Optional.of(candidate);
            }
        } catch (RuntimeException e) {
            // 数値変換失敗・不正な日付などはパース不能として扱う
            return Optional.empty();
        }
        return Optional.empty();
    }
}
