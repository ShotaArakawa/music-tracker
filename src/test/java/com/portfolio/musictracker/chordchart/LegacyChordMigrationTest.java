package com.portfolio.musictracker.chordchart;

import com.portfolio.musictracker.IntegrationTestSupport;
import com.portfolio.musictracker.chordchart.LegacyChordMigration.LegacySection;
import com.portfolio.musictracker.entity.Song;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class LegacyChordMigrationTest extends IntegrationTestSupport {

    @Autowired
    private LegacyChordMigration migration;
    @Autowired
    private ChordChartService chordChartService;
    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void 旧コード進行を見出しとコードのマスに並べる() {
        ChordSheet s = LegacyChordMigration.toSheet("曲", List.of(
                new LegacySection("サビ", "F - G - Em - Am\nBm7-5 | E7", "C"),
                new LegacySection("空", "  ", null)));

        assertThat(s.value(0, 1)).isEqualTo("曲");
        assertThat(s.value(2, 0)).isEqualTo("【サビ】");
        assertThat(s.value(2, 1)).isEqualTo("Key：C");
        assertThat(s.getData().get(3)).startsWith("", "F", "G", "Em", "Am", "");
        // "Bm7-5" のようにコード名の一部の "-" は分割しない
        assertThat(s.getData().get(4)).startsWith("", "Bm7-5", "E7", "");
        // 中身のないセクションは出さない
        assertThat(s.getData()).noneMatch(row -> row.contains("【空】"));
    }

    @Test
    void 起動時に旧テーブルを変換して削除する() throws Exception {
        Song song = createSong(alice, "旧データの曲");
        jdbc.execute("CREATE TABLE chord_sections (id BIGINT AUTO_INCREMENT PRIMARY KEY, song_id BIGINT NOT NULL, "
                + "name VARCHAR(50), sort_order INT, content TEXT, section_key VARCHAR(20))");
        jdbc.update("INSERT INTO chord_sections (song_id, name, sort_order, content, section_key) VALUES (?,?,?,?,?)",
                song.getId(), "Aメロ", 0, "C G Am F", null);

        migration.run(null);

        ChordSheet sheet = chordChartService.find(song).orElseThrow();
        assertThat(sheet.value(2, 0)).isEqualTo("【Aメロ】");
        assertThat(sheet.getData().get(3)).startsWith("", "C", "G", "Am", "F");
        Integer tables = jdbc.queryForObject(
                "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES WHERE LOWER(TABLE_NAME) = 'chord_sections'", Integer.class);
        assertThat(tables).isZero();
    }
}
