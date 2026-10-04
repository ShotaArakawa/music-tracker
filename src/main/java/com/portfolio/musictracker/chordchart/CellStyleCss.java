package com.portfolio.musictracker.chordchart;

import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * セルの見た目（Excel の書式のうちコード譜で使うもの）と CSS 文字列の相互変換。
 * <p>
 * 扱う CSS: font-weight / font-style / text-decoration / font-size(px) / color / background-color /
 * text-align / vertical-align / border-top・right・bottom・left。色は {@code #RRGGBB} と {@code rgb()} を受け付ける。
 */
public class CellStyleCss {

    public enum Side { TOP, RIGHT, BOTTOM, LEFT }

    /** 罫線 1 辺分。width は px、style は solid / dashed / dotted / double。 */
    public record Border(int width, String style, String color) {
    }

    private static final Pattern RGB = Pattern.compile(
            "rgba?\\(\\s*(\\d+)\\s*,\\s*(\\d+)\\s*,\\s*(\\d+)\\s*(?:,\\s*([\\d.]+)\\s*)?\\)");

    public boolean bold;
    public boolean italic;
    public boolean underline;
    /** 文字サイズ（px）。null は既定。 */
    public Integer fontSize;
    /** 文字色 {@code #RRGGBB}。null は既定（黒）。 */
    public String color;
    /** 背景色 {@code #RRGGBB}。null はなし。 */
    public String background;
    /** left / center / right。 */
    public String textAlign = "left";
    /** top / middle / bottom。 */
    public String verticalAlign = "middle";
    public final Map<Side, Border> borders = new EnumMap<>(Side.class);

    /** CSS 文字列を解釈する。知らないプロパティは無視する。 */
    public static CellStyleCss parse(String css) {
        CellStyleCss s = new CellStyleCss();
        if (css == null) {
            return s;
        }
        for (String decl : css.split(";")) {
            int colon = decl.indexOf(':');
            if (colon < 0) {
                continue;
            }
            String key = decl.substring(0, colon).trim().toLowerCase(Locale.ROOT);
            String value = decl.substring(colon + 1).trim();
            String lower = value.toLowerCase(Locale.ROOT);
            switch (key) {
                case "font-weight" -> s.bold = lower.equals("bold") || lower.equals("bolder")
                        || (lower.matches("\\d+") && Integer.parseInt(lower) >= 600);
                case "font-style" -> s.italic = lower.equals("italic") || lower.equals("oblique");
                case "text-decoration", "text-decoration-line" -> s.underline = lower.contains("underline");
                case "font-size" -> s.fontSize = parseLength(lower);
                case "color" -> s.color = parseColor(lower);
                case "background-color", "background" -> s.background = parseColor(lower);
                case "text-align" -> s.textAlign = switch (lower) {
                    case "center" -> "center";
                    case "right", "end" -> "right";
                    default -> "left";
                };
                case "vertical-align" -> s.verticalAlign = switch (lower) {
                    case "top" -> "top";
                    case "bottom" -> "bottom";
                    default -> "middle";
                };
                case "border-top" -> s.putBorder(Side.TOP, lower);
                case "border-right" -> s.putBorder(Side.RIGHT, lower);
                case "border-bottom" -> s.putBorder(Side.BOTTOM, lower);
                case "border-left" -> s.putBorder(Side.LEFT, lower);
                case "border" -> {
                    for (Side side : Side.values()) {
                        s.putBorder(side, lower);
                    }
                }
                default -> {
                    // 対象外のプロパティは無視
                }
            }
        }
        return s;
    }

    private void putBorder(Side side, String value) {
        Border b = parseBorder(value);
        if (b == null) {
            borders.remove(side);
        } else {
            borders.put(side, b);
        }
    }

    /** {@code 1px solid #000} や {@code 2px dashed rgb(0, 0, 0)} を解釈する。none / 0px は null。 */
    static Border parseBorder(String value) {
        if (value.isBlank() || value.contains("none") || value.contains("hidden")) {
            return null;
        }
        Matcher rgb = RGB.matcher(value);
        String color = null;
        String rest = value;
        if (rgb.find()) {
            color = parseColor(rgb.group());
            rest = value.replace(rgb.group(), " ");
        }
        int width = 1;
        String style = "solid";
        for (String token : rest.trim().split("\\s+")) {
            if (token.isEmpty()) {
                continue;
            }
            Integer len = parseLength(token);
            if (len != null) {
                width = len;
            } else if (token.matches("solid|dashed|dotted|double")) {
                style = token;
            } else if (token.equals("thin")) {
                width = 1;
            } else if (token.equals("medium")) {
                width = 2;
            } else if (token.equals("thick")) {
                width = 3;
            } else if (color == null && parseColor(token) != null) {
                color = parseColor(token);
            }
        }
        if (width <= 0) {
            return null;
        }
        return new Border(width, style, color == null ? "#000000" : color);
    }

    /** 12px / 12.5px / 9pt を px（整数）にする。 */
    static Integer parseLength(String value) {
        Matcher m = Pattern.compile("^([\\d.]+)(px|pt)?$").matcher(value.trim());
        if (!m.find()) {
            return null;
        }
        double n = Double.parseDouble(m.group(1));
        if ("pt".equals(m.group(2))) {
            n = n * 96 / 72;
        }
        return (int) Math.round(n);
    }

    /** #rgb / #rrggbb / rgb() / rgba() / 一部の色名を {@code #RRGGBB} にする。透明は null。 */
    static String parseColor(String value) {
        String v = value.trim().toLowerCase(Locale.ROOT);
        if (v.matches("#[0-9a-f]{6}")) {
            return v.toUpperCase(Locale.ROOT);
        }
        if (v.matches("#[0-9a-f]{3}")) {
            return ("#" + v.charAt(1) + v.charAt(1) + v.charAt(2) + v.charAt(2) + v.charAt(3) + v.charAt(3))
                    .toUpperCase(Locale.ROOT);
        }
        Matcher m = RGB.matcher(v);
        if (m.matches()) {
            if (m.group(4) != null && Double.parseDouble(m.group(4)) == 0) {
                return null;
            }
            return String.format("#%02X%02X%02X",
                    clamp(m.group(1)), clamp(m.group(2)), clamp(m.group(3)));
        }
        return switch (v) {
            case "black" -> "#000000";
            case "white" -> "#FFFFFF";
            case "red" -> "#FF0000";
            case "blue" -> "#0000FF";
            case "green" -> "#008000";
            case "gray", "grey" -> "#808080";
            default -> null;
        };
    }

    private static int clamp(String n) {
        return Math.max(0, Math.min(255, Integer.parseInt(n)));
    }

    /** CSS 文字列にする（Jspreadsheet の style にそのまま渡せる形）。 */
    public String toCss() {
        StringBuilder sb = new StringBuilder();
        sb.append("text-align: ").append(textAlign).append("; ");
        sb.append("vertical-align: ").append(verticalAlign).append("; ");
        if (bold) {
            sb.append("font-weight: bold; ");
        }
        if (italic) {
            sb.append("font-style: italic; ");
        }
        if (underline) {
            sb.append("text-decoration: underline; ");
        }
        if (fontSize != null) {
            sb.append("font-size: ").append(fontSize).append("px; ");
        }
        if (color != null) {
            sb.append("color: ").append(color).append("; ");
        }
        if (background != null) {
            sb.append("background-color: ").append(background).append("; ");
        }
        for (Map.Entry<Side, Border> e : borders.entrySet()) {
            Border b = e.getValue();
            sb.append("border-").append(e.getKey().name().toLowerCase(Locale.ROOT)).append(": ")
                    .append(b.width()).append("px ").append(b.style()).append(' ').append(b.color()).append("; ");
        }
        return sb.toString().trim();
    }
}
