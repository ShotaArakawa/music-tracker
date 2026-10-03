package com.portfolio.musictracker;

import com.portfolio.musictracker.chordchart.ChordChartRepository;
import com.portfolio.musictracker.entity.Song;
import com.portfolio.musictracker.entity.User;
import com.portfolio.musictracker.repository.SongRepository;
import com.portfolio.musictracker.repository.TagRepository;
import com.portfolio.musictracker.repository.UserRepository;
import com.portfolio.musictracker.security.CustomUserDetails;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.function.Supplier;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;

/**
 * MockMvc を使う結合テストの共通設定。H2（application-test.yml）で起動し、
 * テストごとにデータを空にして、所有者の異なる2ユーザー（alice / bob）を用意する。
 * <p>
 * コミット後に走る処理（音源ファイル削除）も確かめるため、テストメソッドをトランザクションで包まない。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class IntegrationTestSupport {

    @Autowired
    protected MockMvc mockMvc;
    @Autowired
    protected UserRepository userRepository;
    @Autowired
    protected SongRepository songRepository;
    @Autowired
    private TransactionTemplate transactionTemplate;
    @Autowired
    private ChordChartRepository chordChartRepository;
    @Autowired
    protected TagRepository tagRepository;

    protected User alice;
    protected User bob;

    @BeforeEach
    void resetData() {
        chordChartRepository.deleteAll();
        songRepository.deleteAll();
        tagRepository.deleteAll();
        userRepository.deleteAll();
        alice = userRepository.save(new User("alice", "{noop}x", "alice@example.test", "USER"));
        bob = userRepository.save(new User("bob", "{noop}x", "bob@example.test", "USER"));
    }

    /** 指定ユーザーとしてログインした状態でリクエストする。 */
    protected static RequestPostProcessor as(User u) {
        return user(new CustomUserDetails(u));
    }

    /** 遅延ロードの関連（セクション等）を読むためにトランザクション内で実行する。 */
    protected <T> T inTx(Supplier<T> action) {
        return transactionTemplate.execute(status -> action.get());
    }

    protected Song createSong(User owner, String title) {
        Song song = new Song();
        song.setTitle(title);
        song.setUser(owner);
        return songRepository.save(song);
    }
}
