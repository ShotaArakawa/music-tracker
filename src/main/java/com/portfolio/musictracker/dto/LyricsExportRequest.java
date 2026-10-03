package com.portfolio.musictracker.dto;

import com.portfolio.musictracker.dto.SongDetailForm.SectionDto;

import java.util.ArrayList;
import java.util.List;

/**
 * 歌詞の書き出しリクエスト。画面に表示中の歌詞セクション（未保存の編集も含む）を表示順で送る。
 */
public class LyricsExportRequest {

    private List<SectionDto> sections = new ArrayList<>();

    public List<SectionDto> getSections() {
        return sections;
    }

    public void setSections(List<SectionDto> sections) {
        this.sections = sections;
    }
}
