package com.portfolio.musictracker.entity;

/**
 * セクション構成テンプレートが対象とするエリアの種別。
 * 歌詞エリアとコードエリアは独立して構成を管理するため、テンプレートもエリアごとに分ける。
 */
public enum SectionAreaType {
    /** 歌詞エリア。 */
    LYRIC,
    /** コードエリア。 */
    CHORD
}
