package com.portfolio.musictracker.chordchart;

import java.util.Arrays;
import java.util.Locale;

/**
 * コード譜の白紙テンプレート（src/main/resources/chord-templates の Excel）。
 */
public enum ChordChartTemplate {

    TWO_PER_BAR("two-per-bar.xlsx", "テンプレート① 1小節2マス", "B2", "H3"),
    FOUR_PER_BAR("four-per-bar.xlsx", "テンプレート② 1小節4マス", "B2", "N3");

    private final String fileName;
    private final String label;
    /** タイトル（「アーティスト / タイトル」）を入れるセル。 */
    private final String titleCell;
    /** 「Key：C　BPM：000」を入れるセル。 */
    private final String keyBpmCell;

    ChordChartTemplate(String fileName, String label, String titleCell, String keyBpmCell) {
        this.fileName = fileName;
        this.label = label;
        this.titleCell = titleCell;
        this.keyBpmCell = keyBpmCell;
    }

    public String resourcePath() {
        return "/chord-templates/" + fileName;
    }

    public String getLabel() {
        return label;
    }

    public String getTitleCell() {
        return titleCell;
    }

    public String getKeyBpmCell() {
        return keyBpmCell;
    }

    /** 画面から送られる名前（two-per-bar / TWO_PER_BAR など）を解釈する。 */
    public static ChordChartTemplate parse(String name) {
        String n = name == null ? "" : name.trim().toUpperCase(Locale.ROOT).replace('-', '_');
        return Arrays.stream(values()).filter(t -> t.name().equals(n)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("不明なテンプレートです: " + name));
    }
}
