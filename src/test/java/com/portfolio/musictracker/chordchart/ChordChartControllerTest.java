package com.portfolio.musictracker.chordchart;

import com.portfolio.musictracker.IntegrationTestSupport;
import com.portfolio.musictracker.entity.Song;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ChordChartControllerTest extends IntegrationTestSupport {

    @Autowired
    private ChordChartService chordChartService;
    @Autowired
    private ChordChartRepository chordChartRepository;

    private static final String SMALL_SHEET = "{\"data\":[[\"【サビ】\",\"F\",\"G\"]],\"style\":{},"
            + "\"mergeCells\":{},\"colWidths\":[90,60,60],\"rowHeights\":[30]}";

    private Song songWithKey() {
        Song song = createSong(alice, "夜明けのプロローグ");
        song.setMusicKey("Am");
        song.setBpm(128);
        return songRepository.save(song);
    }

    private byte[] templateXlsx() throws Exception {
        try (InputStream in = getClass().getResourceAsStream(ChordChartTemplate.TWO_PER_BAR.resourcePath())) {
            return in.readAllBytes();
        }
    }

    @Test
    void テンプレートから作るとタイトルとKeyとBPMが入る() throws Exception {
        Song song = songWithKey();
        mockMvc.perform(post("/songs/" + song.getId() + "/chord-chart/template")
                        .param("name", "FOUR_PER_BAR").with(as(alice)).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[1][1]").value("夜明けのプロローグ"))
                .andExpect(jsonPath("$.data[2][13]").value("Key：Am　BPM：128"))
                .andExpect(jsonPath("$.colWidths.length()").value(18));
        // 作成しただけでは保存しない
        assertThat(chordChartRepository.count()).isZero();
    }

    @Test
    void Excelをインポートできる() throws Exception {
        Song song = songWithKey();
        mockMvc.perform(multipart("/songs/" + song.getId() + "/chord-chart/import")
                        .file(new MockMultipartFile("file", "譜面.xlsx", "application/octet-stream", templateXlsx()))
                        .with(as(alice)).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[5][0]").value("【イントロ】"))
                .andExpect(jsonPath("$.mergeCells.B2[0]").value(8));
    }

    @Test
    void 対応していないファイルはエラーメッセージを返す() throws Exception {
        Song song = songWithKey();
        mockMvc.perform(multipart("/songs/" + song.getId() + "/chord-chart/import")
                        .file(new MockMultipartFile("file", "memo.txt", "text/plain", "hello".getBytes()))
                        .with(as(alice)).with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(containsString("Excel")));
    }

    @Test
    void 表示中の表をExcelとPDFでエクスポートできる() throws Exception {
        Song song = songWithKey();
        byte[] xlsx = mockMvc.perform(post("/songs/" + song.getId() + "/chord-chart/export").param("format", "xlsx")
                        .with(as(alice)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(SMALL_SHEET))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", containsString("filename*=UTF-8''")))
                .andReturn().getResponse().getContentAsByteArray();
        assertThat(ChordSheetExcelConverter.read(new ByteArrayInputStream(xlsx)).value(0, 1)).isEqualTo("F");

        byte[] pdf = mockMvc.perform(post("/songs/" + song.getId() + "/chord-chart/export").param("format", "pdf")
                        .with(as(alice)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(SMALL_SHEET))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/pdf"))
                .andReturn().getResponse().getContentAsByteArray();
        assertThat(ChordSheetPdfImporter.read(pdf).value(0, 2)).isEqualTo("G");
    }

    @Test
    void エクスポートではタイトル下のKeyとBPMを画面の値にする() throws Exception {
        Song song = songWithKey();
        // テンプレートの Key / BPM 欄（古い値のまま）
        String sheet = "{\"data\":[[\"\",\"夜明けのプロローグ\",\"\"],[\"\",\"\",\"Key：C　BPM：000\"],[\"【サビ】\",\"F\",\"G\"]],"
                + "\"style\":{},\"mergeCells\":{},\"colWidths\":[90,60,60],\"rowHeights\":[30,30,30]}";
        byte[] xlsx = mockMvc.perform(post("/songs/" + song.getId() + "/chord-chart/export").param("format", "xlsx")
                        .param("key", "Eb").param("bpm", "150")
                        .with(as(alice)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(sheet))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();
        assertThat(ChordSheetExcelConverter.read(new ByteArrayInputStream(xlsx)).value(1, 2))
                .isEqualTo("Key：Eb　BPM：150");

        // Key / BPM 欄のない表は、曲名の下の空いたマスに曲の Key・BPM を入れる（PDF）
        String noMeta = "{\"data\":[[\"夜明けのプロローグ\",\"\"],[\"\",\"\"],[\"F\",\"G\"]],"
                + "\"style\":{},\"mergeCells\":{},\"colWidths\":[120,60],\"rowHeights\":[30,30,30]}";
        byte[] pdf = mockMvc.perform(post("/songs/" + song.getId() + "/chord-chart/export").param("format", "pdf")
                        .with(as(alice)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(noMeta))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();
        assertThat(ChordSheetPdfImporter.read(pdf).value(1, 0)).isEqualTo("Key：Am　BPM：128");
    }

    @Test
    void 他人の曲のコード譜は扱えない() throws Exception {
        Song song = songWithKey();
        mockMvc.perform(get("/songs/" + song.getId() + "/chord-chart").with(as(bob)))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/songs/" + song.getId() + "/chord-chart/export").param("format", "xlsx")
                        .with(as(bob)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(SMALL_SHEET))
                .andExpect(status().isForbidden());
    }

    @Test
    void 大きすぎる表は保存しない() throws Exception {
        Song song = songWithKey();
        String huge = "{\"chordSheet\":{\"data\":[" + "[\"x\"],".repeat(ChordSheet.MAX_ROWS) + "[\"x\"]]}}";
        mockMvc.perform(post("/songs/" + song.getId() + "/save").with(as(alice)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(huge))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(containsString("大きすぎます")));
    }

    @Test
    void コード譜のある曲も削除できる() throws Exception {
        Song song = songWithKey();
        chordChartService.save(song, chordChartService.fromTemplate(ChordChartTemplate.TWO_PER_BAR, song));
        assertThat(chordChartRepository.count()).isEqualTo(1);

        mockMvc.perform(post("/songs/" + song.getId() + "/delete").with(as(alice)).with(csrf()))
                .andExpect(status().is3xxRedirection());

        assertThat(songRepository.findById(song.getId())).isEmpty();
        assertThat(chordChartRepository.count()).isZero();
    }
}
