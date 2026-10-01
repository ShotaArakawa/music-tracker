package com.portfolio.musictracker.chordchart;

import org.apache.poi.ss.util.CellReference;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * コード譜（1シート分の表）のデータ。画面の表計算エディタ（Jspreadsheet）と同じ形で持ち、
 * そのまま JSON で受け渡し・保存する。Excel / PDF とは {@link ChordSheetExcelConverter} などで相互変換する。
 * <ul>
 *     <li>{@code data}: セルの値（行 × 列）</li>
 *     <li>{@code style}: セル名（例: {@code B2}）→ CSS（{@link CellStyleCss} で解釈できる範囲）</li>
 *     <li>{@code mergeCells}: 結合の起点セル名 → {@code [列数, 行数]}</li>
 *     <li>{@code colWidths} / {@code rowHeights}: 列幅・行の高さ（px）</li>
 * </ul>
 */
public class ChordSheet {

    /** 受け付ける表の上限（極端に大きいデータで DB やメモリを圧迫しないため）。 */
    public static final int MAX_ROWS = 1000;
    public static final int MAX_COLS = 100;
    public static final int DEFAULT_COL_WIDTH = 100;
    public static final int DEFAULT_ROW_HEIGHT = 24;

    private List<List<String>> data = new ArrayList<>();
    private Map<String, String> style = new LinkedHashMap<>();
    private Map<String, int[]> mergeCells = new LinkedHashMap<>();
    private List<Integer> colWidths = new ArrayList<>();
    private List<Integer> rowHeights = new ArrayList<>();

    public int rowCount() {
        return data.size();
    }

    public int colCount() {
        return data.stream().mapToInt(List::size).max().orElse(0);
    }

    public String value(int row, int col) {
        if (row >= data.size() || col >= data.get(row).size()) {
            return "";
        }
        String v = data.get(row).get(col);
        return v == null ? "" : v;
    }

    public String styleAt(int row, int col) {
        return style.get(cellName(row, col));
    }

    public int colWidth(int col) {
        return col < colWidths.size() && colWidths.get(col) != null && colWidths.get(col) > 0
                ? colWidths.get(col) : DEFAULT_COL_WIDTH;
    }

    public int rowHeight(int row) {
        return row < rowHeights.size() && rowHeights.get(row) != null && rowHeights.get(row) > 0
                ? rowHeights.get(row) : DEFAULT_ROW_HEIGHT;
    }

    /** 0 始まりの行・列からセル名（例: 0,1 → B1）を作る。 */
    public static String cellName(int row, int col) {
        return CellReference.convertNumToColString(col) + (row + 1);
    }

    /** セル名（例: B1）を {行, 列}（0 始まり）にする。 */
    public static int[] parseCellName(String name) {
        CellReference ref = new CellReference(name);
        return new int[]{ref.getRow(), ref.getCol()};
    }

    /**
     * 受け取ったデータの形を整え、上限を確認する。
     * 行ごとに列数を揃え、列幅・行の高さの不足分を既定値で埋める。
     *
     * @throws IllegalArgumentException 上限を超える・不正なセル名がある場合
     */
    public ChordSheet normalize() {
        if (data == null) {
            data = new ArrayList<>();
        }
        int rows = data.size();
        int cols = colCount();
        if (rows > MAX_ROWS || cols > MAX_COLS) {
            throw new IllegalArgumentException(
                    "コード譜が大きすぎます（最大 " + MAX_ROWS + " 行 × " + MAX_COLS + " 列）");
        }
        List<List<String>> fixed = new ArrayList<>(rows);
        for (List<String> row : data) {
            List<String> r = new ArrayList<>(cols);
            for (int c = 0; c < cols; c++) {
                String v = (row != null && c < row.size()) ? row.get(c) : null;
                r.add(v == null ? "" : v);
            }
            fixed.add(r);
        }
        data = fixed;
        style = (style == null) ? new LinkedHashMap<>() : style;
        mergeCells = (mergeCells == null) ? new LinkedHashMap<>() : mergeCells;
        style.keySet().forEach(ChordSheet::parseCellName);
        for (Map.Entry<String, int[]> m : mergeCells.entrySet()) {
            parseCellName(m.getKey());
            int[] span = m.getValue();
            if (span == null || span.length < 2 || span[0] < 1 || span[1] < 1) {
                throw new IllegalArgumentException("セル結合の指定が不正です: " + m.getKey());
            }
        }
        colWidths = fill(colWidths, cols, DEFAULT_COL_WIDTH);
        rowHeights = fill(rowHeights, rows, DEFAULT_ROW_HEIGHT);
        return this;
    }

    private static List<Integer> fill(List<Integer> src, int size, int def) {
        List<Integer> out = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            Integer v = (src != null && i < src.size()) ? src.get(i) : null;
            out.add(v == null || v <= 0 ? def : Math.min(v, 2000));
        }
        return out;
    }

    public List<List<String>> getData() {
        return data;
    }

    public void setData(List<List<String>> data) {
        this.data = data;
    }

    public Map<String, String> getStyle() {
        return style;
    }

    public void setStyle(Map<String, String> style) {
        this.style = style;
    }

    public Map<String, int[]> getMergeCells() {
        return mergeCells;
    }

    public void setMergeCells(Map<String, int[]> mergeCells) {
        this.mergeCells = mergeCells;
    }

    public List<Integer> getColWidths() {
        return colWidths;
    }

    public void setColWidths(List<Integer> colWidths) {
        this.colWidths = colWidths;
    }

    public List<Integer> getRowHeights() {
        return rowHeights;
    }

    public void setRowHeights(List<Integer> rowHeights) {
        this.rowHeights = rowHeights;
    }
}
