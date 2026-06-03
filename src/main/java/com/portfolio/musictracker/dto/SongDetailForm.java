package com.portfolio.musictracker.dto;

import java.util.ArrayList;
import java.util.List;

/**
 * 作曲コア画面の「変更を保存」で送られてくる一括保存リクエスト。
 * <p>
 * 歌詞セクション・コードセクションはそれぞれ画面上の表示順で並んだ配列として送られ、
 * 配列の並び順がそのまま {@code sortOrder} になる。
 */
public class SongDetailForm {

    private Integer bpm;
    private String musicKey;
    private String worldViewMemo;
    private int lyricProgress;
    private int melodyProgress;
    private int arrangementProgress;

    private List<SectionDto> lyricSections = new ArrayList<>();
    private List<SectionDto> chordSections = new ArrayList<>();

    /** 1セクション分（歌詞 or コード）のデータ。 */
    public static class SectionDto {
        /** 既存セクションのID。新規追加分は null。 */
        private Long id;
        private String name;
        private String content;
        /** コードセクション個別の Key（転調用）。歌詞セクションでは未使用。 */
        private String sectionKey;

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

        public String getSectionKey() {
            return sectionKey;
        }

        public void setSectionKey(String sectionKey) {
            this.sectionKey = sectionKey;
        }
    }

    public Integer getBpm() {
        return bpm;
    }

    public void setBpm(Integer bpm) {
        this.bpm = bpm;
    }

    public String getMusicKey() {
        return musicKey;
    }

    public void setMusicKey(String musicKey) {
        this.musicKey = musicKey;
    }

    public String getWorldViewMemo() {
        return worldViewMemo;
    }

    public void setWorldViewMemo(String worldViewMemo) {
        this.worldViewMemo = worldViewMemo;
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

    public List<SectionDto> getChordSections() {
        return chordSections;
    }

    public void setChordSections(List<SectionDto> chordSections) {
        this.chordSections = chordSections;
    }
}
