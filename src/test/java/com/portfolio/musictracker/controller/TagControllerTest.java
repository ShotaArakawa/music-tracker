package com.portfolio.musictracker.controller;

import com.portfolio.musictracker.IntegrationTestSupport;
import com.portfolio.musictracker.config.TagOwnershipMigration;
import com.portfolio.musictracker.entity.Song;
import com.portfolio.musictracker.entity.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * ユーザーごとのタグ（追加・削除・他人のタグの扱い）と、共通タグからの移行。
 */
class TagControllerTest extends IntegrationTestSupport {

    @Autowired
    private TagOwnershipMigration tagMigration;

    private String addTag(String name, com.portfolio.musictracker.entity.User user) throws Exception {
        return mockMvc.perform(post("/tags").with(as(user)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"" + name + "\"}"))
                .andReturn().getResponse().getContentAsString();
    }

    @Test
    void タグは自分の分だけ見えて同じ名前を別ユーザーも使える() throws Exception {
        addTag("アニソン", alice);
        addTag("アニソン", bob);

        mockMvc.perform(get("/tags").with(as(alice)))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].name").value("アニソン"));
        assertThat(tagRepository.count()).isEqualTo(2);
    }

    @Test
    void 同じ名前や空の名前は追加できない() throws Exception {
        addTag("劇伴", alice);
        mockMvc.perform(post("/tags").with(as(alice)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\" 劇伴 \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(containsString("すでに")));
        mockMvc.perform(post("/tags").with(as(alice)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"  \"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void タグを削除すると曲からも外れる() throws Exception {
        Tag tag = tagRepository.save(new Tag("コンペ", alice));
        Song song = createSong(alice, "曲");
        song.getTags().add(tag);
        songRepository.save(song);

        mockMvc.perform(delete("/tags/" + tag.getId()).with(as(alice)).with(csrf()))
                .andExpect(status().isOk());

        assertThat(tagRepository.findById(tag.getId())).isEmpty();
        assertThat(songRepository.findById(song.getId()).orElseThrow().getTags()).isEmpty();
    }

    @Test
    void 他人のタグは削除も曲への付与もできない() throws Exception {
        Tag bobsTag = tagRepository.save(new Tag("ボブのタグ", bob));
        Song song = createSong(alice, "曲");

        mockMvc.perform(delete("/tags/" + bobsTag.getId()).with(as(alice)).with(csrf()))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/songs/" + song.getId() + "/field").with(as(alice)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"field\":\"tagId\",\"value\":\"" + bobsTag.getId() + "\"}"))
                .andExpect(status().isBadRequest());
        assertThat(tagRepository.findById(bobsTag.getId())).isPresent();
    }

    @Test
    void 共通タグは各ユーザーのタグに複製され曲の紐付けも引き継ぐ() throws Exception {
        Tag shared = tagRepository.save(new Tag("ボカロ", null));
        Song song = createSong(alice, "曲");
        song.getTags().add(shared);
        songRepository.save(song);

        tagMigration.run(null);

        assertThat(tagRepository.findByUserIsNull()).isEmpty();
        Tag alicesTag = tagRepository.findByUserAndName(alice, "ボカロ").orElseThrow();
        assertThat(tagRepository.findByUserAndName(bob, "ボカロ")).isPresent();
        assertThat(songRepository.findById(song.getId()).orElseThrow().getTags())
                .extracting(Tag::getId).containsExactly(alicesTag.getId());
    }
}
