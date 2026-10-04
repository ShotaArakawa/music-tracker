package com.portfolio.musictracker.controller;

import com.portfolio.musictracker.chordchart.ChordChartTemplate;
import com.portfolio.musictracker.dto.FieldUpdateRequest;
import com.portfolio.musictracker.dto.SongDeadline;
import com.portfolio.musictracker.dto.SongDetailForm;
import com.portfolio.musictracker.dto.SongListSection;
import com.portfolio.musictracker.entity.Song;
import com.portfolio.musictracker.entity.User;
import com.portfolio.musictracker.security.CustomUserDetails;
import com.portfolio.musictracker.service.ScheduleService;
import com.portfolio.musictracker.service.SongService;
import com.portfolio.musictracker.status.StatusColor;
import com.portfolio.musictracker.status.StatusService;
import com.portfolio.musictracker.storage.AudioStorage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Controller
@RequestMapping("/songs")
public class SongController {

    private static final Logger log = LoggerFactory.getLogger(SongController.class);

    private final SongService songService;
    private final ScheduleService scheduleService;
    private final AudioStorage audioStorage;
    private final StatusService statusService;

    public SongController(SongService songService, ScheduleService scheduleService,
                          AudioStorage audioStorage, StatusService statusService) {
        this.songService = songService;
        this.scheduleService = scheduleService;
        this.audioStorage = audioStorage;
        this.statusService = statusService;
    }

    /** ダッシュボード（曲一覧）。tagId が指定されればタグで絞り込む。 */
    @GetMapping
    public String list(@RequestParam(name = "tagId", required = false) Long tagId,
                       @AuthenticationPrincipal CustomUserDetails principal, Model model) {
        User user = principal.getUser();
        List<Song> songs = songService.findSongs(user, tagId);
        // 完了した曲は下の「バックアップ一覧」に分ける
        model.addAttribute("sections", List.of(
                new SongListSection("楽曲一覧", false,
                        songs.stream().filter(s -> !s.isCompleted()).toList(),
                        "まだ曲がありません。上の行に曲名を入力して追加してみましょう。"),
                new SongListSection("バックアップ一覧", true,
                        songs.stream().filter(Song::isCompleted).toList(),
                        "完了した曲はまだありません。「完了」にチェックすると、ここに移動します。")));
        model.addAttribute("tags", songService.findAllTags(user));
        model.addAttribute("selectedTagId", tagId);
        model.addAttribute("statuses", statusService.options(user));
        model.addAttribute("statusColors", StatusColor.values());
        model.addAttribute("lastOpenedSong", songService.findLastOpened(user).orElse(null));
        // 曲ID → 納期（日付・超過日数・対応済み）。期限超過の行の色分けに使う
        model.addAttribute("deadlines", scheduleService.deadlinesBySongId(songs));
        return "songs/list";
    }

    /** 一覧画面のドラッグ&ドロップ並び替えを非同期で保存する。 */
    @PostMapping("/reorder")
    @ResponseBody
    public Map<String, Object> reorder(@RequestBody List<Long> orderedIds,
                                       @AuthenticationPrincipal CustomUserDetails principal) {
        songService.reorder(orderedIds, principal.getUser());
        return Map.of("status", "ok");
    }

    /**
     * 曲一覧の「新規追加」行から曲を登録する（Ajax）。
     * リクエスト: {@code {"title": "曲名", "status": "ARRANGING", "tagId": "3", "deadline": "2026-10-31"}}
     * （曲名以外は省略可）
     */
    @PostMapping
    @ResponseBody
    public ResponseEntity<Map<String, Object>> create(@RequestBody Map<String, String> body,
                                                      @AuthenticationPrincipal CustomUserDetails principal) {
        try {
            Song song = songService.create(principal.getUser(), body.get("title"), body.get("status"),
                    body.get("tagId"), body.get("deadline"));
            Map<String, Object> response = buildFieldResponse(song);
            response.put("id", song.getId());
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /** 削除処理。 */
    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id,
                         @AuthenticationPrincipal CustomUserDetails principal,
                         RedirectAttributes redirectAttributes) {
        songService.deleteById(id, principal.getUser());
        redirectAttributes.addFlashAttribute("message", "曲を削除しました。");
        return "redirect:/songs";
    }

    // ===== 作曲コア（詳細）画面 =====

    /** 詳細（作曲コア）画面。歌詞・コード・曲情報を1画面で編集する。 */
    @GetMapping("/{id}")
    public String detail(@PathVariable Long id,
                         @AuthenticationPrincipal CustomUserDetails principal, Model model) {
        model.addAttribute("song", songService.findForDetail(id, principal.getUser()));
        model.addAttribute("chordTemplates", ChordChartTemplate.values());
        return "songs/detail";
    }

    /**
     * 作曲コア画面の一括保存（「変更を保存」ボタン）。
     * 画面の状態を JSON で受け取り、曲情報・進捗・歌詞／コードの各セクションを
     * まとめて保存する。Ajax から呼ばれるため JSON を返す。
     */
    @PostMapping("/{id}/save")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> saveDetail(@PathVariable Long id,
                                                          @RequestBody SongDetailForm form,
                                                          @AuthenticationPrincipal CustomUserDetails principal) {
        try {
            songService.saveDetail(id, form, principal.getUser());
            return ResponseEntity.ok(Map.of("status", "ok"));
        } catch (IllegalArgumentException e) {
            // コード譜が大きすぎる等の入力不備
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /** デモ音源のアップロード。保存後に詳細画面へ戻る。 */
    @PostMapping("/{id}/audio")
    public String uploadAudio(@PathVariable Long id,
                              @RequestParam("audioFile") MultipartFile audioFile,
                              @AuthenticationPrincipal CustomUserDetails principal,
                              RedirectAttributes redirectAttributes) {
        try {
            songService.updateAudio(id, audioFile, principal.getUser());
            redirectAttributes.addFlashAttribute("message", "デモ音源をアップロードしました。");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        } catch (IllegalStateException e) {
            // 保存先（ディスク / R2）の障害。原因はログに残し、画面には概要だけ出す
            log.warn("デモ音源の保存に失敗しました: songId={}", id, e);
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/songs/" + id;
    }

    /**
     * デモ音源の再生用配信。所有者だけが取得できる。
     * <ul>
     *     <li>R2 などのストレージ: 短時間有効な署名付き URL へリダイレクト</li>
     *     <li>ローカルディスク: アプリから配信（Resource を返すと Range リクエストにも対応する）</li>
     * </ul>
     */
    @GetMapping("/{id}/audio")
    public ResponseEntity<Resource> audio(@PathVariable Long id,
                                          @AuthenticationPrincipal CustomUserDetails principal) {
        try {
            String stored = songService.findAudioFileName(id, principal.getUser());
            Optional<URI> url = audioStorage.playbackUrl(stored);
            if (url.isPresent()) {
                return ResponseEntity.status(HttpStatus.FOUND)
                        .location(url.get())
                        .cacheControl(CacheControl.noStore())
                        .build();
            }
            Resource resource = audioStorage.load(stored);
            MediaType type = MediaTypeFactory.getMediaType(resource)
                    .orElse(MediaType.APPLICATION_OCTET_STREAM);
            return ResponseEntity.ok()
                    .contentType(type)
                    .cacheControl(CacheControl.noCache().cachePrivate())
                    .body(resource);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    // ===== 一覧画面のインライン編集（Ajax） =====

    /**
     * 一覧画面からの1項目だけの非同期更新を受け付ける。
     * 更新後、画面の再描画に必要な値（ステータスのラベル・色、タグ一覧など）を JSON で返す。
     */
    @PostMapping("/{id}/field")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> updateField(@PathVariable Long id,
                                                           @RequestBody FieldUpdateRequest request,
                                                           @AuthenticationPrincipal CustomUserDetails principal) {
        try {
            Song song = songService.updateField(id, request.getField(), request.getValue(), principal.getUser());
            return ResponseEntity.ok(buildFieldResponse(song));
        } catch (IllegalArgumentException e) {
            String message = (e.getMessage() == null) ? "更新に失敗しました" : e.getMessage();
            return ResponseEntity.badRequest().body(Map.of("error", message));
        }
    }

    /** インライン編集後にセルを再描画するための情報をまとめる。 */
    private Map<String, Object> buildFieldResponse(Song song) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", "ok");
        response.put("title", song.getTitle());
        response.put("memo", song.getMemo());
        response.put("deadline", song.getDeadline());
        response.put("deadlineDone", song.isDeadlineDone());
        // 納期を日付として解釈できた場合の日付・残り日数・超過（行の色分けに使う）
        SongDeadline d = scheduleService.deadlinesBySongId(List.of(song)).get(song.getId());
        response.put("deadlineDate", d == null ? null : d.date().toString());
        response.put("daysUntil", d == null ? null : d.daysUntil());
        response.put("overdue", d != null && d.isOverdue());
        response.put("statusName", song.getStatusKey());
        response.put("statusLabel", song.getStatusLabel());
        response.put("statusColorClass", song.getStatusColorClass());
        List<Map<String, Object>> tags = song.getTags().stream()
                .map(t -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", t.getId());
                    m.put("name", t.getName());
                    return m;
                })
                .toList();
        response.put("tags", tags);
        return response;
    }
}
