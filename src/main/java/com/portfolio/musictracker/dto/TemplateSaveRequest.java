package com.portfolio.musictracker.dto;

import com.portfolio.musictracker.dto.SongDetailForm.SectionDto;

import java.util.ArrayList;
import java.util.List;

/**
 * 「現在の構成をテンプレートとして保存」リクエスト。
 * 画面上のセクション一覧（表示順）をそのままテンプレートのブロック構成として保存する。
 */
public class TemplateSaveRequest {

    /** テンプレート名（例:「王道ポップス構成」）。 */
    private String name;

    /** 対象エリア。"LYRIC" または "CHORD"。 */
    private String type;

    /** 保存するブロック一覧（表示順）。 */
    private List<SectionDto> sections = new ArrayList<>();

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public List<SectionDto> getSections() {
        return sections;
    }

    public void setSections(List<SectionDto> sections) {
        this.sections = sections;
    }
}
