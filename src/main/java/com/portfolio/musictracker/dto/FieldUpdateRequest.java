package com.portfolio.musictracker.dto;

/**
 * 一覧画面のインライン編集（Ajax）から送られてくる1項目分の更新リクエスト。
 *
 * <p>{@code field} に更新対象のフィールド名（title / memo / status / tagId）、
 * {@code value} にその新しい値（文字列）を入れて送る。</p>
 */
public class FieldUpdateRequest {

    private String field;
    private String value;

    public String getField() {
        return field;
    }

    public void setField(String field) {
        this.field = field;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }
}
