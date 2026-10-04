package com.portfolio.musictracker.dto;

import com.portfolio.musictracker.entity.Song;

import java.util.List;

/**
 * 曲一覧画面の1セクション（「楽曲一覧」または完了した曲の「バックアップ一覧」）。
 *
 * @param title        セクション見出し
 * @param archive      バックアップ一覧（完了した曲）なら true
 * @param songs        表示する曲（表示順）
 * @param emptyMessage 曲がないときに表示する文
 */
public record SongListSection(String title, boolean archive, List<Song> songs, String emptyMessage) {
}
