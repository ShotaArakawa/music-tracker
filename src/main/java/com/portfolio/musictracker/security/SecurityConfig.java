package com.portfolio.musictracker.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Spring Security 設定。
 * <ul>
 *     <li>ログイン／ユーザー登録／静的リソース以外は認証必須</li>
 *     <li>パスワードは {@link BCryptPasswordEncoder} で照合・ハッシュ化</li>
 *     <li>フォームログイン（独自ログイン画面 /login）とログアウトを有効化</li>
 * </ul>
 * <p>
 * 既存の多数の Ajax POST（並び替え・インライン編集・一括保存・テンプレート操作）を
 * そのまま動かすため、CSRF はこのアプリでは無効化している（ポートフォリオ用途）。
 */
@Configuration
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http,
                                           UserDetailsService userDetailsService) throws Exception {
        http
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/login", "/signup", "/api/health",
                        "/css/**", "/js/**", "/images/**", "/webjars/**", "/favicon.ico",
                        // PWA関連（manifest / Service Worker / アイコン）は未認証で取得できるようにする
                        "/manifest.json", "/sw.js", "/icon.png").permitAll()
                .anyRequest().authenticated()
            )
            .formLogin(form -> form
                .loginPage("/login")
                .loginProcessingUrl("/login")
                .defaultSuccessUrl("/songs", true)
                .failureUrl("/login?error")
                .permitAll()
            )
            .logout(logout -> logout
                .logoutUrl("/logout")
                .logoutSuccessUrl("/login?logout")
                // ログアウト時は Remember Me の Cookie も明示的に削除する
                .deleteCookies("remember-me")
                .permitAll()
            )
            // ログイン状態の保持（Remember Me）。
            // ハッシュ方式のトークン Cookie を発行し、ブラウザを閉じても
            // 有効期限内なら自動で再認証する。
            // key を固定することでアプリ再起動後もトークンが有効なままになる。
            .rememberMe(remember -> remember
                .key("musictracker-remember-me-key")
                .rememberMeParameter("remember-me")
                .rememberMeCookieName("remember-me")
                .tokenValiditySeconds(60 * 60 * 24 * 14)   // 14日間
                .userDetailsService(userDetailsService)
            )
            // 全画面が同一オリジンの Ajax/フォームのみのため CSRF は無効化
            .csrf(csrf -> csrf.disable());

        return http.build();
    }
}
