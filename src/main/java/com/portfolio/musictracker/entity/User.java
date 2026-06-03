package com.portfolio.musictracker.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * アプリを利用するクリエイター（ユーザー）。
 * パスワードは BCrypt でハッシュ化したものを保存する（平文は保持しない）。
 */
@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** ログインID。一意。 */
    @Column(nullable = false, unique = true, length = 50)
    private String username;

    /** BCrypt ハッシュ化済みパスワード。 */
    @Column(nullable = false, length = 100)
    private String password;

    /** メールアドレス。一意。 */
    @Column(nullable = false, unique = true, length = 150)
    private String email;

    /** 権限ロール（例: USER, ADMIN）。authorities は "ROLE_" + role で組み立てる。 */
    @Column(nullable = false, length = 20)
    private String role = "USER";

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    public User() {
    }

    public User(String username, String password, String email, String role) {
        this.username = username;
        this.password = password;
        this.email = email;
        this.role = role;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
