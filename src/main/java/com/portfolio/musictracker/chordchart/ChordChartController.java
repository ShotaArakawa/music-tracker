package com.portfolio.musictracker.chordchart;

import com.portfolio.musictracker.entity.Song;
import com.portfolio.musictracker.security.CustomUserDetails;
import com.portfolio.musictracker.service.SongService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * スタジオ画面のコード譜エディタから呼ばれる API。
 * <p>
 * 作成・インポートは「画面に読み込む表」を返すだけで保存はしない（「変更を保存」でまとめて保存する）。
 * エクスポートは画面に表示中の表を受け取ってファイルにする（未保存の編集も含めて出力できる）。
 */
@RestController
@RequestMapping("/songs/{id}/chord-chart")
public class ChordChartController {

    private static final Logger log = LoggerFactory.getLogger(ChordChartController.class);
    private static final MediaType XLSX =
            MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private final SongService songService;
    private final ChordChartService chordChartService;

    public ChordChartController(SongService songService, ChordChartService chordChartService) {
        this.songService = songService;
        this.chordChartService = chordChartService;
    }

    /** 保存済みのコード譜。まだなければ {@code sheet: null}。 */
    @GetMapping
    public Map<String, Object> get(@PathVariable Long id, @AuthenticationPrincipal CustomUserDetails principal) {
        Song song = songService.findOwned(id, principal.getUser());
        Map<String, Object> body = new HashMap<>();
        body.put("sheet", chordChartService.find(song).orElse(null));
        return body;
    }

    /** 白紙テンプレートから作った表を返す。 */
    @PostMapping("/template")
    public ChordSheet fromTemplate(@PathVariable Long id, @RequestParam("name") String name,
                                   @AuthenticationPrincipal CustomUserDetails principal) {
        Song song = songService.findOwned(id, principal.getUser());
        return chordChartService.fromTemplate(ChordChartTemplate.parse(name), song);
    }

    /** Excel / PDF を読み込んだ表を返す。 */
    @PostMapping("/import")
    public ChordSheet importFile(@PathVariable Long id, @RequestParam("file") MultipartFile file,
                                 @AuthenticationPrincipal CustomUserDetails principal) {
        songService.findOwned(id, principal.getUser());
        return chordChartService.importFile(file);
    }

    /** 表示中の表を Excel（format=xlsx）または PDF（format=pdf）にして返す。 */
    @PostMapping("/export")
    public ResponseEntity<byte[]> export(@PathVariable Long id, @RequestParam("format") String format,
                                         @RequestBody ChordSheet sheet,
                                         @AuthenticationPrincipal CustomUserDetails principal) {
        Song song = songService.findOwned(id, principal.getUser());
        String baseName = "コード譜_" + safeFileName(song.getTitle());
        return switch (format) {
            case "xlsx" -> file(chordChartService.exportXlsx(sheet), XLSX, baseName + ".xlsx");
            case "pdf" -> file(chordChartService.exportPdf(sheet, song.getTitle()), MediaType.APPLICATION_PDF,
                    baseName + ".pdf");
            default -> throw new IllegalArgumentException("出力形式は xlsx か pdf を指定してください");
        };
    }

    private static ResponseEntity<byte[]> file(byte[] bytes, MediaType type, String fileName) {
        return ResponseEntity.ok()
                .contentType(type)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(fileName, StandardCharsets.UTF_8).build().toString())
                .body(bytes);
    }

    /** ファイル名に使えない文字を置き換える。 */
    static String safeFileName(String title) {
        String s = (title == null || title.isBlank()) ? "無題" : title.trim();
        s = s.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "_");
        return s.length() > 80 ? s.substring(0, 80) : s;
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleBadRequest(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, Object>> handleFailure(IllegalStateException e) {
        log.warn("コード譜の処理に失敗しました", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "コード譜の処理に失敗しました"));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, Object>> handleForbidden(AccessDeniedException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", e.getMessage()));
    }
}
