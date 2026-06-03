package com.portfolio.musictracker.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/**
 * コードエリアのセクション。歌詞セクションとは独立して管理される。
 * <p>
 * 楽曲全体の Key とは別に、セクション個別の Key（転調）を保持できる。
 * 例:「A メロは C、サビは D に転調」といった構成に対応する。
 * {@code sectionKey} が未設定（null/空）のセクションは、楽曲全体の Key を継承する。
 */
@Entity
@Table(name = "chord_sections")
public class ChordSection extends AbstractSection {

    /** このセクション個別の Key（例: C, Am, D Major）。未設定なら曲全体の Key を使う。 */
    @Column(length = 20)
    private String sectionKey;

    public ChordSection() {
        super();
    }

    public ChordSection(Song song, String name, int sortOrder) {
        super(song, name, sortOrder);
    }

    public String getSectionKey() {
        return sectionKey;
    }

    public void setSectionKey(String sectionKey) {
        this.sectionKey = sectionKey;
    }
}
