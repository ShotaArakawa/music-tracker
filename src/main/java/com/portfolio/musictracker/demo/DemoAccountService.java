package com.portfolio.musictracker.demo;

import com.portfolio.musictracker.chordchart.ChordChartRepository;
import com.portfolio.musictracker.entity.Song;
import com.portfolio.musictracker.entity.User;
import com.portfolio.musictracker.repository.CustomStatusRepository;
import com.portfolio.musictracker.repository.SongRepository;
import com.portfolio.musictracker.repository.TagRepository;
import com.portfolio.musictracker.repository.UserRepository;
import com.portfolio.musictracker.service.TagService;
import com.portfolio.musictracker.storage.AudioStorage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

/**
 * ランディングページの「テストユーザーで試してみる」用のお試しアカウント。
 * <p>
 * 訪問者ごとに専用のアカウントを作り（他の人の操作の影響を受けない）、サンプルデータを入れる。
 * パスワードはランダムで誰にも知らせず、ログインはこの機能からだけ行う。
 * 作成から {@code app.demo.ttl-hours} 時間たったアカウントはデータごと自動で削除する。
 */
@Service
public class DemoAccountService {

    private static final Logger log = LoggerFactory.getLogger(DemoAccountService.class);
    private static final String USERNAME_PREFIX = "guest-";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserRepository userRepository;
    private final SongRepository songRepository;
    private final TagRepository tagRepository;
    private final CustomStatusRepository customStatusRepository;
    private final ChordChartRepository chordChartRepository;
    private final TagService tagService;
    private final DemoSampleData sampleData;
    private final AudioStorage audioStorage;
    private final PasswordEncoder passwordEncoder;
    private final boolean enabled;
    private final int ttlHours;
    private final int maxAccounts;

    public DemoAccountService(UserRepository userRepository, SongRepository songRepository,
                              TagRepository tagRepository, CustomStatusRepository customStatusRepository,
                              ChordChartRepository chordChartRepository, TagService tagService,
                              DemoSampleData sampleData, AudioStorage audioStorage, PasswordEncoder passwordEncoder,
                              @Value("${app.demo.enabled:true}") boolean enabled,
                              @Value("${app.demo.ttl-hours:24}") int ttlHours,
                              @Value("${app.demo.max-accounts:200}") int maxAccounts) {
        this.userRepository = userRepository;
        this.songRepository = songRepository;
        this.tagRepository = tagRepository;
        this.customStatusRepository = customStatusRepository;
        this.chordChartRepository = chordChartRepository;
        this.tagService = tagService;
        this.sampleData = sampleData;
        this.audioStorage = audioStorage;
        this.passwordEncoder = passwordEncoder;
        this.enabled = enabled;
        this.ttlHours = ttlHours;
        this.maxAccounts = maxAccounts;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public int getTtlHours() {
        return ttlHours;
    }

    /**
     * お試しアカウントを作り、サンプルデータを入れる。
     *
     * @throws DemoUnavailableException 機能が無効、または同時に存在できる数の上限に達している場合
     */
    @Transactional
    public User create() {
        if (!enabled) {
            throw new DemoUnavailableException("お試し機能は現在ご利用いただけません");
        }
        if (userRepository.countByDemoTrue() >= maxAccounts) {
            throw new DemoUnavailableException("お試しアカウントが混み合っています。しばらくしてからお試しください");
        }
        String username;
        do {
            byte[] bytes = new byte[4];
            RANDOM.nextBytes(bytes);
            username = USERNAME_PREFIX + HexFormat.of().formatHex(bytes);
        } while (userRepository.existsByUsername(username));

        User user = new User(username, passwordEncoder.encode(UUID.randomUUID().toString()),
                username + "@demo.music-tracker.invalid", "USER");
        user.setDemo(true);
        User saved = userRepository.save(user);
        tagService.ensureDefaultTags(saved);
        sampleData.seed(saved);
        return saved;
    }

    /** 作成から一定時間たったお試しアカウントを、曲・コード譜・タグ・追加ステータスごと削除する。 */
    @Transactional
    public int deleteExpired() {
        List<User> expired = userRepository.findByDemoTrueAndCreatedAtBefore(LocalDateTime.now().minusHours(ttlHours));
        for (User user : expired) {
            delete(user);
        }
        if (!expired.isEmpty()) {
            log.info("[Demo] 期限切れのお試しアカウントを {} 件削除しました", expired.size());
        }
        return expired.size();
    }

    private void delete(User user) {
        for (Song song : songRepository.findByUser(user)) {
            chordChartRepository.deleteBySong(song);
            if (song.getAudioFilePath() != null) {
                // お試しアカウントは音源をアップロードできないが、念のため片付ける
                try {
                    audioStorage.delete(song.getAudioFilePath());
                } catch (RuntimeException e) {
                    log.warn("お試しアカウントの音源の削除に失敗しました", e);
                }
            }
            songRepository.delete(song);
        }
        songRepository.flush();
        customStatusRepository.deleteAll(customStatusRepository.findByUserOrderByIdAsc(user));
        tagRepository.deleteAll(tagRepository.findByUserOrderByIdAsc(user));
        userRepository.delete(user);
    }

    /** お試しアカウントを作れないときの例外。 */
    public static class DemoUnavailableException extends RuntimeException {
        public DemoUnavailableException(String message) {
            super(message);
        }
    }
}
