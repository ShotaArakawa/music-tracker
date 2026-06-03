package com.portfolio.musictracker.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/**
 * 歌詞エリアのセクション。コードセクションとは独立して管理される。
 */
@Entity
@Table(name = "lyric_sections")
public class LyricSection extends AbstractSection {

    public LyricSection() {
        super();
    }

    public LyricSection(Song song, String name, int sortOrder) {
        super(song, name, sortOrder);
    }
}
