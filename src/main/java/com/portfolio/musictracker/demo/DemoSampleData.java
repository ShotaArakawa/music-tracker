package com.portfolio.musictracker.demo;

import com.portfolio.musictracker.chordchart.ChordChartService;
import com.portfolio.musictracker.chordchart.ChordChartTemplate;
import com.portfolio.musictracker.chordchart.ChordSheet;
import com.portfolio.musictracker.entity.CustomStatus;
import com.portfolio.musictracker.entity.LyricSection;
import com.portfolio.musictracker.entity.Song;
import com.portfolio.musictracker.entity.Status;
import com.portfolio.musictracker.entity.Tag;
import com.portfolio.musictracker.entity.User;
import com.portfolio.musictracker.repository.CustomStatusRepository;
import com.portfolio.musictracker.repository.SongRepository;
import com.portfolio.musictracker.repository.TagRepository;
import com.portfolio.musictracker.status.StatusColor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * お試しアカウントに入れるサンプルデータ（曲・歌詞・コード譜・タグ・追加ステータス）。
 * 一覧の色分け（期限超過）・バックアップ一覧・追加ステータスなど、主な機能がひと目で分かる内容にしている。
 */
@Component
public class DemoSampleData {

    private final SongRepository songRepository;
    private final TagRepository tagRepository;
    private final CustomStatusRepository customStatusRepository;
    private final ChordChartService chordChartService;

    public DemoSampleData(SongRepository songRepository, TagRepository tagRepository,
                          CustomStatusRepository customStatusRepository, ChordChartService chordChartService) {
        this.songRepository = songRepository;
        this.tagRepository = tagRepository;
        this.customStatusRepository = customStatusRepository;
        this.chordChartService = chordChartService;
    }

    /** サンプルデータを入れる。既定のタグ（ボカロ / バンド / コンペ）は作成済みであること。 */
    public void seed(User user) {
        Map<String, Tag> tags = tagRepository.findByUserOrderByIdAsc(user).stream()
                .collect(Collectors.toMap(Tag::getName, Function.identity()));
        CustomStatus mixing = customStatusRepository.save(
                new CustomStatus("ミックス中", StatusColor.PURPLE.getColorClass(), user));
        LocalDate today = LocalDate.now();
        int order = 0;

        Song dawn = song(user, "夜明けのプロローグ", Status.ARRANGING, tags.get("バンド"), today.plusDays(5),
                "Am", 128, 80, 45, order++);
        dawn.addLyricSection(lyric(dawn, "Aメロ", "眠れない夜の隅で\n時計の針だけが進む\n書きかけのメロディが\nまだ名前を探してる", 0));
        dawn.addLyricSection(lyric(dawn, "Bメロ", "遠回りした分だけ\n見える景色もあるから", 1));
        dawn.addLyricSection(lyric(dawn, "サビ", "夜明けよ来い 声にして\nまだ見ぬ朝を描くよ\n消えないように 忘れないように\nこの歌を鳴らすよ", 2));
        songRepository.save(dawn);
        chordChartService.save(dawn, chart(dawn));

        Song neon = song(user, "Neon Drive", Status.MELODY_MAKING, tags.get("ボカロ"), today.minusDays(2),
                "F#m", 150, 60, 20, order++);
        neon.addLyricSection(lyric(neon, "Aメロ", "ネオンの海を抜けて\nアクセル踏み込んだ", 0));
        neon.addLyricSection(lyric(neon, "サビ", "", 1));
        songRepository.save(neon);

        Song wind = song(user, "風の記憶", Status.LYRICS_WRITING, tags.get("コンペ"), today.plusDays(14),
                "D", 88, 30, 0, order++);
        wind.addLyricSection(lyric(wind, "Aメロ", "改札を抜ける風が\n君の香りを連れてきた", 0));
        songRepository.save(wind);

        Song starlight = song(user, "Starlight Anthem", Status.FULL_CHORUS_DONE, tags.get("バンド"),
                today.plusDays(30), "E", 140, 100, 85, order++);
        starlight.changeStatus(mixing);
        songRepository.save(starlight);

        Song sketch = song(user, "未完成スケッチ", Status.LYRICS_WRITING, null, null, "C", 120, 10, 0, order++);
        songRepository.save(sketch);

        Song rain = song(user, "雨上がりの放課後", Status.FULL_CHORUS_DONE, tags.get("コンペ"), today.minusDays(20),
                "G", 96, 100, 100, order);
        rain.markCompleted(true);
        songRepository.save(rain);
    }

    private static Song song(User user, String title, Status status, Tag tag, LocalDate deadline,
                             String key, int bpm, int lyricProgress, int arrangementProgress, int order) {
        Song song = new Song();
        song.setUser(user);
        song.setTitle(title);
        song.changeStatus(status);
        if (tag != null) {
            song.setTags(new HashSet<>(Set.of(tag)));
        }
        song.setDeadline(deadline == null ? null : deadline.toString());
        song.setMusicKey(key);
        song.setBpm(bpm);
        song.setLyricProgress(lyricProgress);
        song.setArrangementProgress(arrangementProgress);
        song.setListOrder(order);
        return song;
    }

    private static LyricSection lyric(Song song, String name, String content, int order) {
        LyricSection section = new LyricSection(song, name, order);
        section.setContent(content);
        return section;
    }

    /**
     * 「1小節4マス」テンプレートにコードを書き込んだコード譜。
     * 各セクション見出しの行から3行おきにコードを書く行があり、B・F・J・N 列が各小節の頭。
     */
    private ChordSheet chart(Song song) {
        ChordSheet sheet = chordChartService.fromTemplate(ChordChartTemplate.FOUR_PER_BAR, song);
        // {行（1始まり）, 4小節分のコード}
        List<Object[]> lines = List.of(
                new Object[]{6, new String[]{"Am", "F", "G", "C"}},
                new Object[]{9, new String[]{"Am", "F", "G", "E"}},
                new Object[]{14, new String[]{"Am", "Em", "F", "C"}},
                new Object[]{17, new String[]{"Dm", "Am", "Bm7-5", "E7"}},
                new Object[]{20, new String[]{"Am", "Em", "F", "C"}},
                new Object[]{23, new String[]{"Dm", "G", "Csus4", "C"}},
                new Object[]{28, new String[]{"F", "G", "Em", "Am"}},
                new Object[]{31, new String[]{"Dm", "Em", "F", "G"}},
                new Object[]{36, new String[]{"F", "G", "Em", "Am"}},
                new Object[]{39, new String[]{"F", "G", "C", "C7"}},
                new Object[]{42, new String[]{"F", "G", "Em", "Am"}},
                new Object[]{45, new String[]{"Dm", "G", "C", "C"}});
        int[] barColumns = {1, 5, 9, 13};
        for (Object[] line : lines) {
            int row = (int) line[0] - 1;
            String[] chords = (String[]) line[1];
            if (row >= sheet.rowCount()) {
                continue;
            }
            List<String> cells = sheet.getData().get(row);
            for (int i = 0; i < chords.length && barColumns[i] < cells.size(); i++) {
                cells.set(barColumns[i], chords[i]);
            }
        }
        return sheet;
    }
}
