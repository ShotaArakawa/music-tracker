package com.portfolio.musictracker.status;

import com.portfolio.musictracker.IntegrationTestSupport;
import com.portfolio.musictracker.entity.CustomStatus;
import com.portfolio.musictracker.entity.Song;
import com.portfolio.musictracker.entity.Status;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * ユーザーが追加するステータス。
 */
class StatusControllerTest extends IntegrationTestSupport {

    private CustomStatus addStatus(String name, String color) throws Exception {
        mockMvc.perform(post("/statuses").with(as(alice)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\",\"color\":\"" + color + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value(name));
        return customStatusRepository.findByUserOrderByIdAsc(alice).stream()
                .filter(s -> s.getName().equals(name)).findFirst().orElseThrow();
    }

    private void setStatus(Song song, String value) throws Exception {
        mockMvc.perform(post("/songs/" + song.getId() + "/field").with(as(alice)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"field\":\"status\",\"value\":\"" + value + "\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void 追加したステータスを曲に設定できて一覧の選択肢に出る() throws Exception {
        CustomStatus mix = addStatus("歌入れ待ち", "PURPLE");
        assertThat(mix.getColorClass()).isEqualTo("st-purple");
        Song song = createSong(alice, "曲");

        mockMvc.perform(post("/songs/" + song.getId() + "/field").with(as(alice)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"field\":\"status\",\"value\":\"" + mix.getKey() + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statusName").value(mix.getKey()))
                .andExpect(jsonPath("$.statusLabel").value("歌入れ待ち"))
                .andExpect(jsonPath("$.statusColorClass").value("st-purple"));

        String html = mockMvc.perform(get("/songs").with(as(alice)))
                .andReturn().getResponse().getContentAsString();
        assertThat(html).contains("data-value=\"" + mix.getKey() + "\"").contains("歌入れ待ち");
        // 他のユーザーには出ない
        String bobHtml = mockMvc.perform(get("/songs").with(as(bob)))
                .andReturn().getResponse().getContentAsString();
        assertThat(bobHtml).doesNotContain("歌入れ待ち");
    }

    @Test
    void 完了チェックを外すと追加したステータスに戻る() throws Exception {
        CustomStatus mix = addStatus("ミックス中", "PINK");
        Song song = createSong(alice, "曲");
        setStatus(song, mix.getKey());

        mockMvc.perform(post("/songs/" + song.getId() + "/field").with(as(alice)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"field\":\"deadlineDone\",\"value\":\"true\"}"))
                .andExpect(jsonPath("$.statusName").value("RELEASED"));
        mockMvc.perform(post("/songs/" + song.getId() + "/field").with(as(alice)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"field\":\"deadlineDone\",\"value\":\"false\"}"))
                .andExpect(jsonPath("$.statusLabel").value("ミックス中"))
                .andExpect(jsonPath("$.deadlineDone").value(false));

        // 既定のステータスを選ぶと、追加したステータスは外れる
        setStatus(song, "ARRANGING");
        assertThat(songRepository.findById(song.getId()).orElseThrow().getStatusLabel()).isEqualTo("編曲中");
    }

    @Test
    void 完了した曲に追加したステータスを選ぶと完了が外れる() throws Exception {
        CustomStatus mix = addStatus("ミックス中", "TEAL");
        Song song = createSong(alice, "曲");
        song.changeStatus(Status.RELEASED);
        songRepository.save(song);

        mockMvc.perform(post("/songs/" + song.getId() + "/field").with(as(alice)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"field\":\"status\",\"value\":\"" + mix.getKey() + "\"}"))
                .andExpect(jsonPath("$.deadlineDone").value(false))
                .andExpect(jsonPath("$.statusLabel").value("ミックス中"));
    }

    @Test
    void 削除すると曲は元の既定ステータスに戻る() throws Exception {
        CustomStatus mix = addStatus("ミックス中", "ORANGE");
        Song song = createSong(alice, "曲");
        setStatus(song, "ARRANGING");
        setStatus(song, mix.getKey());

        mockMvc.perform(delete("/statuses/" + mix.getId()).with(as(alice)).with(csrf()))
                .andExpect(status().isOk());

        assertThat(customStatusRepository.count()).isZero();
        Song after = songRepository.findById(song.getId()).orElseThrow();
        assertThat(after.getCustomStatus()).isNull();
        assertThat(after.getStatusLabel()).isEqualTo("編曲中");
    }

    @Test
    void 不正な追加と他人のステータスは拒否する() throws Exception {
        CustomStatus mix = addStatus("ミックス中", "GRAY");
        // 重複・既定と同じ名前・空・不正な色
        for (String body : new String[]{
                "{\"name\":\"ミックス中\",\"color\":\"GRAY\"}",
                "{\"name\":\"編曲中\",\"color\":\"GRAY\"}",
                "{\"name\":\" \",\"color\":\"GRAY\"}",
                "{\"name\":\"歌入れ\",\"color\":\"bg-danger; x\"}"}) {
            mockMvc.perform(post("/statuses").with(as(alice)).with(csrf())
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest());
        }
        // bob は alice のステータスを削除・設定できない
        mockMvc.perform(delete("/statuses/" + mix.getId()).with(as(bob)).with(csrf()))
                .andExpect(status().isBadRequest());
        Song bobSong = createSong(bob, "bobの曲");
        mockMvc.perform(post("/songs/" + bobSong.getId() + "/field").with(as(bob)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"field\":\"status\",\"value\":\"" + mix.getKey() + "\"}"))
                .andExpect(status().isBadRequest());
        assertThat(customStatusRepository.count()).isEqualTo(1);
    }
}
