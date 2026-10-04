package com.portfolio.musictracker.status;

/**
 * 画面のステータス選択肢1件分。
 *
 * @param key        送受信に使う値（既定は {@code ARRANGING} など、追加分は {@code custom:12}）
 * @param label      表示名
 * @param colorClass バッジの色クラス
 * @param id         ユーザーが追加したステータスの ID（既定のステータスは null。削除に使う）
 */
public record StatusOption(String key, String label, String colorClass, Long id) {

    /** ユーザーが追加したステータスか（削除できる）。 */
    public boolean custom() {
        return id != null;
    }
}
