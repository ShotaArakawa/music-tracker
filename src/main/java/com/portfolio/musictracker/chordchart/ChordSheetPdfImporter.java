package com.portfolio.musictracker.chordchart;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentNameDictionary;
import org.apache.pdfbox.pdmodel.common.PDNameTreeNode;
import org.apache.pdfbox.pdmodel.common.filespecification.PDComplexFileSpecification;
import org.apache.pdfbox.pdmodel.common.filespecification.PDEmbeddedFile;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * PDF からコード譜を読み取る。
 * <ol>
 *     <li>このアプリが書き出した PDF（元データの .xlsx が添付されている）→ 添付から書式ごと完全に復元</li>
 *     <li>文字データを持つ PDF → 文字の位置から行・列を推定して表に並べる（罫線や色は再現しない）</li>
 *     <li>スキャン画像・手書きなど文字データのない PDF → 読み取れない旨のエラー</li>
 * </ol>
 */
public final class ChordSheetPdfImporter {

    /** 同じ列とみなす文字の開始位置のずれ（pt）。 */
    private static final float COLUMN_TOLERANCE = 6f;
    private static final int MAX_IMPORT_COLS = 40;

    private ChordSheetPdfImporter() {
    }

    public static ChordSheet read(byte[] pdf) {
        try (PDDocument doc = Loader.loadPDF(pdf)) {
            byte[] attached = findAttachedSheet(doc);
            if (attached != null) {
                return ChordSheetExcelConverter.read(new ByteArrayInputStream(attached));
            }
            return fromText(doc);
        } catch (InvalidPasswordException e) {
            throw new IllegalArgumentException("パスワード付きの PDF は読み込めません", e);
        } catch (IOException e) {
            throw new IllegalArgumentException("PDF ファイルとして読み込めませんでした", e);
        }
    }

    /** 添付ファイルのうち .xlsx（このアプリの元データ）を探す。 */
    private static byte[] findAttachedSheet(PDDocument doc) throws IOException {
        PDDocumentNameDictionary names = doc.getDocumentCatalog().getNames();
        if (names == null || names.getEmbeddedFiles() == null) {
            return null;
        }
        Map<String, PDComplexFileSpecification> files = new LinkedHashMap<>();
        collect(names.getEmbeddedFiles(), files);
        PDComplexFileSpecification spec = files.get(ChordSheetPdfRenderer.ATTACHMENT_NAME);
        if (spec == null) {
            spec = files.entrySet().stream()
                    .filter(e -> e.getKey().toLowerCase(Locale.ROOT).endsWith(".xlsx"))
                    .map(Map.Entry::getValue).findFirst().orElse(null);
        }
        if (spec == null) {
            return null;
        }
        PDEmbeddedFile ef = spec.getEmbeddedFileUnicode() != null
                ? spec.getEmbeddedFileUnicode() : spec.getEmbeddedFile();
        return ef == null ? null : ef.toByteArray();
    }

    private static void collect(PDNameTreeNode<PDComplexFileSpecification> node,
                                Map<String, PDComplexFileSpecification> out) throws IOException {
        if (node.getNames() != null) {
            out.putAll(node.getNames());
        }
        if (node.getKids() != null) {
            for (PDNameTreeNode<PDComplexFileSpecification> kid : node.getKids()) {
                collect(kid, out);
            }
        }
    }

    // ===================== 文字の位置から表を組み立てる =====================

    private record Glyph(int page, float x, float y, float width, float size, String text, boolean bold) {
    }

    /** 1行の中で、間隔の空いた文字のまとまり。 */
    private record Token(int page, float x, float y, float endX, float size, String text, boolean bold) {
    }

    private static ChordSheet fromText(PDDocument doc) throws IOException {
        List<Glyph> glyphs = new ArrayList<>();
        PDFTextStripper stripper = new PDFTextStripper() {
            @Override
            protected void processTextPosition(TextPosition t) {
                String u = t.getUnicode();
                if (u == null || u.isBlank()) {
                    return;
                }
                String fontName = t.getFont() == null || t.getFont().getName() == null
                        ? "" : t.getFont().getName();
                glyphs.add(new Glyph(getCurrentPageNo(), t.getXDirAdj(), t.getYDirAdj(), t.getWidthDirAdj(),
                        Math.max(1f, t.getFontSizeInPt()), u, isBoldFont(fontName)));
            }
        };
        stripper.setSortByPosition(true);
        stripper.getText(doc);
        if (glyphs.isEmpty()) {
            throw new IllegalArgumentException("この PDF には文字データがないため読み取れません"
                    + "（スキャン画像や手書きの PDF は対象外です）。Excel 形式でインポートしてください。");
        }

        // ページ → 行 → 字句 に分ける
        List<List<Token>> lines = toLines(glyphs);
        List<Float> columns = columnStarts(lines);
        int cols = columns.size();

        ChordSheet sheet = new ChordSheet();
        List<List<String>> data = new ArrayList<>();
        List<Integer> heights = new ArrayList<>();
        int prevPage = lines.get(0).get(0).page();
        for (List<Token> line : lines) {
            int page = line.get(0).page();
            if (page != prevPage) {
                // ページの区切りは空行で表す
                data.add(emptyRow(cols));
                heights.add(ChordSheet.DEFAULT_ROW_HEIGHT);
                prevPage = page;
            }
            int r = data.size();
            String[] row = new String[cols];
            float maxSize = 0;
            for (Token t : line) {
                int c = nearestColumn(columns, t.x());
                row[c] = (row[c] == null) ? t.text() : row[c] + " " + t.text();
                maxSize = Math.max(maxSize, t.size());
                CellStyleCss s = new CellStyleCss();
                s.fontSize = Math.round(t.size() * 96 / 72);
                s.bold = t.bold();
                sheet.getStyle().put(ChordSheet.cellName(r, c), s.toCss());
            }
            List<String> values = new ArrayList<>(cols);
            for (String v : row) {
                values.add(v == null ? "" : v);
            }
            data.add(values);
            heights.add(Math.max(ChordSheet.DEFAULT_ROW_HEIGHT, Math.round(maxSize * 1.6f * 96 / 72)));
        }

        List<Integer> widths = new ArrayList<>(cols);
        for (int c = 0; c < cols; c++) {
            float pt = (c + 1 < cols) ? columns.get(c + 1) - columns.get(c) : maxTokenWidth(lines, columns, c);
            widths.add(Math.max(24, Math.round(pt * 96 / 72)));
        }
        sheet.setData(data);
        sheet.setColWidths(widths);
        sheet.setRowHeights(heights);
        return sheet.normalize();
    }

    private static boolean isBoldFont(String name) {
        String n = name.toLowerCase(Locale.ROOT);
        return n.contains("bold") || n.contains("heavy") || n.contains("black")
                || n.matches(".*-w[6-9].*");
    }

    /** 文字を行（y がほぼ同じもの）にまとめ、行内は間隔で字句に区切る。 */
    private static List<List<Token>> toLines(List<Glyph> glyphs) {
        glyphs.sort(Comparator.comparingInt(Glyph::page).thenComparing(Glyph::y).thenComparing(Glyph::x));
        List<List<Glyph>> rawLines = new ArrayList<>();
        List<Glyph> current = new ArrayList<>();
        for (Glyph g : glyphs) {
            if (!current.isEmpty()) {
                Glyph head = current.get(0);
                float tol = Math.max(2f, Math.min(head.size(), g.size()) * 0.45f);
                if (g.page() != head.page() || Math.abs(g.y() - head.y()) > tol) {
                    rawLines.add(current);
                    current = new ArrayList<>();
                }
            }
            current.add(g);
        }
        rawLines.add(current);

        List<List<Token>> lines = new ArrayList<>();
        for (List<Glyph> line : rawLines) {
            line.sort(Comparator.comparing(Glyph::x));
            List<Token> tokens = new ArrayList<>();
            StringBuilder sb = new StringBuilder();
            Glyph first = null;
            Glyph last = null;
            float maxSize = 0;
            boolean bold = false;
            for (Glyph g : line) {
                if (last != null) {
                    float gap = g.x() - (last.x() + last.width());
                    if (gap > Math.max(3f, Math.min(last.size(), g.size()) * 0.6f)) {
                        tokens.add(new Token(first.page(), first.x(), first.y(),
                                last.x() + last.width(), maxSize, normalize(sb), bold));
                        sb.setLength(0);
                        first = null;
                        maxSize = 0;
                        bold = false;
                    }
                }
                if (first == null) {
                    first = g;
                }
                sb.append(g.text());
                maxSize = Math.max(maxSize, g.size());
                bold |= g.bold();
                last = g;
            }
            if (first != null) {
                tokens.add(new Token(first.page(), first.x(), first.y(),
                        last.x() + last.width(), maxSize, normalize(sb), bold));
            }
            tokens.removeIf(t -> t.text().isBlank());
            if (!tokens.isEmpty()) {
                lines.add(tokens);
            }
        }
        return lines;
    }

    private static String normalize(CharSequence s) {
        return Normalizer.normalize(s, Normalizer.Form.NFKC).trim();
    }

    /**
     * 字句の開始位置を近いものどうしでまとめ、列の左端の一覧を作る。
     * 列が多すぎる場合は許容幅を広げてまとめ直す。
     */
    private static List<Float> columnStarts(List<List<Token>> lines) {
        List<Float> xs = new ArrayList<>();
        for (List<Token> line : lines) {
            for (Token t : line) {
                xs.add(t.x());
            }
        }
        xs.sort(Float::compare);
        float tol = COLUMN_TOLERANCE;
        List<Float> starts;
        do {
            starts = new ArrayList<>();
            float clusterStart = Float.NaN;
            for (float x : xs) {
                if (Float.isNaN(clusterStart) || x - clusterStart > tol) {
                    starts.add(x);
                    clusterStart = x;
                }
            }
            tol *= 1.5f;
        } while (starts.size() > MAX_IMPORT_COLS);
        return starts;
    }

    private static int nearestColumn(List<Float> starts, float x) {
        int best = 0;
        for (int i = 0; i < starts.size(); i++) {
            if (starts.get(i) <= x + 0.5f) {
                best = i;
            }
        }
        return best;
    }

    private static float maxTokenWidth(List<List<Token>> lines, List<Float> columns, int col) {
        float max = 60f;
        for (List<Token> line : lines) {
            for (Token t : line) {
                if (nearestColumn(columns, t.x()) == col) {
                    max = Math.max(max, t.endX() - t.x() + 8f);
                }
            }
        }
        return max;
    }

    private static List<String> emptyRow(int cols) {
        List<String> row = new ArrayList<>(cols);
        for (int i = 0; i < cols; i++) {
            row.add("");
        }
        return row;
    }
}
