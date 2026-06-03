package com.portfolio.musictracker.entity;

import jakarta.persistence.Column;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MappedSuperclass;

/**
 * 歌詞セクション・コードセクションが共通して持つ項目をまとめた基底クラス。
 * <p>
 * 歌詞とコードはそれぞれ独立したテーブル（エンティティ）として管理するが、
 * 「見出し名・並び順・本文」という構造は共通なので、ここに集約する。
 */
@MappedSuperclass
public abstract class AbstractSection {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 所属する曲。 */
    @ManyToOne
    @JoinColumn(name = "song_id", nullable = false)
    private Song song;

    /** セクション見出し（例: Aメロ, サビ, Intro）。 */
    @Column(nullable = false, length = 50)
    private String name;

    /** エリア内での並び順。小さいほど上に表示する。 */
    @Column(nullable = false)
    private int sortOrder;

    /** 本文（歌詞 or コード進行）。 */
    @Column(columnDefinition = "TEXT")
    private String content;

    protected AbstractSection() {
    }

    protected AbstractSection(Song song, String name, int sortOrder) {
        this.song = song;
        this.name = name;
        this.sortOrder = sortOrder;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Song getSong() {
        return song;
    }

    public void setSong(Song song) {
        this.song = song;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(int sortOrder) {
        this.sortOrder = sortOrder;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }
}
