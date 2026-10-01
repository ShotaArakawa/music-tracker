package com.portfolio.musictracker.controller;

import com.portfolio.musictracker.IntegrationTestSupport;
import com.portfolio.musictracker.entity.SectionAreaType;
import com.portfolio.musictracker.entity.SectionTemplate;
import com.portfolio.musictracker.entity.SectionTemplateItem;
import com.portfolio.musictracker.entity.User;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TemplateControllerTest extends IntegrationTestSupport {

    private SectionTemplate createTemplate(User owner, String name) {
        SectionTemplate t = new SectionTemplate(name, SectionAreaType.LYRIC, owner);
        t.addItem(new SectionTemplateItem("サビ", 0, "夜明けよ来い"));
        return templateRepository.save(t);
    }

    @Test
    void 保存したテンプレートは作成者のものになり一覧に出る() throws Exception {
        mockMvc.perform(post("/templates").with(as(alice)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"王道\",\"type\":\"LYRIC\",\"sections\":[{\"name\":\"サビ\",\"content\":\"C\"}]}"))
                .andExpect(status().isOk());

        assertThat(templateRepository.findAll()).singleElement()
                .satisfies(t -> assertThat(t.isOwnedBy(alice)).isTrue());
        mockMvc.perform(get("/templates").param("type", "LYRIC").with(as(alice)))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].shared").value(false));
    }

    @Test
    void 他人のテンプレートは一覧に出ず取得も変更もできない() throws Exception {
        Long id = createTemplate(alice, "aliceの").getId();

        mockMvc.perform(get("/templates").param("type", "LYRIC").with(as(bob)))
                .andExpect(jsonPath("$", hasSize(0)));
        mockMvc.perform(get("/templates/" + id).with(as(bob)))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/templates/" + id + "/rename").with(as(bob)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"乗っ取り\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/templates/" + id + "/overwrite").with(as(bob)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sections\":[{\"name\":\"x\",\"content\":\"\"}]}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/templates/" + id).with(as(bob)).with(csrf()))
                .andExpect(status().isForbidden());

        assertThat(templateRepository.findById(id)).get()
                .extracting(SectionTemplate::getName).isEqualTo("aliceの");
    }

    @Test
    void 自分のテンプレートは名前変更と削除ができる() throws Exception {
        Long id = createTemplate(alice, "旧名").getId();

        mockMvc.perform(post("/templates/" + id + "/rename").with(as(alice)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"新名\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("新名"));
        mockMvc.perform(delete("/templates/" + id).with(as(alice)).with(csrf()))
                .andExpect(status().isOk());

        assertThat(templateRepository.findById(id)).isEmpty();
    }

    @Test
    void 共有テンプレートは全員が適用できるが変更はできない() throws Exception {
        Long id = createTemplate(null, "共有").getId();

        mockMvc.perform(get("/templates").param("type", "LYRIC").with(as(bob)))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].shared").value(true));
        mockMvc.perform(get("/templates/" + id).with(as(bob)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sections[0].content").value("夜明けよ来い"));
        mockMvc.perform(delete("/templates/" + id).with(as(bob)).with(csrf()))
                .andExpect(status().isForbidden());
    }
}
