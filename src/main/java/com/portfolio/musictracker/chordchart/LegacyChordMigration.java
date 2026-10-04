package com.portfolio.musictracker.chordchart;

import com.portfolio.musictracker.repository.SongRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 旧「コード進行」（chord_sections テーブル）を新しいコード譜（表）へ移す、起動時の一回限りの移行処理。
 * <p>
 * 曲ごとに「【セクション名】の見出し＋コード（1マス1コード）」の表を作り、その曲にコード譜がまだなければ保存する。
 * 移し終えたら旧テーブルを削除する（残しておくと曲を削除するときに外部キー制約で失敗するため）。
 * テーブルが存在しなければ何もしない。
 */
@Component
public class LegacyChordMigration implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(LegacyChordMigration.class);
    private static final String TABLE = "chord_sections";

    private static final String HEADER_CSS = "text-align: center; vertical-align: middle; font-weight: bold; "
            + "font-size: 16px; color: #000000;";
    private static final String KEY_CSS = "text-align: left; vertical-align: middle; font-size: 15px; "
            + "color: #4285F4;";
    private static final String CHORD_CSS = "text-align: left; vertical-align: bottom; font-size: 21px; "
            + "color: #000000; border-left: 1px solid #000000;";
    private static final String TITLE_CSS = "text-align: left; vertical-align: bottom; font-weight: bold; "
            + "font-size: 24px; color: #000000;";
    private static final int CHORD_COLS = 8;

    private final DataSource dataSource;
    private final JdbcTemplate jdbc;
    private final SongRepository songRepository;
    private final ChordChartRepository chartRepository;
    private final ChordChartService chartService;
    private final TransactionTemplate tx;

    public LegacyChordMigration(DataSource dataSource, JdbcTemplate jdbc, SongRepository songRepository,
                                ChordChartRepository chartRepository, ChordChartService chartService,
                                TransactionTemplate tx) {
        this.dataSource = dataSource;
        this.jdbc = jdbc;
        this.songRepository = songRepository;
        this.chartRepository = chartRepository;
        this.chartService = chartService;
        this.tx = tx;
    }

    record LegacySection(String name, String content, String key) {
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!tableExists()) {
            return;
        }
        Map<Long, List<LegacySection>> bySong = new LinkedHashMap<>();
        jdbc.query("SELECT song_id, name, content, section_key FROM " + TABLE + " ORDER BY song_id, sort_order",
                rs -> {
                    bySong.computeIfAbsent(rs.getLong("song_id"), k -> new ArrayList<>())
                            .add(new LegacySection(rs.getString("name"), rs.getString("content"),
                                    rs.getString("section_key")));
                });
        int migrated = tx.execute(status -> {
            int count = 0;
            for (Map.Entry<Long, List<LegacySection>> e : bySong.entrySet()) {
                boolean hasChords = e.getValue().stream()
                        .anyMatch(s -> s.content() != null && !s.content().isBlank());
                if (!hasChords || chartRepository.existsBySongId(e.getKey())) {
                    continue;
                }
                var song = songRepository.findById(e.getKey()).orElse(null);
                if (song == null) {
                    continue;
                }
                chartService.save(song, toSheet(song.getTitle(), e.getValue()));
                count++;
            }
            return count;
        });
        jdbc.execute("DROP TABLE " + TABLE);
        log.info("[Migration] 旧コード進行を {} 曲分コード譜へ移行し、{} テーブルを削除しました", migrated, TABLE);
    }

    private boolean tableExists() {
        try (Connection con = dataSource.getConnection()) {
            DatabaseMetaData meta = con.getMetaData();
            for (String name : new String[]{TABLE, TABLE.toUpperCase()}) {
                try (ResultSet rs = meta.getTables(con.getCatalog(), null, name, new String[]{"TABLE"})) {
                    if (rs.next()) {
                        return true;
                    }
                }
            }
            return false;
        } catch (SQLException e) {
            log.warn("[Migration] テーブルの確認に失敗しました", e);
            return false;
        }
    }

    /**
     * 旧セクションを表にする。1行目にタイトル、各セクションは
     * 見出し行（【セクション名】と転調の Key）＋コード行（1マス1コード、1行8マスまで）。
     */
    static ChordSheet toSheet(String title, List<LegacySection> sections) {
        ChordSheet sheet = new ChordSheet();
        List<List<String>> data = new ArrayList<>();
        List<Integer> heights = new ArrayList<>();
        addRow(sheet, data, heights, 36, new String[]{"", title}, new String[]{null, TITLE_CSS});
        addRow(sheet, data, heights, 16, new String[0], new String[0]);
        for (LegacySection s : sections) {
            if (s.content() == null || s.content().isBlank()) {
                continue;
            }
            // 見出し行：A 列にセクション名、転調の Key があれば B 列に
            String header = "【" + (s.name() == null ? "" : s.name()) + "】";
            boolean hasKey = s.key() != null && !s.key().isBlank();
            addRow(sheet, data, heights, 28,
                    new String[]{header, hasKey ? "Key：" + s.key().trim() : ""},
                    new String[]{HEADER_CSS, hasKey ? KEY_CSS : null});
            for (String line : s.content().split("\\r?\\n")) {
                // 空白で区切り、単独の区切り記号（旧画面が自動で入れていた " - " や小節線）は捨てる。
                // "Bm7-5" のようにコード名の一部になっている "-" は分割しない
                List<String> chords = new ArrayList<>();
                for (String token : line.trim().split("\\s+")) {
                    if (!token.isBlank() && !token.matches("[\\-–—|｜/]+")) {
                        chords.add(token);
                    }
                }
                if (chords.isEmpty()) {
                    continue;
                }
                for (int i = 0; i < chords.size(); i += CHORD_COLS) {
                    String[] values = new String[CHORD_COLS + 1];
                    String[] styles = new String[CHORD_COLS + 1];
                    values[0] = "";
                    for (int c = 0; c < CHORD_COLS; c++) {
                        values[c + 1] = i + c < chords.size() ? chords.get(i + c) : "";
                        styles[c + 1] = CHORD_CSS;
                    }
                    addRow(sheet, data, heights, 32, values, styles);
                }
            }
            addRow(sheet, data, heights, 16, new String[0], new String[0]);
        }
        List<Integer> widths = new ArrayList<>();
        widths.add(110);
        for (int c = 0; c < CHORD_COLS; c++) {
            widths.add(72);
        }
        sheet.setData(data);
        sheet.setRowHeights(heights);
        sheet.setColWidths(widths);
        // 行ごとの列数の違いは normalize が空文字で埋めて揃える
        return sheet.normalize();
    }

    private static void addRow(ChordSheet sheet, List<List<String>> data, List<Integer> heights, int height,
                               String[] values, String[] styles) {
        int r = data.size();
        List<String> row = new ArrayList<>();
        for (int c = 0; c < values.length; c++) {
            row.add(values[c] == null ? "" : values[c]);
            if (styles[c] != null) {
                sheet.getStyle().put(ChordSheet.cellName(r, c), styles[c]);
            }
        }
        data.add(row);
        heights.add(height);
    }
}
