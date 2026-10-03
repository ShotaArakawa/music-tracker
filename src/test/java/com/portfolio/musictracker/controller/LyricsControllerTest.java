package com.portfolio.musictracker.controller;

import com.portfolio.musictracker.IntegrationTestSupport;
import com.portfolio.musictracker.entity.Song;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 歌詞の書き出し（Word / テキスト / PDF）。
 */
class LyricsControllerTest extends IntegrationTestSupport {

    private static final String BODY = "{\"sections\":["
            + "{\"name\":\"Aメロ\",\"content\":\"眠れない夜の隅で\\n時計の針だけが進む\"},"
            + "{\"name\":\"\",\"content\":\"\"},"
            + "{\"name\":\"サビ\",\"content\":\"夜明けよ来い\\n\\n声にして\"}]}";

    private byte[] export(Song song, String format) throws Exception {
        return mockMvc.perform(post("/songs/" + song.getId() + "/lyrics/export").param("format", format)
                        .with(as(alice)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", containsString("filename*=UTF-8''")))
                .andReturn().getResponse().getContentAsByteArray();
    }

    @Test
    void テキストで書き出せる() throws Exception {
        Song song = createSong(alice, "夜明けのプロローグ");
        String text = new String(export(song, "txt"), StandardCharsets.UTF_8);

        assertThat(text).isEqualTo("夜明けのプロローグ\r\n\r\n"
                + "【Aメロ】\r\n眠れない夜の隅で\r\n時計の針だけが進む\r\n\r\n"
                + "【サビ】\r\n夜明けよ来い\r\n\r\n声にして\r\n");
    }

    @Test
    void Wordで書き出せる() throws Exception {
        Song song = createSong(alice, "夜明けのプロローグ");
        try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(export(song, "docx")));
             XWPFWordExtractor extractor = new XWPFWordExtractor(doc)) {
            String text = extractor.getText();
            assertThat(text).contains("夜明けのプロローグ", "【Aメロ】", "眠れない夜の隅で", "時計の針だけが進む",
                    "【サビ】", "声にして");
            assertThat(text.indexOf("【Aメロ】")).isLessThan(text.indexOf("【サビ】"));
        }
    }

    @Test
    void PDFで書き出せる() throws Exception {
        Song song = createSong(alice, "夜明けのプロローグ");
        try (PDDocument doc = Loader.loadPDF(export(song, "pdf"))) {
            String text = new PDFTextStripper().getText(doc);
            assertThat(text).contains("夜明けのプロローグ", "【Aメロ】", "眠れない夜の隅で", "【サビ】", "声にして");
        }
    }

    @Test
    void 長い歌詞はPDFで改ページする() throws Exception {
        Song song = createSong(alice, "長い曲");
        String lines = "この行は歌詞の長さを確かめるための行です\\n".repeat(80);
        byte[] pdf = mockMvc.perform(post("/songs/" + song.getId() + "/lyrics/export").param("format", "pdf")
                        .with(as(alice)).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sections\":[{\"name\":\"全体\",\"content\":\"" + lines + "\"}]}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();
        try (PDDocument doc = Loader.loadPDF(pdf)) {
            assertThat(doc.getNumberOfPages()).isGreaterThan(1);
        }
    }

    @Test
    void 他人の曲の歌詞は書き出せない() throws Exception {
        Song song = createSong(alice, "曲");
        mockMvc.perform(post("/songs/" + song.getId() + "/lyrics/export").param("format", "txt")
                        .with(as(bob)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isForbidden());
    }

    @Test
    void 対応していない形式はエラー() throws Exception {
        Song song = createSong(alice, "曲");
        mockMvc.perform(post("/songs/" + song.getId() + "/lyrics/export").param("format", "xls")
                        .with(as(alice)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isBadRequest());
    }
}
