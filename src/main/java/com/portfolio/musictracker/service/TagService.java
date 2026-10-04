package com.portfolio.musictracker.service;

import com.portfolio.musictracker.entity.Song;
import com.portfolio.musictracker.entity.Tag;
import com.portfolio.musictracker.entity.User;
import com.portfolio.musictracker.repository.SongRepository;
import com.portfolio.musictracker.repository.TagRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * ユーザーごとのタグの一覧・追加・削除。
 */
@Service
@Transactional(readOnly = true)
public class TagService {

    /** 新しいユーザーに最初から用意するタグ。 */
    public static final List<String> DEFAULT_TAGS = List.of("ボカロ", "バンド", "コンペ");
    public static final int MAX_NAME_LENGTH = 50;

    private final TagRepository tagRepository;
    private final SongRepository songRepository;

    public TagService(TagRepository tagRepository, SongRepository songRepository) {
        this.tagRepository = tagRepository;
        this.songRepository = songRepository;
    }

    /** ユーザーのタグ（作成順）。 */
    public List<Tag> findAll(User user) {
        return tagRepository.findByUserOrderByIdAsc(user);
    }

    /**
     * タグを追加する。
     *
     * @throws IllegalArgumentException 名前が空・長すぎる・同名のタグがすでにある場合
     */
    @Transactional
    public Tag create(User user, String rawName) {
        String name = rawName == null ? "" : rawName.trim();
        if (name.isEmpty()) {
            throw new IllegalArgumentException("タグ名を入力してください");
        }
        if (name.length() > MAX_NAME_LENGTH) {
            throw new IllegalArgumentException("タグ名は" + MAX_NAME_LENGTH + "文字以内で入力してください");
        }
        if (tagRepository.existsByUserAndName(user, name)) {
            throw new IllegalArgumentException("「" + name + "」はすでにあります");
        }
        return tagRepository.save(new Tag(name, user));
    }

    /**
     * タグを削除する。このタグが付いている自分の曲からも外す。
     *
     * @throws IllegalArgumentException 自分のタグでない（存在しない）場合
     */
    @Transactional
    public void delete(User user, Long tagId) {
        Tag tag = tagRepository.findByIdAndUser(tagId, user)
                .orElseThrow(() -> new IllegalArgumentException("タグが見つかりません: id=" + tagId));
        for (Song song : songRepository.findByUserAndTagId(user, tagId)) {
            song.getTags().removeIf(t -> t.getId().equals(tagId));
        }
        tagRepository.delete(tag);
    }

    /** タグを1つも持っていないユーザーに既定のタグを用意する。 */
    @Transactional
    public void ensureDefaultTags(User user) {
        if (tagRepository.countByUser(user) > 0) {
            return;
        }
        for (String name : DEFAULT_TAGS) {
            tagRepository.save(new Tag(name, user));
        }
    }
}
