package com.portfolio.musictracker.config;

import com.portfolio.musictracker.entity.Song;
import com.portfolio.musictracker.entity.User;
import com.portfolio.musictracker.repository.SongRepository;
import com.portfolio.musictracker.repository.UserRepository;
import com.portfolio.musictracker.service.TagService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * 起動時の初期データ投入。
 * <ul>
 *     <li>{@code app.init.demo-user=true} かつユーザーが1人もいなければ初期ユーザー（demo）を作成
 *         （既知のパスワードを持つため本番では無効にする）。既定のタグも用意する</li>
 *     <li>所有者未設定（マルチユーザー化前）の曲を初期ユーザーへ移行</li>
 * </ul>
 * タグはユーザーごとに持つため、ここでは共通のタグを作らない（{@code TagOwnershipMigration} の前に実行する）。
 */
@Component
@Order(1)
public class DataInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    private static final String DEFAULT_USERNAME = "demo";
    private static final String DEFAULT_PASSWORD = "demo1234";
    private static final String DEFAULT_EMAIL = "demo@example.com";

    private final UserRepository userRepository;
    private final SongRepository songRepository;
    private final TagService tagService;
    private final PasswordEncoder passwordEncoder;
    private final boolean createDemoUser;

    public DataInitializer(UserRepository userRepository, SongRepository songRepository,
                           TagService tagService, PasswordEncoder passwordEncoder,
                           @Value("${app.init.demo-user:false}") boolean createDemoUser) {
        this.userRepository = userRepository;
        this.songRepository = songRepository;
        this.tagService = tagService;
        this.passwordEncoder = passwordEncoder;
        this.createDemoUser = createDemoUser;
    }

    @Override
    @Transactional
    public void run(String... args) {
        findMigrationTarget().ifPresent(this::migrateOrphanSongs);
    }

    /**
     * 所有者未設定の曲の移行先ユーザーを返す。
     * demo ユーザー → 先頭ユーザーの順で探し、誰もいなければ（許可されていれば）demo を作成する。
     */
    private Optional<User> findMigrationTarget() {
        Optional<User> existing = userRepository.findByUsername(DEFAULT_USERNAME)
                .or(() -> userRepository.findAll().stream().findFirst());
        if (existing.isPresent() || !createDemoUser) {
            return existing;
        }
        User user = new User(DEFAULT_USERNAME,
                passwordEncoder.encode(DEFAULT_PASSWORD), DEFAULT_EMAIL, "USER");
        User saved = userRepository.save(user);
        tagService.ensureDefaultTags(saved);
        log.info("[Init] 初期ユーザーを作成しました（username={} / password={}）",
                DEFAULT_USERNAME, DEFAULT_PASSWORD);
        return Optional.of(saved);
    }

    /** 所有者未設定の既存曲を初期ユーザーへ紐づける（既存データが消えないようにする）。 */
    private void migrateOrphanSongs(User defaultUser) {
        List<Song> orphans = songRepository.findByUserIsNull();
        if (orphans.isEmpty()) {
            return;
        }
        orphans.forEach(song -> song.setUser(defaultUser));
        songRepository.saveAll(orphans);
        log.info("[Init] 所有者未設定の曲 {} 件を初期ユーザー（{}）へ移行しました",
                orphans.size(), defaultUser.getUsername());
    }
}
