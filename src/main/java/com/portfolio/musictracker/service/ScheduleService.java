package com.portfolio.musictracker.service;

import com.portfolio.musictracker.dto.SongDeadline;
import com.portfolio.musictracker.entity.Song;
import com.portfolio.musictracker.entity.User;
import com.portfolio.musictracker.repository.SongRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * 納期（deadline）にもとづくスケジュール関連の集計を担う。
 * カレンダー表示用のイベント抽出と、リマインダー（期限が近い／超過）の判定を提供する。
 */
@Service
@Transactional(readOnly = true)
public class ScheduleService {

    /** 「期限が近い」とみなす日数（今日から何日以内か）。 */
    public static final int ALERT_WITHIN_DAYS = 3;

    private final SongRepository songRepository;

    public ScheduleService(SongRepository songRepository) {
        this.songRepository = songRepository;
    }

    /** 指定ユーザーの、納期が解釈できる全曲を納期日の昇順で返す（カレンダー用）。 */
    public List<SongDeadline> findAllWithDeadline(User user) {
        return toSortedDeadlines(songRepository.findByUser(user));
    }

    /**
     * 指定ユーザーのリマインダー対象（納期超過、または今日から
     * {@value #ALERT_WITHIN_DAYS} 日以内）を納期日の昇順で返す。
     */
    public List<SongDeadline> findAlerts(User user) {
        return filterAlerts(findAllWithDeadline(user));
    }

    /** 全ユーザー横断のリマインダー対象（サーバーの定期バッチ・ログ用）。 */
    public List<SongDeadline> findAlertsGlobal() {
        return filterAlerts(toSortedDeadlines(songRepository.findAll()));
    }

    private List<SongDeadline> filterAlerts(List<SongDeadline> deadlines) {
        return deadlines.stream()
                .filter(d -> d.daysUntil() <= ALERT_WITHIN_DAYS)
                .toList();
    }

    private List<SongDeadline> toSortedDeadlines(List<Song> songs) {
        LocalDate today = LocalDate.now();
        return songs.stream()
                .map(song -> toSongDeadline(song, today))
                .flatMap(Optional::stream)
                .sorted(Comparator.comparing(SongDeadline::date))
                .toList();
    }

    private Optional<SongDeadline> toSongDeadline(Song song, LocalDate today) {
        return DeadlineParser.parse(song.getDeadline(), today)
                .map(date -> new SongDeadline(
                        song.getId(),
                        song.getTitle(),
                        song.getDeadline(),
                        date,
                        ChronoUnit.DAYS.between(today, date)));
    }
}
