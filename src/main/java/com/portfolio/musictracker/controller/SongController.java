package com.portfolio.musictracker.controller;

import com.portfolio.musictracker.dto.FieldUpdateRequest;
import com.portfolio.musictracker.dto.SongDetailForm;
import com.portfolio.musictracker.entity.Song;
import com.portfolio.musictracker.entity.Status;
import com.portfolio.musictracker.entity.User;
import com.portfolio.musictracker.security.CustomUserDetails;
import com.portfolio.musictracker.service.ScheduleService;
import com.portfolio.musictracker.service.SongService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/songs")
public class SongController {

    private final SongService songService;
    private final ScheduleService scheduleService;

    public SongController(SongService songService, ScheduleService scheduleService) {
        this.songService = songService;
        this.scheduleService = scheduleService;
    }

    /** ダッシュボード（曲一覧）。tagId が指定されればタグで絞り込む。 */
    @GetMapping
    public String list(@RequestParam(name = "tagId", required = false) Long tagId,
                       @AuthenticationPrincipal CustomUserDetails principal, Model model) {
        User user = principal.getUser();
        model.addAttribute("songs", songService.findSongs(user, tagId));
        model.addAttribute("tags", songService.findAllTags());
        model.addAttribute("selectedTagId", tagId);
        model.addAttribute("statuses", Status.values());
        model.addAttribute("lastOpenedSong", songService.findLastOpened(user).orElse(null));
        // 納期が迫っている／超過している曲のアラート（ログインユーザーのものに限る）
        model.addAttribute("alerts", scheduleService.findAlerts(user));
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

    /** 新規登録フォーム。 */
    @GetMapping("/new")
    public String newForm(Model model) {
        model.addAttribute("song", new Song());
        model.addAttribute("selectedTagIds", Collections.emptyList());
        model.addAttribute("allTags", songService.findAllTags());
        model.addAttribute("statuses", Status.values());
        return "songs/form";
    }

    /** 編集フォーム。 */
    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id,
                           @AuthenticationPrincipal CustomUserDetails principal, Model model) {
        Song song = songService.findOwned(id, principal.getUser());
        model.addAttribute("song", song);
        model.addAttribute("selectedTagIds", song.getTags().stream().map(t -> t.getId()).toList());
        model.addAttribute("allTags", songService.findAllTags());
        model.addAttribute("statuses", Status.values());
        return "songs/form";
    }

    /** 新規登録・更新の保存処理。 */
    @PostMapping("/save")
    public String save(@Valid @ModelAttribute("song") Song song,
                       BindingResult bindingResult,
                       @RequestParam(name = "tagIds", required = false) List<Long> tagIds,
                       @AuthenticationPrincipal CustomUserDetails principal,
                       Model model,
                       RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("selectedTagIds", tagIds == null ? Collections.emptyList() : tagIds);
            model.addAttribute("allTags", songService.findAllTags());
            model.addAttribute("statuses", Status.values());
            return "songs/form";
        }
        Song saved = songService.save(song, tagIds, principal.getUser());
        redirectAttributes.addFlashAttribute("message",
                "「" + saved.getTitle() + "」を保存しました。");
        return "redirect:/songs";
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
        return "songs/detail";
    }

    /**
     * 作曲コア画面の一括保存（「変更を保存」ボタン）。
     * 画面の状態を JSON で受け取り、曲情報・進捗・歌詞／コードの各セクションを
     * まとめて保存する。Ajax から呼ばれるため JSON を返す。
     */
    @PostMapping("/{id}/save")
    @ResponseBody
    public Map<String, Object> saveDetail(@PathVariable Long id,
                                          @RequestBody SongDetailForm form,
                                          @AuthenticationPrincipal CustomUserDetails principal) {
        songService.saveDetail(id, form, principal.getUser());
        return Map.of("status", "ok");
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
        }
        return "redirect:/songs/" + id;
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
        response.put("statusName", song.getStatus().name());
        response.put("statusLabel", song.getStatus().getLabel());
        response.put("statusColorClass", song.getStatus().getColorClass());
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
