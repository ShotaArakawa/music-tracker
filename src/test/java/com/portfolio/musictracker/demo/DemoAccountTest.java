package com.portfolio.musictracker.demo;

import com.portfolio.musictracker.IntegrationTestSupport;
import com.portfolio.musictracker.chordchart.ChordChartRepository;
import com.portfolio.musictracker.entity.Song;
import com.portfolio.musictracker.entity.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * ランディングページの「テストユーザーで試してみる」（お試しアカウント）。
 */
class DemoAccountTest extends IntegrationTestSupport {

    @Autowired
    private DemoAccountService demoAccountService;
    @Autowired
    private ChordChartRepository chordChartRepository;
    @Autowired
    private JdbcTemplate jdbc;

    /** お試しを開始し、ログイン済みのセッションを返す。 */
    private MockHttpSession startDemo() throws Exception {
        MvcResult result = mockMvc.perform(post("/demo/start").with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/songs"))
                .andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    private User onlyDemoUser() {
        return userRepository.findAll().stream().filter(User::isDemo).findFirst().orElseThrow();
    }

    @Test
    void ランディングページにお試しボタンがある() throws Exception {
        String html = mockMvc.perform(get("/")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(html).contains("テストユーザーで試してみる").contains("/demo/start")
                .contains("/images/landing/list.webp");
    }

    @Test
    void お試しを始めるとサンプルデータ入りの専用アカウントでログインする() throws Exception {
        MockHttpSession session = startDemo();
        String html = mockMvc.perform(get("/songs").session(session))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(html).contains("お試しアカウント").contains("夜明けのプロローグ").contains("ミックス中")
                .contains("table-danger");

        User demo = onlyDemoUser();
        assertThat(demo.getUsername()).startsWith("guest-");
        assertThat(songRepository.findByUser(demo)).hasSize(6)
                .anySatisfy(s -> assertThat(s.isCompleted()).isTrue());
        Song dawn = songRepository.findByUser(demo).stream()
                .filter(s -> s.getTitle().equals("夜明けのプロローグ")).findFirst().orElseThrow();
        assertThat(chordChartRepository.findBySong(dawn)).isPresent();

        // 2人目は別のアカウント（他の人の操作の影響を受けない）
        startDemo();
        assertThat(userRepository.countByDemoTrue()).isEqualTo(2);
    }

    @Test
    void お試しアカウントは音源アップロードとプロフィール変更ができない() throws Exception {
        MockHttpSession session = startDemo();
        User demo = onlyDemoUser();
        Song song = songRepository.findByUser(demo).get(0);

        mockMvc.perform(multipart("/songs/" + song.getId() + "/audio")
                        .file(new MockMultipartFile("audioFile", "a.mp3", "audio/mpeg", new byte[]{1, 2, 3}))
                        .session(session).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("error", org.hamcrest.Matchers.containsString("お試しアカウント")));
        assertThat(songRepository.findById(song.getId()).orElseThrow().getAudioFilePath()).isNull();

        mockMvc.perform(post("/profile/update").session(session).with(csrf())
                        .param("username", "hijack").param("email", "x@example.test"))
                .andExpect(status().isOk());
        assertThat(userRepository.findById(demo.getId()).orElseThrow().getUsername()).isEqualTo(demo.getUsername());
    }

    @Test
    void 期限切れのお試しアカウントはデータごと削除する() throws Exception {
        startDemo();
        User demo = onlyDemoUser();
        Song normal = createSong(alice, "通常ユーザーの曲");

        assertThat(demoAccountService.deleteExpired()).isZero();
        jdbc.update("UPDATE users SET created_at = ? WHERE id = ?",
                LocalDateTime.now().minusHours(25), demo.getId());
        assertThat(demoAccountService.deleteExpired()).isEqualTo(1);

        assertThat(userRepository.findById(demo.getId())).isEmpty();
        assertThat(songRepository.findAll()).extracting(Song::getId).containsExactly(normal.getId());
        assertThat(chordChartRepository.count()).isZero();
        assertThat(customStatusRepository.count()).isZero();
        assertThat(tagRepository.findAll()).allSatisfy(t -> assertThat(t.getUser()).isNotNull());
    }

    @Test
    void お試しからの新規登録はログアウトして登録画面へ() throws Exception {
        MockHttpSession session = startDemo();
        mockMvc.perform(post("/logout").param("to", "signup").session(session).with(csrf()))
                .andExpect(redirectedUrl("/signup"));
        mockMvc.perform(post("/logout").with(csrf()))
                .andExpect(redirectedUrl("/login?logout"));
    }
}
