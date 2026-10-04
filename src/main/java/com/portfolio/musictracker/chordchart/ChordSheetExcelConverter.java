package com.portfolio.musictracker.chordchart;

import org.apache.poi.hssf.usermodel.HSSFCellStyle;
import org.apache.poi.hssf.usermodel.HSSFFont;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.hssf.util.HSSFColor;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Color;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.FormulaEvaluator;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.PrintSetup;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.apache.poi.xssf.usermodel.XSSFFont;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xssf.usermodel.extensions.XSSFCellBorder.BorderSide;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * コード譜（{@link ChordSheet}）と Excel ファイルの相互変換。
 * <p>
 * 読み込みは .xlsx / .xls の先頭（アクティブ）シートの値・書式（文字・塗り・配置・罫線）・
 * セル結合・列幅・行の高さを取り込む。書き出しは同じ要素を .xlsx に復元する。
 * 数式は計算結果の値として取り込む。
 */
public final class ChordSheetExcelConverter {

    /** テンプレートで使われているフォント。書き出し時の既定フォントにする。 */
    public static final String FONT_NAME = "M PLUS Rounded 1c";

    private ChordSheetExcelConverter() {
    }

    // ===================== 読み込み =====================

    /**
     * Excel ファイルを読み込む。
     *
     * @throws IllegalArgumentException Excel として読めない場合
     */
    public static ChordSheet read(InputStream in) {
        try (Workbook wb = WorkbookFactory.create(in)) {
            return read(wb);
        } catch (IOException | RuntimeException e) {
            if (e instanceof IllegalArgumentException iae && iae.getMessage() != null
                    && iae.getMessage().startsWith("コード譜")) {
                throw iae;
            }
            throw new IllegalArgumentException("Excel ファイルとして読み込めませんでした", e);
        }
    }

    static ChordSheet read(Workbook wb) {
        Sheet sheet = wb.getSheetAt(wb.getActiveSheetIndex());
        DataFormatter formatter = new DataFormatter(Locale.JAPAN);
        FormulaEvaluator evaluator = wb.getCreationHelper().createFormulaEvaluator();

        // 表の大きさ：値・書式のあるセルと結合範囲の最大まで
        int rows = sheet.getLastRowNum() + 1;
        int cols = 0;
        for (Row row : sheet) {
            cols = Math.max(cols, row.getLastCellNum());
        }
        for (CellRangeAddress m : sheet.getMergedRegions()) {
            rows = Math.max(rows, m.getLastRow() + 1);
            cols = Math.max(cols, m.getLastColumn() + 1);
        }
        rows = Math.max(1, Math.min(rows, ChordSheet.MAX_ROWS));
        cols = Math.max(1, Math.min(cols, ChordSheet.MAX_COLS));

        ChordSheet result = new ChordSheet();
        List<List<String>> data = new ArrayList<>(rows);
        List<Integer> heights = new ArrayList<>(rows);
        float defaultHeightPt = sheet.getDefaultRowHeightInPoints();
        for (int r = 0; r < rows; r++) {
            Row row = sheet.getRow(r);
            List<String> values = new ArrayList<>(cols);
            int maxFontPx = 0;
            for (int c = 0; c < cols; c++) {
                Cell cell = (row == null) ? null : row.getCell(c);
                values.add(cell == null ? "" : cellText(cell, formatter, evaluator));
                if (cell != null) {
                    CellStyleCss css = toCss(wb, cell);
                    result.getStyle().put(ChordSheet.cellName(r, c), css.toCss());
                    maxFontPx = Math.max(maxFontPx, css.fontSize == null ? 0 : css.fontSize);
                }
            }
            data.add(values);
            float pt = (row == null) ? defaultHeightPt : row.getHeightInPoints();
            int px = Math.round(pt * 96 / 72);
            // 高さを個別指定していない行は、Excel と同じく文字の大きさに合わせて広げる
            if (!hasCustomHeight(row)) {
                px = Math.max(px, Math.round(maxFontPx * 1.05f));
            }
            heights.add(px);
        }
        List<Integer> widths = new ArrayList<>(cols);
        for (int c = 0; c < cols; c++) {
            widths.add(Math.max(8, columnWidthPx(sheet, c)));
        }
        for (CellRangeAddress m : sheet.getMergedRegions()) {
            if (m.getFirstRow() < rows && m.getFirstColumn() < cols) {
                int colspan = Math.min(m.getLastColumn(), cols - 1) - m.getFirstColumn() + 1;
                int rowspan = Math.min(m.getLastRow(), rows - 1) - m.getFirstRow() + 1;
                if (colspan > 1 || rowspan > 1) {
                    result.getMergeCells().put(ChordSheet.cellName(m.getFirstRow(), m.getFirstColumn()),
                            new int[]{colspan, rowspan});
                }
            }
        }
        result.setData(data);
        result.setColWidths(widths);
        result.setRowHeights(heights);
        return result.normalize();
    }

    /**
     * 列幅（px）。幅を個別指定していない列は、シートの既定列幅（defaultColWidth）を使う
     * （POI の標準計算はこれを見ないため、Google スプレッドシート由来のテンプレートで列が狭くなる）。
     */
    private static int columnWidthPx(Sheet sheet, int col) {
        if (sheet instanceof org.apache.poi.xssf.usermodel.XSSFSheet xs) {
            var ws = xs.getCTWorksheet();
            boolean explicit = false;
            for (var cols : ws.getColsArray()) {
                for (var def : cols.getColArray()) {
                    if (def.getMin() - 1 <= col && col <= def.getMax() - 1 && def.isSetWidth()) {
                        explicit = true;
                    }
                }
            }
            if (!explicit && ws.isSetSheetFormatPr() && ws.getSheetFormatPr().isSetDefaultColWidth()) {
                return (int) Math.round(ws.getSheetFormatPr().getDefaultColWidth() * 7);
            }
            if (!explicit) {
                // Excel の既定（基本列幅 8 文字 + 余白）＝ 64px
                int base = sheet.getDefaultColumnWidth();
                return (int) Math.ceil((base * 7 + 5) / 8.0) * 8;
            }
        }
        return Math.round(sheet.getColumnWidthInPixels(col));
    }

    private static boolean hasCustomHeight(Row row) {
        if (row instanceof org.apache.poi.xssf.usermodel.XSSFRow xr) {
            return xr.getCTRow().isSetCustomHeight() && xr.getCTRow().getCustomHeight();
        }
        return row != null && row.getHeight() != row.getSheet().getDefaultRowHeight();
    }

    private static String cellText(Cell cell, DataFormatter formatter, FormulaEvaluator evaluator) {
        try {
            return formatter.formatCellValue(cell, evaluator);
        } catch (RuntimeException e) {
            // 外部参照など評価できない数式はキャッシュ値（なければ式そのもの）を使う
            return formatter.formatCellValue(cell);
        }
    }

    private static CellStyleCss toCss(Workbook wb, Cell cell) {
        CellStyle cs = cell.getCellStyle();
        CellStyleCss s = new CellStyleCss();
        Font font = wb.getFontAt(cs.getFontIndex());
        s.bold = font.getBold();
        s.italic = font.getItalic();
        s.underline = font.getUnderline() != Font.U_NONE;
        s.fontSize = Math.round(font.getFontHeightInPoints() * 96f / 72f);
        s.color = fontColor(wb, font);
        if (cs.getFillPattern() == FillPatternType.SOLID_FOREGROUND) {
            s.background = hex(wb, cs.getFillForegroundColorColor());
        }
        s.textAlign = switch (cs.getAlignment()) {
            case CENTER, CENTER_SELECTION -> "center";
            case RIGHT -> "right";
            case GENERAL -> isNumeric(cell) ? "right" : "left";
            default -> "left";
        };
        s.verticalAlign = switch (cs.getVerticalAlignment()) {
            case TOP -> "top";
            case CENTER, JUSTIFY, DISTRIBUTED -> "middle";
            default -> "bottom";
        };
        putBorder(s, CellStyleCss.Side.TOP, cs.getBorderTop(), borderColor(wb, cs, BorderSide.TOP));
        putBorder(s, CellStyleCss.Side.RIGHT, cs.getBorderRight(), borderColor(wb, cs, BorderSide.RIGHT));
        putBorder(s, CellStyleCss.Side.BOTTOM, cs.getBorderBottom(), borderColor(wb, cs, BorderSide.BOTTOM));
        putBorder(s, CellStyleCss.Side.LEFT, cs.getBorderLeft(), borderColor(wb, cs, BorderSide.LEFT));
        return s;
    }

    private static boolean isNumeric(Cell cell) {
        return switch (cell.getCellType()) {
            case NUMERIC -> true;
            case FORMULA -> cell.getCachedFormulaResultType() == org.apache.poi.ss.usermodel.CellType.NUMERIC;
            default -> false;
        };
    }

    private static void putBorder(CellStyleCss s, CellStyleCss.Side side, BorderStyle style, String color) {
        if (style == null || style == BorderStyle.NONE) {
            return;
        }
        CellStyleCss.Border b = switch (style) {
            case MEDIUM -> new CellStyleCss.Border(2, "solid", color);
            case THICK -> new CellStyleCss.Border(3, "solid", color);
            case DOUBLE -> new CellStyleCss.Border(3, "double", color);
            case DOTTED, HAIR -> new CellStyleCss.Border(1, "dotted", color);
            case DASHED, DASH_DOT, DASH_DOT_DOT, SLANTED_DASH_DOT -> new CellStyleCss.Border(1, "dashed", color);
            case MEDIUM_DASHED, MEDIUM_DASH_DOT, MEDIUM_DASH_DOT_DOT -> new CellStyleCss.Border(2, "dashed", color);
            default -> new CellStyleCss.Border(1, "solid", color);
        };
        s.borders.put(side, b);
    }

    private static String fontColor(Workbook wb, Font font) {
        if (font instanceof XSSFFont xf) {
            return hex(wb, xf.getXSSFColor());
        }
        if (font instanceof HSSFFont hf && wb instanceof HSSFWorkbook hwb) {
            return hex(wb, hf.getHSSFColor(hwb));
        }
        return null;
    }

    private static String borderColor(Workbook wb, CellStyle cs, BorderSide side) {
        String c = null;
        if (cs instanceof XSSFCellStyle xs) {
            c = hex(wb, xs.getBorderColor(side));
        } else if (cs instanceof HSSFCellStyle hs && wb instanceof HSSFWorkbook hwb) {
            short idx = switch (side) {
                case TOP -> hs.getTopBorderColor();
                case RIGHT -> hs.getRightBorderColor();
                case BOTTOM -> hs.getBottomBorderColor();
                default -> hs.getLeftBorderColor();
            };
            c = hex(wb, hwb.getCustomPalette().getColor(idx));
        }
        return c == null ? "#000000" : c;
    }

    /** Excel の色（テーマ色・濃淡を含む）を {@code #RRGGBB} にする。自動色・未指定は null。 */
    private static String hex(Workbook wb, Color color) {
        if (color instanceof XSSFColor xc) {
            if (xc.isAuto()) {
                return null;
            }
            byte[] rgb = xc.hasTint() ? xc.getRGBWithTint() : xc.getRGB();
            if (rgb == null) {
                return null;
            }
            return String.format("#%02X%02X%02X", rgb[0] & 0xFF, rgb[1] & 0xFF, rgb[2] & 0xFF);
        }
        if (color instanceof HSSFColor hc) {
            if (hc.getIndex() == HSSFColor.HSSFColorPredefined.AUTOMATIC.getIndex()) {
                return null;
            }
            short[] t = hc.getTriplet();
            return String.format("#%02X%02X%02X", t[0], t[1], t[2]);
        }
        return null;
    }

    // ===================== 書き出し =====================

    /** コード譜を .xlsx にする。印刷は A4 縦・横幅を1ページに収める設定にする。 */
    public static byte[] write(ChordSheet sheetData) {
        ChordSheet data = sheetData.normalize();
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            // Excel の列幅は「既定フォントの数字の幅」が単位になる。テンプレートと同じ Arial 10pt
            // （数字幅 7px）にしておかないと、Excel で開いたときに列幅が画面とずれる
            XSSFFont defaultFont = wb.getFontAt(0);
            defaultFont.setFontName("Arial");
            defaultFont.setFontHeightInPoints((short) 10);
            Sheet sheet = wb.createSheet("コード譜");
            sheet.setDisplayGridlines(false);
            sheet.setPrintGridlines(false);
            sheet.setFitToPage(true);
            PrintSetup ps = sheet.getPrintSetup();
            ps.setPaperSize(PrintSetup.A4_PAPERSIZE);
            ps.setLandscape(false);
            ps.setFitWidth((short) 1);
            ps.setFitHeight((short) 0);

            for (int c = 0; c < data.colCount(); c++) {
                int px = data.colWidth(c);
                // 読み込み（文字数 × 7px）と対になる換算
                sheet.setColumnWidth(c, Math.min(255 * 256, Math.round(px / 7f * 256)));
            }
            Map<String, CellStyle> styleCache = new HashMap<>();
            Map<String, Font> fontCache = new HashMap<>();
            for (int r = 0; r < data.rowCount(); r++) {
                Row row = sheet.createRow(r);
                row.setHeightInPoints(data.rowHeight(r) * 0.75f);
                for (int c = 0; c < data.colCount(); c++) {
                    String value = data.value(r, c);
                    String css = data.styleAt(r, c);
                    if (value.isEmpty() && css == null) {
                        continue;
                    }
                    Cell cell = row.createCell(c);
                    setValue(cell, value);
                    if (css != null) {
                        cell.setCellStyle(styleCache.computeIfAbsent(css, k -> createStyle(wb, k, fontCache)));
                    }
                }
            }
            for (Map.Entry<String, int[]> m : data.getMergeCells().entrySet()) {
                int[] rc = ChordSheet.parseCellName(m.getKey());
                int[] span = m.getValue();
                if (span[0] > 1 || span[1] > 1) {
                    sheet.addMergedRegion(new CellRangeAddress(
                            rc[0], rc[0] + span[1] - 1, rc[1], rc[1] + span[0] - 1));
                }
            }
            wb.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Excel ファイルの作成に失敗しました", e);
        }
    }

    /** 整数・小数だけの値は数値セル、それ以外は文字列セルにする（"000" など先頭ゼロは文字列のまま）。 */
    private static void setValue(Cell cell, String value) {
        if (value.matches("-?(0|[1-9]\\d{0,14})(\\.\\d+)?")) {
            cell.setCellValue(Double.parseDouble(value));
        } else if (!value.isEmpty()) {
            cell.setCellValue(value);
        }
    }

    private static CellStyle createStyle(XSSFWorkbook wb, String css, Map<String, Font> fontCache) {
        CellStyleCss s = CellStyleCss.parse(css);
        XSSFCellStyle cs = wb.createCellStyle();
        String fontKey = s.bold + "|" + s.italic + "|" + s.underline + "|" + s.fontSize + "|" + s.color;
        cs.setFont(fontCache.computeIfAbsent(fontKey, k -> {
            XSSFFont f = wb.createFont();
            f.setFontName(FONT_NAME);
            f.setBold(s.bold);
            f.setItalic(s.italic);
            if (s.underline) {
                f.setUnderline(Font.U_SINGLE);
            }
            // 画面は px の整数で持つため、pt に戻すときは整数に丸める（17pt → 23px → 17pt）
            f.setFontHeight(s.fontSize == null ? 10 : Math.max(1, Math.round(s.fontSize * 0.75)));
            if (s.color != null) {
                f.setColor(xssfColor(s.color));
            }
            return f;
        }));
        if (s.background != null) {
            cs.setFillForegroundColor(xssfColor(s.background));
            cs.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        }
        cs.setAlignment(switch (s.textAlign) {
            case "center" -> HorizontalAlignment.CENTER;
            case "right" -> HorizontalAlignment.RIGHT;
            default -> HorizontalAlignment.LEFT;
        });
        cs.setVerticalAlignment(switch (s.verticalAlign) {
            case "top" -> VerticalAlignment.TOP;
            case "bottom" -> VerticalAlignment.BOTTOM;
            default -> VerticalAlignment.CENTER;
        });
        for (Map.Entry<CellStyleCss.Side, CellStyleCss.Border> e : s.borders.entrySet()) {
            CellStyleCss.Border b = e.getValue();
            BorderStyle bs = switch (b.style()) {
                case "double" -> BorderStyle.DOUBLE;
                case "dotted" -> BorderStyle.DOTTED;
                case "dashed" -> b.width() >= 2 ? BorderStyle.MEDIUM_DASHED : BorderStyle.DASHED;
                default -> b.width() >= 3 ? BorderStyle.THICK : b.width() == 2 ? BorderStyle.MEDIUM : BorderStyle.THIN;
            };
            XSSFColor color = xssfColor(b.color());
            switch (e.getKey()) {
                case TOP -> { cs.setBorderTop(bs); cs.setTopBorderColor(color); }
                case RIGHT -> { cs.setBorderRight(bs); cs.setRightBorderColor(color); }
                case BOTTOM -> { cs.setBorderBottom(bs); cs.setBottomBorderColor(color); }
                case LEFT -> { cs.setBorderLeft(bs); cs.setLeftBorderColor(color); }
            }
        }
        return cs;
    }

    private static XSSFColor xssfColor(String hex) {
        int rgb = Integer.parseInt(hex.substring(1), 16);
        return new XSSFColor(new byte[]{(byte) (rgb >> 16), (byte) (rgb >> 8), (byte) rgb}, null);
    }
}
