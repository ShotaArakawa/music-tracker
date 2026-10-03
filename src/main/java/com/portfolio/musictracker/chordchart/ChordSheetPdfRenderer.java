package com.portfolio.musictracker.chordchart;

import org.apache.pdfbox.io.RandomAccessReadBuffer;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentInformation;
import org.apache.pdfbox.pdmodel.PDDocumentNameDictionary;
import org.apache.pdfbox.pdmodel.PDEmbeddedFilesNameTreeNode;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.common.filespecification.PDComplexFileSpecification;
import org.apache.pdfbox.pdmodel.common.filespecification.PDEmbeddedFile;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.pdmodel.graphics.state.RenderingMode;
import org.apache.fontbox.ttf.TTFParser;
import org.apache.fontbox.ttf.TrueTypeFont;

import java.awt.Color;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * コード譜を PDF（A4 縦）に描画する。
 * <p>
 * 表の横幅をページ幅に合わせて縮小し、行の途中でページをまたがないように改ページする。
 * 元データ（.xlsx）を PDF に添付しておき、この PDF をインポートしたときに書式ごと完全に復元できるようにする。
 */
public final class ChordSheetPdfRenderer {

    /** PDF に添付する元データのファイル名（インポート時にこれを探す）。 */
    public static final String ATTACHMENT_NAME = "chord-chart.xlsx";

    private static final float MARGIN = 28f;
    /** px → pt の等倍比率。これより拡大はしない。 */
    private static final float NATURAL_SCALE = 0.75f;
    private static final float CELL_PADDING_PX = 3f;

    private static final byte[] PRIMARY_FONT = loadResource("/fonts/MPLUSRounded1c-Regular.ttf");
    private static final byte[] FALLBACK_FONT = loadResource("/fonts/ipaexg.ttf");

    private ChordSheetPdfRenderer() {
    }

    private static byte[] loadResource(String path) {
        try (InputStream in = ChordSheetPdfRenderer.class.getResourceAsStream(path)) {
            if (in == null) {
                throw new IllegalStateException("フォントが見つかりません: " + path);
            }
            return in.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * PDF を作る。
     *
     * @param sheet      コード譜
     * @param attachment PDF に添付する元データ（.xlsx）。null なら添付しない
     * @param title      文書のタイトル
     */
    public static byte[] render(ChordSheet sheet, byte[] attachment, String title) {
        ChordSheet data = sheet.normalize();
        try (PDDocument doc = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Fonts fonts = new Fonts(doc);
            new Layout(doc, data, fonts).draw();
            if (attachment != null) {
                attach(doc, attachment);
            }
            PDDocumentInformation info = doc.getDocumentInformation();
            info.setTitle(title);
            info.setCreator("Music-Tracker");
            info.setCreationDate(Calendar.getInstance());
            doc.save(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("PDF の作成に失敗しました", e);
        }
    }

    private static void attach(PDDocument doc, byte[] xlsx) throws IOException {
        PDEmbeddedFile ef = new PDEmbeddedFile(doc, new ByteArrayInputStream(xlsx));
        ef.setSubtype("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        ef.setSize(xlsx.length);
        ef.setCreationDate(Calendar.getInstance());
        PDComplexFileSpecification fs = new PDComplexFileSpecification();
        fs.setFile(ATTACHMENT_NAME);
        fs.setFileUnicode(ATTACHMENT_NAME);
        fs.setEmbeddedFile(ef);
        fs.setEmbeddedFileUnicode(ef);
        fs.setFileDescription("コード譜の元データ（Excel）");
        PDEmbeddedFilesNameTreeNode tree = new PDEmbeddedFilesNameTreeNode();
        tree.setNames(Map.of(ATTACHMENT_NAME, fs));
        PDDocumentNameDictionary names = new PDDocumentNameDictionary(doc.getDocumentCatalog());
        names.setEmbeddedFiles(tree);
        doc.getDocumentCatalog().setNames(names);
    }

    /** 主フォント（M PLUS Rounded 1c）と、収録されていない文字用の予備フォント（IPAex ゴシック）。 */
    private static final class Fonts {
        private final PDDocument doc;
        final PDType0Font primary;
        /** 予備フォントは大きい（約6MB）ため、主フォントにない文字が出てきたときだけ読み込む。 */
        private PDType0Font fallback;
        private final TrueTypeFont primaryTtf;
        private final Map<Integer, Boolean> primaryHas = new HashMap<>();

        Fonts(PDDocument doc) throws IOException {
            this.doc = doc;
            primaryTtf = new TTFParser().parse(new RandomAccessReadBuffer(PRIMARY_FONT));
            primary = PDType0Font.load(doc, primaryTtf, true);
        }

        private PDType0Font fallback() {
            if (fallback == null) {
                try {
                    fallback = PDType0Font.load(doc, new ByteArrayInputStream(FALLBACK_FONT), true);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            }
            return fallback;
        }

        PDType0Font fontFor(int codePoint) {
            boolean has = primaryHas.computeIfAbsent(codePoint, cp -> {
                try {
                    return primaryTtf.getUnicodeCmapLookup().getGlyphId(cp) > 0;
                } catch (IOException e) {
                    return false;
                }
            });
            return has ? primary : fallback();
        }

        /** 同じフォントで描ける文字ごとに区切る。どちらのフォントにもない文字は「?」にする。 */
        List<Run> runs(String text) {
            List<Run> runs = new ArrayList<>();
            StringBuilder sb = new StringBuilder();
            PDType0Font current = null;
            for (int i = 0; i < text.length(); ) {
                int cp = text.codePointAt(i);
                i += Character.charCount(cp);
                if (Character.isISOControl(cp)) {
                    continue;
                }
                PDType0Font f = fontFor(cp);
                String ch = new String(Character.toChars(cp));
                if (f != primary && !canEncode(f, ch)) {
                    f = primary;
                    ch = "?";
                }
                if (current != null && f != current) {
                    runs.add(new Run(sb.toString(), current));
                    sb.setLength(0);
                }
                current = f;
                sb.append(ch);
            }
            if (current != null && !sb.isEmpty()) {
                runs.add(new Run(sb.toString(), current));
            }
            return runs;
        }

        private static boolean canEncode(PDType0Font font, String ch) {
            try {
                font.encode(ch);
                return true;
            } catch (IOException | IllegalArgumentException e) {
                return false;
            }
        }
    }

    private record Run(String text, PDType0Font font) {
        float width(float size) {
            try {
                return font.getStringWidth(text) / 1000f * size;
            } catch (IOException e) {
                return 0;
            }
        }
    }

    /** ページ割りと描画。 */
    private static final class Layout {
        private final PDDocument doc;
        private final ChordSheet data;
        private final Fonts fonts;
        private final float scale;
        private final float[] colX;
        /** 結合セルで隠れるセル（描画しない）。 */
        private final boolean[][] covered;
        /** 各行から始まる結合が最後に覆う行（改ページで分断しないため）。 */
        private final int[] groupEnd;

        Layout(PDDocument doc, ChordSheet data, Fonts fonts) {
            this.doc = doc;
            this.data = data;
            this.fonts = fonts;
            int rows = data.rowCount();
            int cols = data.colCount();
            float totalPx = 0;
            for (int c = 0; c < cols; c++) {
                totalPx += data.colWidth(c);
            }
            float usable = PDRectangle.A4.getWidth() - MARGIN * 2;
            this.scale = totalPx <= 0 ? NATURAL_SCALE : Math.min(NATURAL_SCALE, usable / totalPx);
            this.colX = new float[cols + 1];
            for (int c = 0; c < cols; c++) {
                colX[c + 1] = colX[c] + data.colWidth(c) * scale;
            }
            this.covered = new boolean[rows][cols];
            this.groupEnd = new int[rows];
            for (int r = 0; r < rows; r++) {
                groupEnd[r] = r;
            }
            for (Map.Entry<String, int[]> m : data.getMergeCells().entrySet()) {
                int[] rc = ChordSheet.parseCellName(m.getKey());
                int[] span = m.getValue();
                if (rc[0] >= rows || rc[1] >= cols) {
                    continue;
                }
                int lastRow = Math.min(rows - 1, rc[0] + span[1] - 1);
                int lastCol = Math.min(cols - 1, rc[1] + span[0] - 1);
                for (int r = rc[0]; r <= lastRow; r++) {
                    for (int c = rc[1]; c <= lastCol; c++) {
                        covered[r][c] = !(r == rc[0] && c == rc[1]);
                    }
                }
                groupEnd[rc[0]] = Math.max(groupEnd[rc[0]], lastRow);
            }
        }

        void draw() throws IOException {
            int rows = data.rowCount();
            float pageTop = PDRectangle.A4.getHeight() - MARGIN;
            float usableHeight = PDRectangle.A4.getHeight() - MARGIN * 2;
            int r = 0;
            if (rows == 0) {
                doc.addPage(new PDPage(PDRectangle.A4));
                return;
            }
            while (r < rows) {
                // 1ページに入る行を決める（結合セルの行はまとめて扱う）
                int start = r;
                float used = 0;
                while (r < rows) {
                    int end = r;
                    for (int k = r; k <= end; k++) {
                        end = Math.max(end, groupEnd[k]);
                    }
                    float h = 0;
                    for (int k = r; k <= end; k++) {
                        h += data.rowHeight(k) * scale;
                    }
                    if (used + h > usableHeight && r > start) {
                        break;
                    }
                    used += h;
                    r = end + 1;
                }
                drawPage(start, r - 1, pageTop);
            }
        }

        private void drawPage(int firstRow, int lastRow, float top) throws IOException {
            PDPage page = new PDPage(PDRectangle.A4);
            doc.addPage(page);
            float[] rowY = new float[lastRow - firstRow + 2];
            rowY[0] = top;
            for (int r = firstRow; r <= lastRow; r++) {
                rowY[r - firstRow + 1] = rowY[r - firstRow] - data.rowHeight(r) * scale;
            }
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                for (int pass = 0; pass < 3; pass++) {
                    for (int r = firstRow; r <= lastRow; r++) {
                        for (int c = 0; c < data.colCount(); c++) {
                            if (covered[r][c]) {
                                continue;
                            }
                            int[] span = data.getMergeCells().getOrDefault(ChordSheet.cellName(r, c), new int[]{1, 1});
                            int lastC = Math.min(data.colCount() - 1, c + span[0] - 1);
                            int lastR = Math.min(lastRow, r + span[1] - 1);
                            float x0 = MARGIN + colX[c];
                            float x1 = MARGIN + colX[lastC + 1];
                            float y0 = rowY[r - firstRow];
                            float y1 = rowY[lastR - firstRow + 1];
                            CellStyleCss style = CellStyleCss.parse(data.styleAt(r, c));
                            switch (pass) {
                                case 0 -> drawBackground(cs, style, x0, y0, x1, y1);
                                case 1 -> drawText(cs, style, data.value(r, c), x0, y0, x1, y1);
                                default -> drawBorders(cs, r, c, lastR, lastC, x0, y0, x1, y1);
                            }
                        }
                    }
                }
            }
        }

        private void drawBackground(PDPageContentStream cs, CellStyleCss s,
                                    float x0, float y0, float x1, float y1) throws IOException {
            if (s.background == null) {
                return;
            }
            cs.setNonStrokingColor(Color.decode(s.background));
            cs.addRect(x0, y1, x1 - x0, y0 - y1);
            cs.fill();
        }

        private void drawText(PDPageContentStream cs, CellStyleCss s, String value,
                              float x0, float y0, float x1, float y1) throws IOException {
            if (value == null || value.isBlank()) {
                return;
            }
            float size = (s.fontSize == null ? 13 : s.fontSize) * scale;
            Color color = s.color == null ? Color.BLACK : Color.decode(s.color);
            String[] lines = value.split("\\r?\\n");
            float lineHeight = size * 1.2f;
            float blockHeight = lineHeight * (lines.length - 1) + size;
            float pad = CELL_PADDING_PX * scale;
            // 1行目のベースライン位置（文字の高さを概ね 0.8em として上下位置を合わせる）
            float baseline = switch (s.verticalAlign) {
                case "top" -> y0 - pad - size * 0.85f;
                case "bottom" -> y1 + pad + size * 0.2f + lineHeight * (lines.length - 1);
                default -> (y0 + y1) / 2f + blockHeight / 2f - size * 0.85f;
            };
            for (String line : lines) {
                List<Run> runs = fonts.runs(line);
                float width = 0;
                for (Run run : runs) {
                    width += run.width(size);
                }
                float x = switch (s.textAlign) {
                    case "center" -> (x0 + x1) / 2f - width / 2f;
                    case "right" -> x1 - pad - width;
                    default -> x0 + pad;
                };
                float startX = x;
                for (Run run : runs) {
                    cs.beginText();
                    cs.setFont(run.font(), size);
                    cs.setNonStrokingColor(color);
                    if (s.bold) {
                        // 太字フォントは持たないため、輪郭を少し太らせて表現する
                        cs.setRenderingMode(RenderingMode.FILL_STROKE);
                        cs.setStrokingColor(color);
                        cs.setLineWidth(size * 0.04f);
                    } else {
                        cs.setRenderingMode(RenderingMode.FILL);
                    }
                    if (s.italic) {
                        cs.setTextMatrix(new org.apache.pdfbox.util.Matrix(1, 0, 0.2f, 1, x, baseline));
                    } else {
                        cs.newLineAtOffset(x, baseline);
                    }
                    cs.showText(run.text());
                    cs.endText();
                    x += run.width(size);
                }
                if (s.underline && width > 0) {
                    cs.setStrokingColor(color);
                    cs.setLineWidth(Math.max(0.5f, size * 0.05f));
                    cs.moveTo(startX, baseline - size * 0.12f);
                    cs.lineTo(startX + width, baseline - size * 0.12f);
                    cs.stroke();
                }
                baseline -= lineHeight;
            }
        }

        /** 結合セルは、上端・左端を起点セル、右端を右上のセル、下端を左下のセルの罫線で描く。 */
        private void drawBorders(PDPageContentStream cs, int r, int c, int lastR, int lastC,
                                 float x0, float y0, float x1, float y1) throws IOException {
            CellStyleCss anchor = CellStyleCss.parse(data.styleAt(r, c));
            CellStyleCss right = CellStyleCss.parse(data.styleAt(r, lastC));
            CellStyleCss bottom = CellStyleCss.parse(data.styleAt(lastR, c));
            line(cs, anchor.borders.get(CellStyleCss.Side.TOP), x0, y0, x1, y0);
            line(cs, anchor.borders.get(CellStyleCss.Side.LEFT), x0, y0, x0, y1);
            line(cs, right.borders.get(CellStyleCss.Side.RIGHT), x1, y0, x1, y1);
            line(cs, bottom.borders.get(CellStyleCss.Side.BOTTOM), x0, y1, x1, y1);
        }

        private void line(PDPageContentStream cs, CellStyleCss.Border b,
                          float xa, float ya, float xb, float yb) throws IOException {
            if (b == null) {
                return;
            }
            float w = Math.max(0.4f, b.width() * scale * 0.9f);
            cs.setStrokingColor(Color.decode(b.color()));
            cs.setLineWidth("double".equals(b.style()) ? w / 3f : w);
            switch (b.style()) {
                case "dashed" -> cs.setLineDashPattern(new float[]{w * 4, w * 2}, 0);
                case "dotted" -> cs.setLineDashPattern(new float[]{w, w * 1.5f}, 0);
                default -> cs.setLineDashPattern(new float[]{}, 0);
            }
            if ("double".equals(b.style())) {
                float off = w / 3f;
                boolean horizontal = ya == yb;
                cs.moveTo(xa - (horizontal ? 0 : off), ya + (horizontal ? off : 0));
                cs.lineTo(xb - (horizontal ? 0 : off), yb + (horizontal ? off : 0));
                cs.moveTo(xa + (horizontal ? 0 : off), ya - (horizontal ? off : 0));
                cs.lineTo(xb + (horizontal ? 0 : off), yb - (horizontal ? off : 0));
            } else {
                cs.moveTo(xa, ya);
                cs.lineTo(xb, yb);
            }
            cs.stroke();
            cs.setLineDashPattern(new float[]{}, 0);
        }
    }
}
