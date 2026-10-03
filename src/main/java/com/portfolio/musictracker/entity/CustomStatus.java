package com.portfolio.musictracker.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * ユーザーが自分で追加したステータス（例:「ミックス中」「歌入れ待ち」）。
 * <p>
 * 既定のステータス（{@link Status}）に加えて選べる。追加・削除は本人にだけ影響する。
 * 「完了」はチェックボックスと連動する特別なステータスのため、既定の {@link Status#RELEASED} だけを使う。
 */
@Entity
@Table(name = "custom_statuses", uniqueConstraints = @UniqueConstraint(
        name = "uk_custom_statuses_user_name", columnNames = {"user_id", "name"}))
public class CustomStatus {

    /** 画面との受け渡しで、既定のステータス（Status の名前）と区別するための接頭辞。 */
    public static final String KEY_PREFIX = "custom:";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 30)
    private String name;

    /** バッジの色クラス。{@code StatusColor} の中から選ぶ。 */
    @Column(nullable = false, length = 40)
    private String colorClass;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    public CustomStatus() {
    }

    public CustomStatus(String name, String colorClass, User user) {
        this.name = name;
        this.colorClass = colorClass;
        this.user = user;
    }

    /** 画面で使うキー（{@code custom:12} の形）。 */
    public String getKey() {
        return KEY_PREFIX + id;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getColorClass() {
        return colorClass;
    }

    public void setColorClass(String colorClass) {
        this.colorClass = colorClass;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }
}
