package com.portfolio.musictracker.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.util.HashSet;
import java.util.Set;

/**
 * 曲に紐付けるタグ（例：ボカロ / バンド / コンペ）。ユーザーごとに持ち、追加・削除は本人にだけ影響する。
 * <p>
 * 持ち主のいないタグ（user が null）は、ユーザーごとのタグにする前の共通タグ。
 * 起動時の移行処理（{@code TagOwnershipMigration}）で各ユーザーのタグに置き換える。
 */
@Entity
@Table(name = "tags", uniqueConstraints = @UniqueConstraint(
        name = "uk_tags_user_name", columnNames = {"user_id", "name"}))
public class Tag {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    private String name;

    /** タグの持ち主。 */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @ManyToMany(mappedBy = "tags")
    private Set<Song> songs = new HashSet<>();

    public Tag() {
    }

    public Tag(String name, User user) {
        this.name = name;
        this.user = user;
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

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public Set<Song> getSongs() {
        return songs;
    }

    public void setSongs(Set<Song> songs) {
        this.songs = songs;
    }
}
