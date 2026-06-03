package com.portfolio.musictracker.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * セクション構成のテンプレート（例:「王道ポップス構成」「アニソン構成」）。
 * <p>
 * 歌詞エリア／コードエリアそれぞれの「ブロックの並び」を名前付きで保存しておき、
 * 別の楽曲にワンクリックで適用してブロック構成を一括生成するために使う。
 */
@Entity
@Table(name = "section_templates")
public class SectionTemplate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** テンプレート名（例:「王道ポップス構成」）。 */
    @Column(nullable = false, length = 100)
    private String name;

    /** 対象エリア（歌詞 or コード）。 */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private SectionAreaType type;

    /** テンプレートを構成する各ブロック。並び順で保持する。 */
    @OneToMany(mappedBy = "template", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder ASC")
    private List<SectionTemplateItem> items = new ArrayList<>();

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    public SectionTemplate() {
    }

    public SectionTemplate(String name, SectionAreaType type) {
        this.name = name;
        this.type = type;
    }

    /** ブロックを末尾に追加し、双方向の関連を整える。 */
    public void addItem(SectionTemplateItem item) {
        item.setTemplate(this);
        this.items.add(item);
    }

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

    public SectionAreaType getType() {
        return type;
    }

    public void setType(SectionAreaType type) {
        this.type = type;
    }

    public List<SectionTemplateItem> getItems() {
        return items;
    }

    public void setItems(List<SectionTemplateItem> items) {
        this.items = items;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
