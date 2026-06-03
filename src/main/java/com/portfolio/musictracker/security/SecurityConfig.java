package com.portfolio.musictracker.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
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
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/login", "/signup",
                        "/css/**", "/js/**", "/images/**", "/webjars/**", "/favicon.ico").permitAll()
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
                .permitAll()
            )
            // 全画面が同一オリジンの Ajax/フォームのみのため CSRF は無効化
            .csrf(csrf -> csrf.disable());

        return http.build();
    }
}
