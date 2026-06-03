package com.portfolio.musictracker.repository;

import com.portfolio.musictracker.entity.Song;
import com.portfolio.musictracker.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface SongRepository extends JpaRepository<Song, Long> {

    /** 指定ユーザーの曲を表示順（listOrder 昇順）で取得する。 */
    List<Song> findByUserOrderByListOrderAsc(User user);

    /** 指定ユーザーの全曲（カレンダー・リマインダー集計用）。 */
    List<Song> findByUser(User user);

    /** 指定ユーザーの曲のうち、指定タグIDが紐付いているものを表示順で取得する。 */
    @Query("SELECT DISTINCT s FROM Song s JOIN s.tags t "
            + "WHERE s.user = :user AND t.id = :tagId ORDER BY s.listOrder ASC")
    List<Song> findByUserAndTagId(@Param("user") User user, @Param("tagId") Long tagId);

    /** 指定ユーザーの曲の中での最大 listOrder（1件もなければ 0）。 */
    @Query("SELECT COALESCE(MAX(s.listOrder), 0) FROM Song s WHERE s.user = :user")
    int findMaxListOrderByUser(@Param("user") User user);

    /** 指定ユーザーが最後に詳細画面を開いた曲（lastOpenedAt が最新の1曲）。 */
    Optional<Song> findTopByUserAndLastOpenedAtIsNotNullOrderByLastOpenedAtDesc(User user);

    /** ユーザー未割り当て（移行前）の曲一覧。起動時の初期ユーザーへの移行に使う。 */
    List<Song> findByUserIsNull();
}
