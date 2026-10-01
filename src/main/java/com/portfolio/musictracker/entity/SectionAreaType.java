package com.portfolio.musictracker.entity;

/**
 * セクション構成テンプレートが対象とするエリアの種別。
 */
public enum SectionAreaType {
    /** 歌詞エリア。 */
    LYRIC,
    /**
     * 旧コード進行エリア（廃止）。コード譜は表形式に置き換えたため新規作成・一覧には使わない。
     * 以前に保存されたテンプレートを読み込めるよう値だけ残している。
     */
    CHORD
}
