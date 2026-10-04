package com.portfolio.musictracker.status;

import com.portfolio.musictracker.entity.CustomStatus;
import com.portfolio.musictracker.entity.Song;
import com.portfolio.musictracker.entity.Status;
import com.portfolio.musictracker.entity.User;
import com.portfolio.musictracker.repository.CustomStatusRepository;
import com.portfolio.musictracker.repository.SongRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * ステータスの選択肢（既定 + ユーザーが追加したもの）と、追加・削除。
 */
@Service
@Transactional(readOnly = true)
public class StatusService {

    public static final int MAX_NAME_LENGTH = 30;
    public static final int MAX_PER_USER = 30;

    private final CustomStatusRepository customStatusRepository;
    private final SongRepository songRepository;

    public StatusService(CustomStatusRepository customStatusRepository, SongRepository songRepository) {
        this.customStatusRepository = customStatusRepository;
        this.songRepository = songRepository;
    }

    /**
     * 画面に出すステータスの選択肢。既定のステータス → 追加したステータス → 「完了」の順。
     */
    public List<StatusOption> options(User user) {
        List<StatusOption> options = new ArrayList<>();
        for (Status s : Status.values()) {
            if (s != Status.RELEASED) {
                options.add(new StatusOption(s.name(), s.getLabel(), s.getColorClass(), null));
            }
        }
        for (CustomStatus c : customStatusRepository.findByUserOrderByIdAsc(user)) {
            options.add(new StatusOption(c.getKey(), c.getName(), c.getColorClass(), c.getId()));
        }
        options.add(new StatusOption(Status.RELEASED.name(), Status.RELEASED.getLabel(),
                Status.RELEASED.getColorClass(), null));
        return options;
    }

    /**
     * ステータスを追加する。
     *
     * @param color {@link StatusColor} の名前（GRAY / PURPLE など）
     * @throws IllegalArgumentException 名前が空・長すぎる・既存と重複・色が不正・上限超過の場合
     */
    @Transactional
    public CustomStatus create(User user, String rawName, String color) {
        String name = rawName == null ? "" : rawName.trim();
        if (name.isEmpty()) {
            throw new IllegalArgumentException("ステータス名を入力してください");
        }
        if (name.length() > MAX_NAME_LENGTH) {
            throw new IllegalArgumentException("ステータス名は" + MAX_NAME_LENGTH + "文字以内で入力してください");
        }
        boolean builtIn = Arrays.stream(Status.values()).anyMatch(s -> s.getLabel().equals(name));
        if (builtIn || customStatusRepository.existsByUserAndName(user, name)) {
            throw new IllegalArgumentException("「" + name + "」はすでにあります");
        }
        if (customStatusRepository.countByUser(user) >= MAX_PER_USER) {
            throw new IllegalArgumentException("追加できるステータスは" + MAX_PER_USER + "個までです");
        }
        StatusColor c = StatusColor.of(color)
                .orElseThrow(() -> new IllegalArgumentException("色の指定が正しくありません"));
        return customStatusRepository.save(new CustomStatus(name, c.getColorClass(), user));
    }

    /**
     * 追加したステータスを削除する。このステータスだった曲は、既定のステータスに戻る。
     *
     * @throws IllegalArgumentException 自分のステータスでない（存在しない）場合
     */
    @Transactional
    public void delete(User user, Long id) {
        CustomStatus status = findOwned(user, id);
        for (Song song : songRepository.findByCustomStatus(status)) {
            song.setCustomStatus(null);
        }
        customStatusRepository.delete(status);
    }

    /** 自分が追加したステータスを取得する。なければ例外。 */
    public CustomStatus findOwned(User user, Long id) {
        return customStatusRepository.findByIdAndUser(id, user)
                .orElseThrow(() -> new IllegalArgumentException("ステータスが見つかりません: id=" + id));
    }
}
