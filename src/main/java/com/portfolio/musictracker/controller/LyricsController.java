package com.portfolio.musictracker.controller;

import com.portfolio.musictracker.dto.LyricsExportRequest;
import com.portfolio.musictracker.entity.Song;
import com.portfolio.musictracker.security.CustomUserDetails;
import com.portfolio.musictracker.service.LyricsExportService;
import com.portfolio.musictracker.service.SongService;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * スタジオ画面の歌詞を Word / テキスト / PDF で書き出す API。
 * 画面に表示中の歌詞（未保存の編集も含む）を受け取ってファイルにする。
 */
@RestController
@RequestMapping("/songs/{id}/lyrics")
public class LyricsController {

    private static final MediaType DOCX = MediaType.parseMediaType(
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document");
    private static final MediaType TEXT = MediaType.parseMediaType("text/plain;charset=UTF-8");

    private final SongService songService;
    private final LyricsExportService exportService;

    public LyricsController(SongService songService, LyricsExportService exportService) {
        this.songService = songService;
        this.exportService = exportService;
    }

    /** format=docx（Word）/ txt（テキスト）/ pdf */
    @PostMapping("/export")
    public ResponseEntity<byte[]> export(@PathVariable Long id, @RequestParam("format") String format,
                                         @RequestBody LyricsExportRequest request,
                                         @AuthenticationPrincipal CustomUserDetails principal) {
        Song song = songService.findOwned(id, principal.getUser());
        String title = song.getTitle();
        String baseName = "歌詞_" + safeFileName(title);
        return switch (format) {
            case "docx" -> file(exportService.toWord(title, request.getSections()), DOCX, baseName + ".docx");
            case "txt" -> file(exportService.toText(title, request.getSections()), TEXT, baseName + ".txt");
            case "pdf" -> file(exportService.toPdf(title, request.getSections()),
                    MediaType.APPLICATION_PDF, baseName + ".pdf");
            default -> throw new IllegalArgumentException("出力形式は docx / txt / pdf のいずれかを指定してください");
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
    private static String safeFileName(String title) {
        String s = (title == null || title.isBlank()) ? "無題" : title.trim();
        s = s.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "_");
        return s.length() > 80 ? s.substring(0, 80) : s;
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleBadRequest(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
    }
}
