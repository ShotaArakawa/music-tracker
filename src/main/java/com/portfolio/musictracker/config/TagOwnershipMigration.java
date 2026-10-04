package com.portfolio.musictracker.config;

import com.portfolio.musictracker.entity.Song;
import com.portfolio.musictracker.entity.Tag;
import com.portfolio.musictracker.entity.User;
import com.portfolio.musictracker.repository.SongRepository;
import com.portfolio.musictracker.repository.TagRepository;
import com.portfolio.musictracker.repository.UserRepository;
import com.portfolio.musictracker.service.TagService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

/**
 * タグを「全ユーザー共通」から「ユーザーごと」に切り替える、起動時の移行処理（何度実行しても安全）。
 * <ol>
 *     <li>MySQL に残っている旧制約（タグ名だけの一意制約）を外す（ユーザーが違えば同じ名前を使えるようにする）</li>
 *     <li>持ち主のいない共通タグを、全ユーザーそれぞれのタグとして複製し、各自の曲の紐付けを付け替える</li>
 *     <li>共通タグを削除し、タグを1つも持たないユーザーには既定のタグを用意する</li>
 * </ol>
 */
@Component
@Order(2)
public class TagOwnershipMigration implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(TagOwnershipMigration.class);

    private final DataSource dataSource;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final TagRepository tagRepository;
    private final SongRepository songRepository;
    private final UserRepository userRepository;
    private final TagService tagService;

    public TagOwnershipMigration(DataSource dataSource, JdbcTemplate jdbc, TransactionTemplate tx,
                                 TagRepository tagRepository, SongRepository songRepository,
                                 UserRepository userRepository, TagService tagService) {
        this.dataSource = dataSource;
        this.jdbc = jdbc;
        this.tx = tx;
        this.tagRepository = tagRepository;
        this.songRepository = songRepository;
        this.userRepository = userRepository;
        this.tagService = tagService;
    }

    @Override
    public void run(ApplicationArguments args) {
        dropLegacyNameUniqueIndex();
        int converted = tx.execute(status -> convertSharedTags());
        tx.executeWithoutResult(status -> userRepository.findAll().forEach(tagService::ensureDefaultTags));
        if (converted > 0) {
            log.info("[Migration] 共通タグ {} 件をユーザーごとのタグに移行しました", converted);
        }
    }

    /** 旧仕様の「タグ名だけの一意制約」（MySQL）を外す。 */
    private void dropLegacyNameUniqueIndex() {
        if (!isMySql()) {
            return;
        }
        List<String> indexes = jdbc.queryForList(
                "SELECT INDEX_NAME FROM information_schema.STATISTICS "
                        + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'tags' "
                        + "AND NON_UNIQUE = 0 AND INDEX_NAME <> 'PRIMARY' "
                        + "GROUP BY INDEX_NAME HAVING COUNT(*) = 1 AND MAX(COLUMN_NAME) = 'name'",
                String.class);
        for (String index : indexes) {
            jdbc.execute("ALTER TABLE tags DROP INDEX `" + index.replace("`", "") + "`");
            log.info("[Migration] tags の旧一意制約 {} を削除しました", index);
        }
    }

    private boolean isMySql() {
        try (Connection con = dataSource.getConnection()) {
            return con.getMetaData().getDatabaseProductName().toLowerCase().contains("mysql");
        } catch (SQLException e) {
            log.warn("[Migration] データベースの種類を確認できませんでした", e);
            return false;
        }
    }

    /** 共通タグを各ユーザーのタグとして複製し、曲の紐付けを付け替えてから共通タグを消す。 */
    private int convertSharedTags() {
        List<Tag> shared = tagRepository.findByUserIsNull();
        if (shared.isEmpty()) {
            return 0;
        }
        for (User user : userRepository.findAll()) {
            for (Tag tag : shared) {
                Tag own = tagRepository.findByUserAndName(user, tag.getName())
                        .orElseGet(() -> tagRepository.save(new Tag(tag.getName(), user)));
                for (Song song : songRepository.findByUserAndTagId(user, tag.getId())) {
                    song.getTags().removeIf(t -> t.getId().equals(tag.getId()));
                    song.getTags().add(own);
                }
            }
        }
        songRepository.flush();
        for (Tag tag : shared) {
            // 所有者のいない曲などに残った紐付けも外してから削除する
            jdbc.update("DELETE FROM song_tags WHERE tag_id = ?", tag.getId());
            tagRepository.delete(tag);
        }
        return shared.size();
    }
}
