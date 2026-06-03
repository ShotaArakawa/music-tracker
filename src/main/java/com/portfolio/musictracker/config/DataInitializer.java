package com.portfolio.musictracker.config;

import com.portfolio.musictracker.entity.Song;
import com.portfolio.musictracker.entity.Tag;
import com.portfolio.musictracker.entity.User;
import com.portfolio.musictracker.repository.SongRepository;
import com.portfolio.musictracker.repository.TagRepository;
import com.portfolio.musictracker.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 起動時の初期データ投入。
 * <ul>
 *     <li>初期タグ（ボカロ / バンド / コンペ）を投入</li>
 *     <li>ユーザーが1人もいなければ初期ユーザー（demo）を作成</li>
 *     <li>所有者未設定（マルチユーザー化前）の曲を初期ユーザーへ移行</li>
 * </ul>
 */
@Component
public class DataInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    private static final List<String> DEFAULT_TAGS = List.of("ボカロ", "バンド", "コンペ");
    private static final String DEFAULT_USERNAME = "demo";
    private static final String DEFAULT_PASSWORD = "demo1234";
    private static final String DEFAULT_EMAIL = "demo@example.com";

    private final TagRepository tagRepository;
    private final UserRepository userRepository;
    private final SongRepository songRepository;
    private final PasswordEncoder passwordEncoder;

    public DataInitializer(TagRepository tagRepository, UserRepository userRepository,
                           SongRepository songRepository, PasswordEncoder passwordEncoder) {
        this.tagRepository = tagRepository;
        this.userRepository = userRepository;
        this.songRepository = songRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(String... args) {
        seedTags();
        User defaultUser = ensureDefaultUser();
        migrateOrphanSongs(defaultUser);
    }

    private void seedTags() {
        for (String name : DEFAULT_TAGS) {
            if (!tagRepository.existsByName(name)) {
                tagRepository.save(new Tag(name));
            }
        }
    }

    /** ユーザーが1人もいなければ初期ユーザーを作成し、返す。既にいれば最初の1人を返す。 */
    private User ensureDefaultUser() {
        return userRepository.findByUsername(DEFAULT_USERNAME).orElseGet(() -> {
            if (userRepository.count() > 0) {
                // 既に別ユーザーが存在する場合は移行先として先頭ユーザーを使う
                return userRepository.findAll().get(0);
            }
            User user = new User(DEFAULT_USERNAME,
                    passwordEncoder.encode(DEFAULT_PASSWORD), DEFAULT_EMAIL, "USER");
            User saved = userRepository.save(user);
            log.info("[Init] 初期ユーザーを作成しました（username={} / password={}）",
                    DEFAULT_USERNAME, DEFAULT_PASSWORD);
            return saved;
        });
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
