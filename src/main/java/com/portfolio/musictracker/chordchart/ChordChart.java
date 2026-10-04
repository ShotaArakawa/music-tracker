package com.portfolio.musictracker.chordchart;

import com.portfolio.musictracker.entity.Song;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * 曲ごとのコード譜（1曲につき1枚）。表の内容は {@link ChordSheet} を JSON にして保存する。
 * <p>
 * 一覧画面などで曲を読み込むたびに大きな JSON を読まないよう、曲とは別テーブルにしている。
 */
@Entity
@Table(name = "chord_charts")
public class ChordChart {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "song_id", nullable = false, unique = true)
    private Song song;

    /** {@link ChordSheet} の JSON。 */
    @Column(nullable = false, columnDefinition = "LONGTEXT")
    private String sheetJson;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

    protected ChordChart() {
    }

    public ChordChart(Song song, String sheetJson) {
        this.song = song;
        this.sheetJson = sheetJson;
    }

    public Long getId() {
        return id;
    }

    public Song getSong() {
        return song;
    }

    public String getSheetJson() {
        return sheetJson;
    }

    public void setSheetJson(String sheetJson) {
        this.sheetJson = sheetJson;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
