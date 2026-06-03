package com.portfolio.musictracker.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * {@link SectionTemplate} を構成する 1 ブロック分のデータ。
 * <p>
 * 見出し名・並び順に加え、コードテンプレートでは本文（コード進行）と
 * セクション個別 Key も保持できるため、構成だけでなく中身ごと使い回せる。
 */
@Entity
@Table(name = "section_template_items")
public class SectionTemplateItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 所属するテンプレート。 */
    @ManyToOne
    @JoinColumn(name = "template_id", nullable = false)
    private SectionTemplate template;

    /** ブロック見出し（例: Aメロ, サビ）。 */
    @Column(nullable = false, length = 50)
    private String name;

    /** 並び順。小さいほど上。 */
    @Column(nullable = false)
    private int sortOrder;

    /** 本文（歌詞 or コード進行）。構成だけ使いたい場合は空でもよい。 */
    @Column(columnDefinition = "TEXT")
    private String content;

    /** コードテンプレートのセクション個別 Key（転調用）。 */
    @Column(length = 20)
    private String sectionKey;

    public SectionTemplateItem() {
    }

    public SectionTemplateItem(String name, int sortOrder, String content, String sectionKey) {
        this.name = name;
        this.sortOrder = sortOrder;
        this.content = content;
        this.sectionKey = sectionKey;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public SectionTemplate getTemplate() {
        return template;
    }

    public void setTemplate(SectionTemplate template) {
        this.template = template;
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

    public String getSectionKey() {
        return sectionKey;
    }

    public void setSectionKey(String sectionKey) {
        this.sectionKey = sectionKey;
    }
}
