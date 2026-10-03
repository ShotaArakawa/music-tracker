package com.portfolio.musictracker.pdf;

import org.apache.fontbox.ttf.TTFParser;
import org.apache.fontbox.ttf.TrueTypeFont;
import org.apache.pdfbox.io.RandomAccessReadBuffer;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.font.PDType0Font;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * PDF に日本語を描くためのフォント（コード譜・歌詞の PDF 出力で共通）。
 * <p>
 * 主フォントはテンプレートと同じ M PLUS Rounded 1c。収録されていない文字は IPAex ゴシックで補う。
 * 予備フォントは大きい（約6MB）ため、必要な文字が出てきたときだけ読み込む。
 */
public final class PdfFonts {

    private static final byte[] PRIMARY_FONT = loadResource("/fonts/MPLUSRounded1c-Regular.ttf");
    private static final byte[] FALLBACK_FONT = loadResource("/fonts/ipaexg.ttf");

    private final PDDocument doc;
    private final PDType0Font primary;
    private final TrueTypeFont primaryTtf;
    private final Map<Integer, Boolean> primaryHas = new HashMap<>();
    private PDType0Font fallback;

    /** 同じフォントで描ける文字のまとまり。 */
    public record Run(String text, PDType0Font font) {
        /** 指定サイズで描いたときの幅（pt）。 */
        public float width(float size) {
            try {
                return font.getStringWidth(text) / 1000f * size;
            } catch (IOException e) {
                return 0;
            }
        }
    }

    public PdfFonts(PDDocument doc) throws IOException {
        this.doc = doc;
        this.primaryTtf = new TTFParser().parse(new RandomAccessReadBuffer(PRIMARY_FONT));
        this.primary = PDType0Font.load(doc, primaryTtf, true);
    }

    private static byte[] loadResource(String path) {
        try (InputStream in = PdfFonts.class.getResourceAsStream(path)) {
            if (in == null) {
                throw new IllegalStateException("フォントが見つかりません: " + path);
            }
            return in.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
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

    private PDType0Font fontFor(int codePoint) {
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
    public List<Run> runs(String text) {
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

    /** 1行の文字列を描いたときの幅（pt）。 */
    public float width(String text, float size) {
        float w = 0;
        for (Run run : runs(text)) {
            w += run.width(size);
        }
        return w;
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
