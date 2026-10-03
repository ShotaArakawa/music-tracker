package com.portfolio.musictracker.entity;

/**
 * 曲の進捗ステータス。
 * <p>
 * label : 画面表示用の日本語名<br>
 * colorClass : ダッシュボードで色分けするための Bootstrap バッジ用クラス
 */
public enum Status {

    LYRICS_WRITING("作詞中", "bg-secondary"),
    MELODY_MAKING("作曲中", "bg-info"),
    ARRANGING("編曲中", "bg-primary"),
    DEMO_DONE("デモ完成", "bg-warning text-dark"),
    FULL_CHORUS_DONE("フルコーラス完成", "bg-success"),
    /** 完了。曲一覧の「完了」チェックと連動し、バックアップ一覧に移る。 */
    RELEASED("完了", "bg-dark");

    private final String label;
    private final String colorClass;

    Status(String label, String colorClass) {
        this.label = label;
        this.colorClass = colorClass;
    }

    public String getLabel() {
        return label;
    }

    public String getColorClass() {
        return colorClass;
    }
}
