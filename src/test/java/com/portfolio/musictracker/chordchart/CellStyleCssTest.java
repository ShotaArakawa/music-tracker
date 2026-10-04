package com.portfolio.musictracker.chordchart;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CellStyleCssTest {

    @Test
    void ブラウザが正規化したCSSを解釈できる() {
        // Jspreadsheet の getStyle() は色を rgb() で返す
        CellStyleCss s = CellStyleCss.parse("text-align: center; font-weight: bold; font-size: 23px; "
                + "color: rgb(66, 133, 244); background-color: rgb(255, 251, 237); "
                + "border-bottom: 1px solid rgb(0, 0, 0); border-left: 2px dashed #f00;");

        assertThat(s.bold).isTrue();
        assertThat(s.textAlign).isEqualTo("center");
        assertThat(s.fontSize).isEqualTo(23);
        assertThat(s.color).isEqualTo("#4285F4");
        assertThat(s.background).isEqualTo("#FFFBED");
        assertThat(s.borders.get(CellStyleCss.Side.BOTTOM)).isEqualTo(new CellStyleCss.Border(1, "solid", "#000000"));
        assertThat(s.borders.get(CellStyleCss.Side.LEFT)).isEqualTo(new CellStyleCss.Border(2, "dashed", "#FF0000"));
        assertThat(s.borders).doesNotContainKey(CellStyleCss.Side.TOP);
    }

    @Test
    void 透明な背景や罫線なしは無視する() {
        CellStyleCss s = CellStyleCss.parse("background-color: rgba(0, 0, 0, 0); border-top: none; font-size: 12pt");
        assertThat(s.background).isNull();
        assertThat(s.borders).isEmpty();
        assertThat(s.fontSize).isEqualTo(16);
    }

    @Test
    void CSSに戻して再解釈しても同じ() {
        CellStyleCss s = CellStyleCss.parse("font-style: italic; text-decoration: underline; vertical-align: top; "
                + "border-right: 3px double #123456; color: #abc");
        CellStyleCss again = CellStyleCss.parse(s.toCss());
        assertThat(again.toCss()).isEqualTo(s.toCss());
        assertThat(again.color).isEqualTo("#AABBCC");
        assertThat(again.italic).isTrue();
        assertThat(again.underline).isTrue();
    }
}
