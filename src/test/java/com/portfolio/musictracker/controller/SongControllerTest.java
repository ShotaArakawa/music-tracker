package com.portfolio.musictracker.controller;

import com.portfolio.musictracker.IntegrationTestSupport;
import com.portfolio.musictracker.entity.Song;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SongControllerTest extends IntegrationTestSupport {

    @Value("${app.audio.upload-dir}")
    private String uploadDir;

    private static final byte[] AUDIO = {10, 20, 30, 40, 50};

    /** alice の曲に音源をアップロードし、保存ファイル名を返す。 */
    private String uploadAudio(Song song) throws Exception {
        mockMvc.perform(multipart("/songs/" + song.getId() + "/audio")
                        .file(new MockMultipartFile("audioFile", "demo.mp3", "audio/mpeg", AUDIO))
                        .with(as(alice)).with(csrf()))
                .andExpect(status().is3xxRedirection());
        return songRepository.findById(song.getId()).orElseThrow().getAudioFilePath();
    }

    private Path audioPath(String stored) {
        return Paths.get(uploadDir).toAbsolutePath().resolve(stored);
    }

    @Test
    void 音源は所有者だけが再生できる() throws Exception {
        Song song = createSong(alice, "曲");
        uploadAudio(song);

        mockMvc.perform(get("/songs/" + song.getId() + "/audio").with(as(alice)))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "audio/mpeg"))
                .andExpect(content().bytes(AUDIO));
        mockMvc.perform(get("/songs/" + song.getId() + "/audio").with(as(alice)).header("Range", "bytes=1-2"))
                .andExpect(status().isPartialContent());
        mockMvc.perform(get("/songs/" + song.getId() + "/audio").with(as(bob)))
                .andExpect(status().isForbidden());
    }

    @Test
    void 音源ファイルを静的パスから直接取得できない() throws Exception {
        String stored = uploadAudio(createSong(alice, "曲"));
        mockMvc.perform(get("/audio/" + stored).with(as(alice)))
                .andExpect(status().isNotFound());
    }

    @Test
    void 曲を削除すると音源ファイルも消える() throws Exception {
        Song song = createSong(alice, "消す曲");
        String stored = uploadAudio(song);
        assertThat(Files.exists(audioPath(stored))).isTrue();

        mockMvc.perform(post("/songs/" + song.getId() + "/delete").with(as(alice)).with(csrf()))
                .andExpect(status().is3xxRedirection());

        assertThat(songRepository.findById(song.getId())).isEmpty();
        assertThat(Files.exists(audioPath(stored))).isFalse();
    }

    @Test
    void 音源を差し替えると古いファイルは消える() throws Exception {
        Song song = createSong(alice, "曲");
        String first = uploadAudio(song);
        String second = uploadAudio(song);

        assertThat(second).isNotEqualTo(first);
        assertThat(Files.exists(audioPath(first))).isFalse();
        assertThat(Files.exists(audioPath(second))).isTrue();
    }

    @Test
    void 他人の曲は削除できない() throws Exception {
        Song song = createSong(alice, "曲");
        mockMvc.perform(post("/songs/" + song.getId() + "/delete").with(as(bob)).with(csrf()))
                .andExpect(status().isForbidden());
        assertThat(songRepository.findById(song.getId())).isPresent();
    }

    @Test
    void CSRFトークンのないPOSTは拒否する() throws Exception {
        Song song = createSong(alice, "曲");
        mockMvc.perform(post("/songs/" + song.getId() + "/field").with(as(alice))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"field\":\"title\",\"value\":\"改ざん\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/songs/" + song.getId() + "/field").with(as(alice)).with(csrf().asHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"field\":\"title\",\"value\":\"新タイトル\"}"))
                .andExpect(status().isOk());
        assertThat(songRepository.findById(song.getId()).orElseThrow().getTitle()).isEqualTo("新タイトル");
    }

    @Test
    void 未ログインはログイン画面へ() throws Exception {
        mockMvc.perform(get("/songs"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", org.hamcrest.Matchers.endsWith("/login")));
    }

    @Test
    void スタジオの一括保存でセクションの追加削除並び替えができる() throws Exception {
        Song song = createSong(alice, "曲");
        song.setBpm(90);
        song.setMusicKey("C");
        song.setWorldViewMemo("夜の街");
        songRepository.save(song);
        // 初回表示で既定セクション（歌詞: Aメロ/Bメロ/サビ）が作られる
        mockMvc.perform(get("/songs/" + song.getId()).with(as(alice))).andExpect(status().isOk());
        Long sabiId = inTx(() -> songRepository.findById(song.getId()).orElseThrow()
                .getLyricSections().stream()
                .filter(x -> x.getName().equals("サビ")).findFirst().orElseThrow().getId());

        // サビを先頭に、新規「Cメロ」を追加、Aメロ/Bメロは削除。コード譜も一緒に保存する
        String body = "{\"bpm\":120,\"musicKey\":\" Am \",\"lyricProgress\":150,"
                + "\"lyricSections\":[{\"id\":" + sabiId + ",\"name\":\"サビ\",\"content\":\"歌詞\"},"
                + "{\"name\":\"Cメロ\",\"content\":\"\"}],"
                + "\"chordSheet\":{\"data\":[[\"【サビ】\",\"F\",\"G\"]],\"style\":{\"B1\":\"font-weight: bold;\"},"
                + "\"mergeCells\":{},\"colWidths\":[90,60,60],\"rowHeights\":[30]}}";
        mockMvc.perform(post("/songs/" + song.getId() + "/save").with(as(alice)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());

        inTx(() -> {
            Song saved = songRepository.findById(song.getId()).orElseThrow();
            // BPM・Key・世界観は画面から外したため、送られてきても変更しない
            assertThat(saved.getBpm()).isEqualTo(90);
            assertThat(saved.getMusicKey()).isEqualTo("C");
            assertThat(saved.getWorldViewMemo()).isEqualTo("夜の街");
            assertThat(saved.getLyricProgress()).isEqualTo(100);
            assertThat(saved.getLyricSections()).extracting(s -> s.getName()).containsExactly("サビ", "Cメロ");
            assertThat(saved.getLyricSections().get(0).getId()).isEqualTo(sabiId);
            return null;
        });
        mockMvc.perform(get("/songs/" + song.getId() + "/chord-chart").with(as(alice)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sheet.data[0][1]").value("F"))
                .andExpect(jsonPath("$.sheet.colWidths[0]").value(90));

        // chordSheet を送らない（null）とコード譜は削除される
        mockMvc.perform(post("/songs/" + song.getId() + "/save").with(as(alice)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"lyricSections\":[]}"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/songs/" + song.getId() + "/chord-chart").with(as(alice)))
                .andExpect(jsonPath("$.sheet").doesNotExist());
    }
}
