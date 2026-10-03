package com.portfolio.musictracker.service;

import com.portfolio.musictracker.chordchart.ChordChartService;
import com.portfolio.musictracker.dto.SongDetailForm;
import com.portfolio.musictracker.dto.SongDetailForm.SectionDto;
import com.portfolio.musictracker.entity.AbstractSection;
import com.portfolio.musictracker.entity.LyricSection;
import com.portfolio.musictracker.entity.Song;
import com.portfolio.musictracker.entity.Status;
import com.portfolio.musictracker.entity.Tag;
import com.portfolio.musictracker.entity.User;
import com.portfolio.musictracker.repository.SongRepository;
import com.portfolio.musictracker.repository.TagRepository;
import com.portfolio.musictracker.storage.AudioStorage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class SongService {

    private static final Logger log = LoggerFactory.getLogger(SongService.class);

    private final SongRepository songRepository;
    private final TagRepository tagRepository;
    private final AudioStorage audioStorage;
    private final ChordChartService chordChartService;

    public SongService(SongRepository songRepository, TagRepository tagRepository,
                       AudioStorage audioStorage, ChordChartService chordChartService) {
        this.songRepository = songRepository;
        this.tagRepository = tagRepository;
        this.audioStorage = audioStorage;
        this.chordChartService = chordChartService;
    }

    /**
     * ログインユーザーの曲一覧を取得する。tagId が指定された場合はそのタグで絞り込む。
     *
     * @param user  ログインユーザー
     * @param tagId 絞り込み対象のタグID（null の場合は全件）
     */
    public List<Song> findSongs(User user, Long tagId) {
        if (tagId == null) {
            return songRepository.findByUserOrderByListOrderAsc(user);
        }
        return songRepository.findByUserAndTagId(user, tagId);
    }

    /**
     * 指定ユーザーが所有する曲を取得する。所有していない（または存在しない）場合は例外。
     * 他ユーザーの曲へのアクセスをここで一括して防ぐ。
     */
    public Song findOwned(Long id, User user) {
        Song song = songRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("指定された曲が見つかりません: id=" + id));
        if (song.getUser() == null || user == null || !song.getUser().getId().equals(user.getId())) {
            throw new AccessDeniedException("この曲にアクセスする権限がありません");
        }
        return song;
    }

    /** ログインユーザーのタグ（作成順）。 */
    public List<Tag> findAllTags(User user) {
        return tagRepository.findByUserOrderByIdAsc(user);
    }

    /** 直近で詳細画面を開いた曲（なければ空）。ログインユーザーのものに限る。 */
    public Optional<Song> findLastOpened(User user) {
        return songRepository.findTopByUserAndLastOpenedAtIsNotNullOrderByLastOpenedAtDesc(user);
    }

    /**
     * 曲を保存する（新規 or 編集フォームからの更新）。
     * <ul>
     *     <li>新規: ログインユーザーを所有者に設定し、一覧末尾（listOrder = 最大値+1）に追加</li>
     *     <li>更新: 既存曲の所有権を確認し、フォーム項目（曲名・ステータス・メモ・タグ）のみ反映。
     *         歌詞/コード/進捗などは保持する</li>
     * </ul>
     */
    @Transactional
    public Song save(Song form, List<Long> tagIds, User user) {
        Set<Tag> resolvedTags = new HashSet<>();
        if (tagIds != null && !tagIds.isEmpty()) {
            // 自分のタグだけを付けられる（他人のタグIDは無視）
            resolvedTags.addAll(tagRepository.findByIdInAndUser(tagIds, user));
        }
        if (form.getId() == null) {
            form.setUser(user);
            form.setTags(resolvedTags);
            form.setListOrder(songRepository.findMaxListOrderByUser(user) + 1);
            form.setDeadlineDone(form.isCompleted());
            return songRepository.save(form);
        }
        // 更新：所有権チェックのうえ、フォーム項目だけを既存エンティティへ反映
        Song existing = findOwned(form.getId(), user);
        existing.setTitle(form.getTitle());
        existing.changeStatus(form.getStatus());
        existing.setMemo(form.getMemo());
        existing.setTags(resolvedTags);
        return songRepository.save(existing);
    }

    /**
     * 一覧画面のドラッグ&ドロップ並び替えを反映する。
     * 渡された曲ID配列の並び順をそのまま listOrder（0始まり）として保存する。
     */
    @Transactional
    public void reorder(List<Long> orderedIds, User user) {
        if (orderedIds == null) {
            return;
        }
        // 自分が所有する曲だけを対象にする（他ユーザーの曲IDが混ざっても無視）
        Map<Long, Song> byId = songRepository.findByUser(user).stream()
                .collect(Collectors.toMap(Song::getId, s -> s));
        int order = 0;
        for (Long id : orderedIds) {
            Song song = byId.get(id);
            if (song != null) {
                song.setListOrder(order);
                order++;
            }
        }
        songRepository.saveAll(byId.values());
    }

    /**
     * デモ音源をアップロードして曲に紐づける。既存ファイルがあれば置き換える。
     */
    @Transactional
    public Song updateAudio(Long id, MultipartFile file, User user) {
        Song song = findOwned(id, user);
        String stored = audioStorage.store(file, id);
        // DB 更新がロールバックされたら、保存したばかりのファイルを片付ける
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status == STATUS_ROLLED_BACK) {
                    audioStorage.delete(stored);
                }
            }
        });
        String previous = song.getAudioFilePath();
        song.setAudioFilePath(stored);
        Song saved = songRepository.save(song);
        if (previous != null && !previous.equals(stored)) {
            afterCommit(() -> audioStorage.delete(previous));
        }
        return saved;
    }

    /** 曲に紐づくデモ音源の保存ファイル名を返す（所有者のみ）。未登録なら例外。 */
    public String findAudioFileName(Long id, User user) {
        String stored = findOwned(id, user).getAudioFilePath();
        if (stored == null) {
            throw new IllegalArgumentException("音源が登録されていません");
        }
        return stored;
    }

    /** 曲を削除する。デモ音源ファイルも DB のコミット後に削除する。 */
    @Transactional
    public void deleteById(Long id, User user) {
        Song song = findOwned(id, user);
        String audio = song.getAudioFilePath();
        chordChartService.deleteBySong(song);
        songRepository.delete(song);
        if (audio != null) {
            afterCommit(() -> audioStorage.delete(audio));
        }
    }

    /**
     * トランザクションのコミット後に処理を実行する（ロールバック時はファイルを消さない）。
     * DB の変更は確定済みのため、ファイル削除に失敗してもログだけ残して処理は成功扱いにする。
     */
    private void afterCommit(Runnable action) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    action.run();
                } catch (RuntimeException e) {
                    log.warn("音源ファイルの後片付けに失敗しました", e);
                }
            }
        });
    }

    /**
     * 一覧画面のインライン編集から、1項目だけを更新する。
     *
     * @param field 更新対象（title / memo / deadline / deadlineDone / status / tagId）
     * @param value 新しい値（文字列）。deadline は {@code yyyy-MM-dd}（空なら納期なし）、
     *              deadlineDone は {@code true} / {@code false}（ステータス「完了」と連動する）
     * @return 更新後の曲
     */
    @Transactional
    public Song updateField(Long id, String field, String value, User user) {
        Song song = findOwned(id, user);
        if (field == null) {
            throw new IllegalArgumentException("フィールド名が指定されていません");
        }
        switch (field) {
            case "title" -> {
                if (value == null || value.isBlank()) {
                    throw new IllegalArgumentException("曲名は必須です");
                }
                song.setTitle(value.trim());
            }
            case "memo" -> song.setMemo(value);
            case "deadline" -> song.setDeadline(parseDeadline(value));
            // 「完了」チェックはステータス「完了」と連動する
            case "deadlineDone" -> song.markCompleted(Boolean.parseBoolean(value));
            case "status" -> {
                try {
                    song.changeStatus(Status.valueOf(value));
                } catch (IllegalArgumentException | NullPointerException e) {
                    throw new IllegalArgumentException("不正なステータスです: " + value);
                }
            }
            case "tagId" -> {
                Set<Tag> tags = new HashSet<>();
                if (value != null && !value.isBlank()) {
                    Long tagId;
                    try {
                        tagId = Long.valueOf(value.trim());
                    } catch (NumberFormatException e) {
                        throw new IllegalArgumentException("不正なタグIDです: " + value);
                    }
                    // 自分のタグだけを付けられる
                    Tag tag = tagRepository.findByIdAndUser(tagId, user)
                            .orElseThrow(() -> new IllegalArgumentException("タグが見つかりません: " + value));
                    tags.add(tag);
                }
                song.setTags(tags);
            }
            default -> throw new IllegalArgumentException("更新できない項目です: " + field);
        }
        return songRepository.save(song);
    }

    /** カレンダーで選んだ日付（yyyy-MM-dd）を検証する。空なら納期なし（null）。 */
    private static String parseDeadline(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(value.trim()).toString();
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("納期の日付が正しくありません: " + value);
        }
    }

    /**
     * 詳細（作曲コア）画面用に曲を取得する。
     * セクションが未作成なら初期セクションを用意し、最後に開いた日時を記録する。
     */
    @Transactional
    public Song findForDetail(Long id, User user) {
        Song song = findOwned(id, user);
        song.setLastOpenedAt(LocalDateTime.now());
        boolean seeded = false;
        if (song.getLyricSections().isEmpty()) {
            String[] defaults = {"Aメロ", "Bメロ", "サビ"};
            for (int i = 0; i < defaults.length; i++) {
                song.addLyricSection(new LyricSection(song, defaults[i], i));
            }
            seeded = true;
        }
        if (seeded) {
            songRepository.save(song);
        }
        return song;
    }

    /**
     * 作曲コア画面の「変更を保存」を一括で反映する。
     * 進捗、歌詞セクションの追加・削除・並び替え・名前変更・本文編集、コード譜（表）をまとめて保存する。
     * BPM・Key・世界観は画面から外したため変更しない（保存済みの値を残す）。
     */
    @Transactional
    public void saveDetail(Long id, SongDetailForm form, User user) {
        Song song = findOwned(id, user);

        song.setLyricProgress(clampPercent(form.getLyricProgress()));
        song.setArrangementProgress(clampPercent(form.getArrangementProgress()));

        reconcileSections(song, song.getLyricSections(), form.getLyricSections(),
                (s, order) -> new LyricSection(s, "", order));

        songRepository.save(song);
        chordChartService.save(song, form.getChordSheet());
    }

    /**
     * 画面から送られてきたセクション一覧を、永続化されているコレクションへ反映する。
     * <ul>
     *     <li>id があり一覧に残っている → 名前・本文・並び順を更新</li>
     *     <li>id が null → 新規セクションとして追加</li>
     *     <li>一覧から消えた既存セクション → orphanRemoval で削除</li>
     * </ul>
     * 並び順は配列の並び（index）をそのまま {@code sortOrder} とする。
     */
    private <T extends AbstractSection> void reconcileSections(
            Song song, List<T> current, List<SectionDto> incoming,
            BiFunction<Song, Integer, T> factory) {

        List<SectionDto> dtos = (incoming == null) ? List.of() : incoming;

        // 削除：送られてこなかった既存IDをコレクションから外す（orphanRemoval が消す）
        Set<Long> keepIds = dtos.stream()
                .map(SectionDto::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        current.removeIf(s -> s.getId() != null && !keepIds.contains(s.getId()));

        Map<Long, T> existingById = current.stream()
                .filter(s -> s.getId() != null)
                .collect(Collectors.toMap(AbstractSection::getId, s -> s));

        // 追加・更新・並び替え（index を sortOrder に）
        int order = 0;
        for (SectionDto dto : dtos) {
            T section = (dto.getId() != null) ? existingById.get(dto.getId()) : null;
            if (section == null) {
                section = factory.apply(song, order);
                current.add(section);
            }
            section.setName(normalizeName(dto.getName()));
            section.setContent(dto.getContent());
            section.setSortOrder(order);
            order++;
        }
    }

    private String normalizeName(String name) {
        return (name == null || name.isBlank()) ? "無題" : name.trim();
    }

    private int clampPercent(int value) {
        return Math.max(0, Math.min(100, value));
    }
}
