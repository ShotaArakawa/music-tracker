package com.portfolio.musictracker.status;

import java.util.Arrays;
import java.util.Optional;

/**
 * ユーザーが追加するステータスに選べるバッジの色。
 * Bootstrap にない色（紫・ピンクなど）は共通 head の {@code st-*} クラスで定義している。
 */
public enum StatusColor {

    GRAY("グレー", "bg-secondary"),
    CYAN("水色", "bg-info"),
    BLUE("青", "bg-primary"),
    YELLOW("黄", "bg-warning text-dark"),
    GREEN("緑", "bg-success"),
    RED("赤", "bg-danger"),
    PURPLE("紫", "st-purple"),
    PINK("ピンク", "st-pink"),
    ORANGE("オレンジ", "st-orange"),
    TEAL("青緑", "st-teal");

    private final String label;
    private final String colorClass;

    StatusColor(String label, String colorClass) {
        this.label = label;
        this.colorClass = colorClass;
    }

    public String getLabel() {
        return label;
    }

    public String getColorClass() {
        return colorClass;
    }

    public static Optional<StatusColor> of(String name) {
        return Arrays.stream(values()).filter(c -> c.name().equals(name)).findFirst();
    }
}
