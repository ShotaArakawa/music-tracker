package com.portfolio.musictracker.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class DeadlineParserTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 6, 8);

    @ParameterizedTest
    @ValueSource(strings = {"2026/7/15", "2026-07-15", "2026.7.15", "26/7/15", "2026年7月15日", "7/15", "7月15日"})
    void 様々な表記を解釈できる(String raw) {
        assertThat(DeadlineParser.parse(raw, TODAY)).contains(LocalDate.of(2026, 7, 15));
    }

    @Test
    void 月日だけで半年以上前なら来年とみなす() {
        assertThat(DeadlineParser.parse("1/10", LocalDate.of(2026, 12, 20)))
                .contains(LocalDate.of(2027, 1, 10));
    }

    @Test
    void 月日だけで半年以内の過去なら今年のまま() {
        assertThat(DeadlineParser.parse("5/20", TODAY)).contains(LocalDate.of(2026, 5, 20));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "  ", "未定", "2/30", "13/1", "6"})
    void 解釈できない値は空(String raw) {
        assertThat(DeadlineParser.parse(raw, TODAY)).isEmpty();
    }

    @Test
    void nullは空() {
        assertThat(DeadlineParser.parse(null, TODAY)).isEqualTo(Optional.empty());
    }
}
