package com.portfolio.musictracker.dto;

import com.portfolio.musictracker.dto.SongDetailForm.SectionDto;

import java.util.ArrayList;
import java.util.List;

/**
 * 歌詞の書き出しリクエスト。画面に表示中の歌詞セクション（未保存の編集も含む）を表示順で送る。
 * Key・BPM も画面で選んでいる値を送る（省略時は曲に保存済みの値）。
 */
public class LyricsExportRequest {

    private List<SectionDto> sections = new ArrayList<>();
    private String musicKey;
    private Integer bpm;

    public List<SectionDto> getSections() {
        return sections;
    }

    public void setSections(List<SectionDto> sections) {
        this.sections = sections;
    }

    public String getMusicKey() {
        return musicKey;
    }

    public void setMusicKey(String musicKey) {
        this.musicKey = musicKey;
    }

    public Integer getBpm() {
        return bpm;
    }

    public void setBpm(Integer bpm) {
        this.bpm = bpm;
    }
}
