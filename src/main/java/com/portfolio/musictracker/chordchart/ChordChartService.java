package com.portfolio.musictracker.chordchart;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.portfolio.musictracker.entity.Song;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * コード譜の保存・テンプレートからの作成・インポート／エクスポートを担う。
 * 曲の所有者チェックは呼び出し側（SongService / コントローラー）で済ませておく。
 */
@Service
@Transactional(readOnly = true)
public class ChordChartService {

    private final ChordChartRepository repository;
    private final ObjectMapper objectMapper;

    public ChordChartService(ChordChartRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    /** 曲のコード譜（なければ空）。 */
    public Optional<ChordSheet> find(Song song) {
        return repository.findBySong(song).map(c -> fromJson(c.getSheetJson()));
    }

    /** コード譜を保存する。null の場合はコード譜を削除する。 */
    @Transactional
    public void save(Song song, ChordSheet sheet) {
        if (sheet == null) {
            repository.deleteBySong(song);
            return;
        }
        String json = toJson(sheet.normalize());
        ChordChart chart = repository.findBySong(song).orElseGet(() -> new ChordChart(song, json));
        chart.setSheetJson(json);
        repository.save(chart);
    }

    @Transactional
    public void deleteBySong(Song song) {
        repository.deleteBySong(song);
    }

    /**
     * 白紙テンプレートからコード譜を作る（保存はしない）。
     * タイトル欄に曲名、「Key / BPM」欄に曲の Key と BPM を入れておく。
     */
    public ChordSheet fromTemplate(ChordChartTemplate template, Song song) {
        ChordSheet sheet;
        try (InputStream in = ChordChartService.class.getResourceAsStream(template.resourcePath())) {
            if (in == null) {
                throw new IllegalStateException("テンプレートが見つかりません: " + template.resourcePath());
            }
            sheet = ChordSheetExcelConverter.read(in);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        setValue(sheet, template.getTitleCell(), song.getTitle());
        setValue(sheet, template.getKeyBpmCell(), keyBpmText(song.getMusicKey(), song.getBpm()));
        return sheet;
    }

    /** タイトル下に入れる「Key：C　BPM：120」の文字列。未設定なら既定値（C / 120）。 */
    public static String keyBpmText(String key, Integer bpm) {
        String k = StringUtils.hasText(key) ? key.trim() : Song.DEFAULT_KEY;
        int b = bpm == null ? Song.DEFAULT_BPM : bpm;
        return "Key：" + k + "　BPM：" + b;
    }

    /** 「Key：…」で始まるセル（テンプレートの Key / BPM 欄）。 */
    private static final Pattern KEY_BPM_CELL =
            Pattern.compile("^\\s*key\\s*[:：].*", Pattern.CASE_INSENSITIVE);

    /**
     * 書き出す表のタイトル下に、現在の Key と BPM を入れる。
     * <ul>
     *     <li>Key / BPM 欄（「Key：…」のセル）があれば、その内容を置き換える</li>
     *     <li>なければ、曲名のセルの1つ下が空いていればそこに入れる</li>
     * </ul>
     * どちらも見つからない表（独自のレイアウトなど）は変更しない。
     */
    public static ChordSheet withKeyBpm(ChordSheet sheet, String title, String key, Integer bpm) {
        if (sheet == null || sheet.getData() == null) {
            return sheet;
        }
        String text = keyBpmText(key, bpm);
        List<List<String>> data = sheet.getData();
        for (List<String> row : data) {
            for (int c = 0; c < row.size(); c++) {
                if (row.get(c) != null && KEY_BPM_CELL.matcher(row.get(c)).matches()) {
                    row.set(c, text);
                    return sheet;
                }
            }
        }
        String t = title == null ? "" : title.trim();
        for (int r = 0; r + 1 < data.size() && !t.isEmpty(); r++) {
            List<String> row = data.get(r);
            for (int c = 0; c < row.size(); c++) {
                if (row.get(c) != null && row.get(c).trim().equals(t)) {
                    List<String> below = data.get(r + 1);
                    if (c < below.size() && !StringUtils.hasText(below.get(c))) {
                        below.set(c, text);
                    }
                    return sheet;
                }
            }
        }
        return sheet;
    }

    private static void setValue(ChordSheet sheet, String cellName, String value) {
        int[] rc = ChordSheet.parseCellName(cellName);
        if (rc[0] < sheet.rowCount() && rc[1] < sheet.colCount()) {
            sheet.getData().get(rc[0]).set(rc[1], value == null ? "" : value);
        }
    }

    /**
     * アップロードされた Excel（.xlsx / .xls）または PDF を読み込む（保存はしない）。
     *
     * @throws IllegalArgumentException 対応していない形式・読み取れない内容の場合
     */
    public ChordSheet importFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("ファイルが選択されていません");
        }
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new IllegalArgumentException("ファイルを読み込めませんでした", e);
        }
        String name = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase(Locale.ROOT);
        if (isPdf(bytes)) {
            return ChordSheetPdfImporter.read(bytes);
        }
        if (name.endsWith(".xlsx") || name.endsWith(".xlsm") || name.endsWith(".xls") || isZipOrOle(bytes)) {
            return ChordSheetExcelConverter.read(new ByteArrayInputStream(bytes));
        }
        throw new IllegalArgumentException("Excel（.xlsx / .xls）または PDF ファイルを選択してください");
    }

    private static boolean isPdf(byte[] b) {
        return b.length >= 4 && b[0] == '%' && b[1] == 'P' && b[2] == 'D' && b[3] == 'F';
    }

    /** .xlsx（ZIP）または .xls（OLE2）の先頭バイトか。 */
    private static boolean isZipOrOle(byte[] b) {
        return b.length >= 4 && ((b[0] == 'P' && b[1] == 'K')
                || ((b[0] & 0xFF) == 0xD0 && (b[1] & 0xFF) == 0xCF && (b[2] & 0xFF) == 0x11 && (b[3] & 0xFF) == 0xE0));
    }

    public byte[] exportXlsx(ChordSheet sheet) {
        return ChordSheetExcelConverter.write(sheet);
    }

    /** PDF を作る。再インポートで書式ごと戻せるよう Excel 版を添付する。 */
    public byte[] exportPdf(ChordSheet sheet, String title) {
        return ChordSheetPdfRenderer.render(sheet, ChordSheetExcelConverter.write(sheet), title);
    }

    String toJson(ChordSheet sheet) {
        try {
            return objectMapper.writeValueAsString(sheet);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("コード譜の保存形式への変換に失敗しました", e);
        }
    }

    private ChordSheet fromJson(String json) {
        try {
            return objectMapper.readValue(json, ChordSheet.class).normalize();
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("保存されたコード譜を読み込めませんでした", e);
        }
    }
}
