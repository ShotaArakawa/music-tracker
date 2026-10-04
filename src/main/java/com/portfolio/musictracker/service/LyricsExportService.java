package com.portfolio.musictracker.service;

import com.portfolio.musictracker.dto.SongDetailForm.SectionDto;
import com.portfolio.musictracker.pdf.PdfFonts;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.state.RenderingMode;
import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

/**
 * 歌詞を Word（.docx）・テキスト（.txt）・PDF に書き出す。
 * <p>
 * どの形式も「曲名」→「Key：C　BPM：120」（横並び）→ 各セクション「【セクション名】＋本文」の順に並べる。
 * 見出しも本文もないセクションは書き出さない。
 */
@Service
public class LyricsExportService {

    /** Word で使う日本語フォント（Windows / Mac の Office に標準で入っているもの）。 */
    private static final String WORD_FONT = "游ゴシック";
    private static final Color HEADING_COLOR = new Color(0x0D, 0x6E, 0xFD);

    /** 書き出す1セクション分（見出しと本文の行）。 */
    record Section(String heading, List<String> lines) {
    }

    /** 空のセクションを除き、改行を行に分ける。 */
    static List<Section> normalize(List<SectionDto> sections) {
        List<Section> result = new ArrayList<>();
        if (sections == null) {
            return result;
        }
        for (SectionDto s : sections) {
            String name = s.getName() == null ? "" : s.getName().trim();
            String content = s.getContent() == null ? "" : s.getContent().replace("\r\n", "\n").replace('\r', '\n');
            if (name.isEmpty() && content.isBlank()) {
                continue;
            }
            // 末尾の空行は落とし、途中の空行（段落の区切り）は残す
            List<String> lines = new ArrayList<>(List.of(content.stripTrailing().split("\n", -1)));
            if (lines.size() == 1 && lines.get(0).isEmpty()) {
                lines.clear();
            }
            result.add(new Section(name.isEmpty() ? "" : "【" + name + "】", lines));
        }
        return result;
    }

    // ===================== テキスト =====================

    /** UTF-8・改行 CRLF のテキストにする（Windows のメモ帳でもそのまま読める）。 */
    public byte[] toText(String title, String keyBpm, List<SectionDto> sections) {
        StringBuilder sb = new StringBuilder();
        sb.append(title).append("\r\n").append(keyBpm).append("\r\n\r\n");
        for (Section s : normalize(sections)) {
            if (!s.heading().isEmpty()) {
                sb.append(s.heading()).append("\r\n");
            }
            for (String line : s.lines()) {
                sb.append(line).append("\r\n");
            }
            sb.append("\r\n");
        }
        return sb.toString().stripTrailing().concat("\r\n").getBytes(StandardCharsets.UTF_8);
    }

    // ===================== Word =====================

    public byte[] toWord(String title, String keyBpm, List<SectionDto> sections) {
        try (XWPFDocument doc = new XWPFDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            XWPFParagraph titlePara = doc.createParagraph();
            titlePara.setAlignment(ParagraphAlignment.CENTER);
            titlePara.setSpacingAfter(60);
            XWPFRun titleRun = run(titlePara, title, 18);
            titleRun.setBold(true);
            // タイトルの下に Key と BPM を横並びで
            XWPFParagraph metaPara = doc.createParagraph();
            metaPara.setAlignment(ParagraphAlignment.CENTER);
            metaPara.setSpacingAfter(360);
            run(metaPara, keyBpm, 11).setColor("5C6670");

            for (Section s : normalize(sections)) {
                if (!s.heading().isEmpty()) {
                    XWPFParagraph h = doc.createParagraph();
                    h.setSpacingBefore(240);
                    h.setSpacingAfter(80);
                    h.setKeepNext(true);
                    XWPFRun hr = run(h, s.heading(), 12);
                    hr.setBold(true);
                    hr.setColor("0D6EFD");
                }
                if (!s.lines().isEmpty()) {
                    // 1セクションの本文は1段落にし、行は改行でつなぐ（コピーしても崩れにくい）
                    XWPFParagraph p = doc.createParagraph();
                    p.setSpacingAfter(120);
                    XWPFRun r = run(p, null, 11);
                    for (int i = 0; i < s.lines().size(); i++) {
                        if (i > 0) {
                            r.addBreak();
                        }
                        r.setText(s.lines().get(i));
                    }
                }
            }
            doc.getProperties().getCoreProperties().setTitle(title);
            doc.getProperties().getCoreProperties().setCreator("Music-Tracker");
            doc.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Word ファイルの作成に失敗しました", e);
        }
    }

    private static XWPFRun run(XWPFParagraph p, String text, int sizePt) {
        XWPFRun r = p.createRun();
        r.setFontFamily(WORD_FONT, XWPFRun.FontCharRange.ascii);
        r.setFontFamily(WORD_FONT, XWPFRun.FontCharRange.hAnsi);
        r.setFontFamily(WORD_FONT, XWPFRun.FontCharRange.eastAsia);
        r.setFontSize(sizePt);
        if (text != null) {
            r.setText(text);
        }
        return r;
    }

    // ===================== PDF =====================

    private static final float MARGIN = 56f;
    private static final float TITLE_SIZE = 18f;
    private static final float HEADING_SIZE = 12.5f;
    private static final float BODY_SIZE = 11f;
    private static final float BODY_LEADING = 19f;
    private static final float META_SIZE = 10.5f;
    private static final Color META_COLOR = new Color(0x5C, 0x66, 0x70);

    /** A4 縦の PDF にする。長い行は折り返し、ページに収まらなければ改ページする。 */
    public byte[] toPdf(String title, String keyBpm, List<SectionDto> sections) {
        try (PDDocument doc = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            new PdfWriter(doc, new PdfFonts(doc)).write(title, keyBpm, normalize(sections));
            doc.getDocumentInformation().setTitle(title);
            doc.getDocumentInformation().setCreator("Music-Tracker");
            doc.getDocumentInformation().setCreationDate(Calendar.getInstance());
            doc.save(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("PDF の作成に失敗しました", e);
        }
    }

    /** 上から順に文字を置いていき、下端に来たら改ページする。 */
    private static final class PdfWriter {
        private final PDDocument doc;
        private final PdfFonts fonts;
        private final float width = PDRectangle.A4.getWidth() - MARGIN * 2;
        private PDPageContentStream cs;
        private float y;

        PdfWriter(PDDocument doc, PdfFonts fonts) {
            this.doc = doc;
            this.fonts = fonts;
        }

        void write(String title, String keyBpm, List<Section> sections) throws IOException {
            newPage();
            for (String line : wrap(title, TITLE_SIZE)) {
                float w = fonts.width(line, TITLE_SIZE);
                text(line, MARGIN + (width - w) / 2, TITLE_SIZE, Color.BLACK, true);
                y -= TITLE_SIZE * 1.5f;
            }
            // タイトルの下に Key と BPM を横並びで
            float metaW = fonts.width(keyBpm, META_SIZE);
            text(keyBpm, MARGIN + (width - metaW) / 2, META_SIZE, META_COLOR, false);
            y -= META_SIZE * 1.6f;
            y -= 14;
            for (Section s : sections) {
                if (!s.heading().isEmpty()) {
                    // 見出しだけがページ末尾に取り残されないよう、本文1行分の余裕も見る
                    ensureSpace(HEADING_SIZE * 2 + BODY_LEADING);
                    y -= HEADING_SIZE * 0.8f;
                    for (String line : wrap(s.heading(), HEADING_SIZE)) {
                        text(line, MARGIN, HEADING_SIZE, HEADING_COLOR, true);
                        y -= HEADING_SIZE * 1.6f;
                    }
                }
                for (String raw : s.lines()) {
                    for (String line : wrap(raw, BODY_SIZE)) {
                        ensureSpace(BODY_LEADING);
                        text(line, MARGIN, BODY_SIZE, Color.BLACK, false);
                        y -= BODY_LEADING;
                    }
                }
                y -= BODY_LEADING * 0.6f;
            }
            cs.close();
            addPageNumbers();
        }

        private void newPage() throws IOException {
            if (cs != null) {
                cs.close();
            }
            PDPage page = new PDPage(PDRectangle.A4);
            doc.addPage(page);
            cs = new PDPageContentStream(doc, page);
            y = PDRectangle.A4.getHeight() - MARGIN;
        }

        private void ensureSpace(float needed) throws IOException {
            if (y - needed < MARGIN) {
                newPage();
            }
        }

        /** 1行をページ幅で折り返す（空行は空行のまま残す）。 */
        private List<String> wrap(String text, float size) {
            List<String> lines = new ArrayList<>();
            StringBuilder current = new StringBuilder();
            for (int i = 0; i < text.length(); ) {
                int cp = text.codePointAt(i);
                String ch = new String(Character.toChars(cp));
                i += Character.charCount(cp);
                if (current.length() > 0 && fonts.width(current + ch, size) > width) {
                    lines.add(current.toString());
                    current.setLength(0);
                }
                current.append(ch);
            }
            lines.add(current.toString());
            return lines;
        }

        private void text(String line, float x, float size, Color color, boolean bold) throws IOException {
            if (line.isEmpty()) {
                return;
            }
            float baseline = y - size;
            for (PdfFonts.Run run : fonts.runs(line)) {
                cs.beginText();
                cs.setFont(run.font(), size);
                cs.setNonStrokingColor(color);
                if (bold) {
                    // 太字フォントは持たないため、輪郭を少し太らせて表現する
                    cs.setRenderingMode(RenderingMode.FILL_STROKE);
                    cs.setStrokingColor(color);
                    cs.setLineWidth(size * 0.035f);
                } else {
                    cs.setRenderingMode(RenderingMode.FILL);
                }
                cs.newLineAtOffset(x, baseline);
                cs.showText(run.text());
                cs.endText();
                x += run.width(size);
            }
        }

        /** 2ページ以上になったときだけ、下にページ番号（n / 全体）を入れる。 */
        private void addPageNumbers() throws IOException {
            int total = doc.getNumberOfPages();
            if (total < 2) {
                return;
            }
            for (int i = 0; i < total; i++) {
                PDPage page = doc.getPage(i);
                try (PDPageContentStream footer = new PDPageContentStream(
                        doc, page, PDPageContentStream.AppendMode.APPEND, true, true)) {
                    String label = (i + 1) + " / " + total;
                    float size = 9f;
                    float w = fonts.width(label, size);
                    footer.beginText();
                    footer.setFont(fonts.runs(label).get(0).font(), size);
                    footer.setNonStrokingColor(Color.GRAY);
                    footer.newLineAtOffset((PDRectangle.A4.getWidth() - w) / 2, MARGIN / 2);
                    footer.showText(label);
                    footer.endText();
                }
            }
        }
    }
}
