package com.portfolio.musictracker.chordchart;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellRangeAddress;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * コード譜の Excel / PDF 変換。同梱テンプレートを使って、書式が往復しても保たれることを確かめる。
 */
class ChordSheetConversionTest {

    private static ChordSheet template(ChordChartTemplate t) throws Exception {
        try (InputStream in = ChordSheetConversionTest.class.getResourceAsStream(t.resourcePath())) {
            return ChordSheetExcelConverter.read(in);
        }
    }

    @Test
    void テンプレート1を読み込める() throws Exception {
        ChordSheet s = template(ChordChartTemplate.TWO_PER_BAR);

        assertThat(s.rowCount()).isEqualTo(52);
        assertThat(s.colCount()).isEqualTo(10);
        assertThat(s.value(1, 1)).isEqualTo("アーティスト / タイトル [★]");
        assertThat(s.value(5, 0)).isEqualTo("【イントロ】");
        assertThat(s.getMergeCells().get("B2")).containsExactly(8, 1);
        // 既定列幅（defaultColWidth=12.63）を反映する
        assertThat(s.getColWidths()).containsOnly(88);
        // 高さ指定のない行は文字の大きさ（17pt = 23px）に合わせて広がる
        assertThat(s.rowHeight(6)).isGreaterThanOrEqualTo(23);
        CellStyleCss b7 = CellStyleCss.parse(s.styleAt(6, 1));
        assertThat(b7.color).isEqualTo("#4285F4");
        assertThat(b7.background).isEqualTo("#FFFBED");
    }

    @Test
    void Excelに書き出して読み直すと同じ表に戻る() throws Exception {
        for (ChordChartTemplate t : ChordChartTemplate.values()) {
            ChordSheet s = template(t);
            s.getData().get(5).set(1, "FM7");
            s.getData().get(6).set(1, "Ⅳ△7");

            ChordSheet back = ChordSheetExcelConverter.read(new ByteArrayInputStream(ChordSheetExcelConverter.write(s)));

            assertThat(back.getData()).isEqualTo(s.getData());
            assertThat(back.getStyle()).isEqualTo(s.getStyle());
            assertThat(back.getMergeCells()).containsOnlyKeys(s.getMergeCells().keySet());
            assertThat(back.getColWidths()).isEqualTo(s.getColWidths());
            assertThat(back.getRowHeights()).isEqualTo(s.getRowHeights());
        }
    }

    @Test
    void 旧形式のxlsも読み込める() throws Exception {
        byte[] xls;
        try (Workbook wb = new HSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet();
            Row row = sheet.createRow(0);
            Font font = wb.createFont();
            font.setBold(true);
            CellStyle style = wb.createCellStyle();
            style.setFont(font);
            row.createCell(0).setCellValue("【サビ】");
            row.getCell(0).setCellStyle(style);
            row.createCell(1).setCellValue("Am7");
            sheet.addMergedRegion(new CellRangeAddress(1, 1, 0, 2));
            wb.write(out);
            xls = out.toByteArray();
        }
        ChordSheet s = ChordSheetExcelConverter.read(new ByteArrayInputStream(xls));
        assertThat(s.value(0, 0)).isEqualTo("【サビ】");
        assertThat(s.value(0, 1)).isEqualTo("Am7");
        assertThat(CellStyleCss.parse(s.styleAt(0, 0)).bold).isTrue();
        assertThat(s.getMergeCells().get("A2")).containsExactly(3, 1);
    }

    @Test
    void Excelでないファイルは読み込めない() {
        assertThatThrownBy(() -> ChordSheetExcelConverter.read(new ByteArrayInputStream("hello".getBytes())))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 書き出したPDFは添付データから完全に復元できる() throws Exception {
        ChordSheet s = template(ChordChartTemplate.FOUR_PER_BAR);
        s.getData().get(5).set(1, "C#m7(♭5)");

        byte[] pdf = ChordSheetPdfRenderer.render(s, ChordSheetExcelConverter.write(s), "テスト");

        try (PDDocument doc = Loader.loadPDF(pdf)) {
            assertThat(doc.getNumberOfPages()).isEqualTo(1);
        }
        ChordSheet back = ChordSheetPdfImporter.read(pdf);
        assertThat(back.getData()).isEqualTo(s.getData());
        assertThat(back.getStyle()).isEqualTo(s.getStyle());
    }

    @Test
    void 行が多いときは複数ページに分ける() throws Exception {
        ChordSheet s = template(ChordChartTemplate.TWO_PER_BAR);
        for (int i = 0; i < 4; i++) {
            s.getData().addAll(template(ChordChartTemplate.TWO_PER_BAR).getData());
        }
        s.setRowHeights(null);
        s.normalize();
        try (PDDocument doc = Loader.loadPDF(ChordSheetPdfRenderer.render(s, null, "長い"))) {
            assertThat(doc.getNumberOfPages()).isGreaterThan(1);
        }
    }

    @Test
    void 文字データのあるPDFは位置から表に並べる() throws Exception {
        byte[] pdf = pdfWithText(new String[][]{{"Intro", "60"}, {"C", "100"}, {"G", "200"}},
                new String[][]{{"A", "60"}, {"Am", "100"}, {"F", "200"}});

        ChordSheet s = ChordSheetPdfImporter.read(pdf);

        assertThat(s.rowCount()).isEqualTo(2);
        assertThat(s.getData().get(0)).containsExactly("Intro", "C", "G");
        assertThat(s.getData().get(1)).containsExactly("A", "Am", "F");
    }

    @Test
    void 文字データのないPDFは読み取れない() throws Exception {
        byte[] pdf;
        try (PDDocument doc = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            doc.addPage(new PDPage(PDRectangle.A4));
            doc.save(out);
            pdf = out.toByteArray();
        }
        assertThatThrownBy(() -> ChordSheetPdfImporter.read(pdf))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("文字データがない");
    }

    /** 1行ずつ {文字, x 座標} を並べた PDF を作る。 */
    private static byte[] pdfWithText(String[][]... lines) throws Exception {
        try (PDDocument doc = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PDPage page = new PDPage(PDRectangle.A4);
            doc.addPage(page);
            PDType1Font font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                float y = 780;
                for (String[][] line : lines) {
                    for (String[] token : line) {
                        cs.beginText();
                        cs.setFont(font, 14);
                        cs.newLineAtOffset(Float.parseFloat(token[1]), y);
                        cs.showText(token[0]);
                        cs.endText();
                    }
                    y -= 30;
                }
            }
            doc.save(out);
            return out.toByteArray();
        }
    }
}
