package com.portfolio.musictracker.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 作曲デモ1曲分の情報。
 */
@Entity
@Table(name = "songs")
public class Song {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "曲名は必須です")
    @Column(nullable = false, length = 200)
    private String title;

    @Column(length = 1000)
    private String memo;

    /**
     * 納期（締め切り）。画面のカレンダーから選んだ日付を {@code yyyy-MM-dd} で保持する。
     * 以前の自由入力（「6/4」など）の値も残っていれば {@code DeadlineParser} で日付として解釈する。
     */
    @Column(length = 50)
    private String deadline;

    /**
     * 完了しているか（曲一覧の「完了」チェック）。ステータス「完了」と常に連動させる
     * （{@link #changeStatus} / {@link #markCompleted} 経由で変更する）。
     * 完了した曲は納期を過ぎても超過として警告せず、バックアップ一覧に表示する。
     */
    @Column(nullable = false)
    private boolean deadlineDone = false;

    /** 「完了」にする直前のステータス。チェックを外したときに戻すために覚えておく。 */
    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    private Status previousStatus;

    @NotNull(message = "ステータスを選択してください")
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private Status status = Status.LYRICS_WRITING;

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
            name = "song_tags",
            joinColumns = @JoinColumn(name = "song_id"),
            inverseJoinColumns = @JoinColumn(name = "tag_id")
    )
    private Set<Tag> tags = new HashSet<>();

    /**
     * この曲の所有ユーザー。一人のユーザーが複数の曲を持つ（多対一）。
     * 既存データ移行を考慮し nullable=true（起動時に初期ユーザーへ移行する）。
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    // ---- 作曲コア画面用の項目 ----

    /** テンポ（BPM）。 */
    private Integer bpm;

    /** キー（例: C, Am, F#m など）。 */
    @Column(length = 20)
    private String musicKey;

    /** 世界観・コンセプトのメモ。 */
    @Column(columnDefinition = "TEXT")
    private String worldViewMemo;

    /** 作詞の進捗率（0〜100）。 */
    @Column(nullable = false)
    private int lyricProgress = 0;

    /** メロディの進捗率（0〜100）。画面からは廃止したが、保存済みの値を残すため列は維持する。 */
    @Column(nullable = false)
    private int melodyProgress = 0;

    /** 編曲の進捗率（0〜100）。 */
    @Column(nullable = false)
    private int arrangementProgress = 0;

    /** アップロードされたデモ音源の保存ファイル名（uploaded-audio 以下）。未アップロードなら null。 */
    @Column(length = 255)
    private String audioFilePath;

    /** 一覧画面での表示順。小さいほど上に表示する。ドラッグ&ドロップで更新される。 */
    @Column(nullable = false)
    private int listOrder = 0;

    /** 最後に詳細（作曲コア）画面を開いた日時。「最後に編集した曲」の特定に使う。 */
    private LocalDateTime lastOpenedAt;

    /** 歌詞エリアのセクション。並び順で管理する。 */
    @OneToMany(mappedBy = "song", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder ASC")
    private List<LyricSection> lyricSections = new ArrayList<>();

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getMemo() {
        return memo;
    }

    public void setMemo(String memo) {
        this.memo = memo;
    }

    public String getDeadline() {
        return deadline;
    }

    public void setDeadline(String deadline) {
        this.deadline = deadline;
    }

    public boolean isDeadlineDone() {
        return deadlineDone;
    }

    public void setDeadlineDone(boolean deadlineDone) {
        this.deadlineDone = deadlineDone;
    }

    public Status getPreviousStatus() {
        return previousStatus;
    }

    public void setPreviousStatus(Status previousStatus) {
        this.previousStatus = previousStatus;
    }

    /** 完了しているか（ステータスが「完了」）。 */
    public boolean isCompleted() {
        return status == Status.RELEASED;
    }

    /**
     * ステータスを変更し、「完了」チェックを連動させる。
     * 「完了」にするときは直前のステータスを覚えておく。
     */
    public void changeStatus(Status newStatus) {
        if (newStatus == null || newStatus == status) {
            deadlineDone = isCompleted();
            return;
        }
        if (newStatus == Status.RELEASED) {
            previousStatus = status;
        } else {
            previousStatus = null;
        }
        status = newStatus;
        deadlineDone = isCompleted();
    }

    /**
     * 「完了」チェックの切り替え。チェックで「完了」に、外すと完了前のステータスに戻す
     * （覚えていなければ「フルコーラス完成」）。
     */
    public void markCompleted(boolean completed) {
        if (completed) {
            changeStatus(Status.RELEASED);
        } else if (isCompleted()) {
            changeStatus(previousStatus != null && previousStatus != Status.RELEASED
                    ? previousStatus : Status.FULL_CHORUS_DONE);
        }
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    public Set<Tag> getTags() {
        return tags;
    }

    public void setTags(Set<Tag> tags) {
        this.tags = tags;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
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

    public List<LyricSection> getLyricSections() {
        return lyricSections;
    }

    public void setLyricSections(List<LyricSection> lyricSections) {
        this.lyricSections = lyricSections;
    }

    /** 歌詞セクションを末尾に追加し、双方向の関連を整える。 */
    public void addLyricSection(LyricSection section) {
        section.setSong(this);
        this.lyricSections.add(section);
    }

    public String getAudioFilePath() {
        return audioFilePath;
    }

    public void setAudioFilePath(String audioFilePath) {
        this.audioFilePath = audioFilePath;
    }

    public int getListOrder() {
        return listOrder;
    }

    public void setListOrder(int listOrder) {
        this.listOrder = listOrder;
    }

    public LocalDateTime getLastOpenedAt() {
        return lastOpenedAt;
    }

    public void setLastOpenedAt(LocalDateTime lastOpenedAt) {
        this.lastOpenedAt = lastOpenedAt;
    }

    /** 作詞と編曲の進捗率の平均を全体の進捗率（%）として返す。 */
    public int getOverallProgress() {
        return Math.round((lyricProgress + arrangementProgress) / 2.0f);
    }
}
