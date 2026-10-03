package com.portfolio.musictracker.dto;

import com.portfolio.musictracker.chordchart.ChordSheet;

import java.util.ArrayList;
import java.util.List;

/**
 * 作曲コア画面の「変更を保存」で送られてくる一括保存リクエスト。
 * <p>
 * 歌詞セクションは画面上の表示順で並んだ配列として送られ、配列の並び順がそのまま {@code sortOrder} になる。
 * コード譜は表のデータ全体（{@link ChordSheet}）を送る。null はコード譜なし（削除）を表す。
 * BPM・Key・世界観はスタジオ画面から外したため受け取らない（保存済みの値はそのまま残る）。
 */
public class SongDetailForm {

    private int lyricProgress;
    private int melodyProgress;
    private int arrangementProgress;

    private List<SectionDto> lyricSections = new ArrayList<>();
    private ChordSheet chordSheet;

    /** 歌詞セクション1件分のデータ。 */
    public static class SectionDto {
        /** 既存セクションのID。新規追加分は null。 */
        private Long id;
        private String name;
        private String content;

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getContent() {
            return content;
        }

        public void setContent(String content) {
            this.content = content;
        }
    }

    public int getLyricProgress() {
        return lyricProgress;
    }

    public void setLyricProgress(int lyricProgress) {
        this.lyricProgress = lyricProgress;
    }

    public int getMelodyProgress() {
        return melodyProgress;
    }

    public void setMelodyProgress(int melodyProgress) {
        this.melodyProgress = melodyProgress;
    }

    public int getArrangementProgress() {
        return arrangementProgress;
    }

    public void setArrangementProgress(int arrangementProgress) {
        this.arrangementProgress = arrangementProgress;
    }

    public List<SectionDto> getLyricSections() {
        return lyricSections;
    }

    public void setLyricSections(List<SectionDto> lyricSections) {
        this.lyricSections = lyricSections;
    }

    public ChordSheet getChordSheet() {
        return chordSheet;
    }

    public void setChordSheet(ChordSheet chordSheet) {
        this.chordSheet = chordSheet;
    }
}
