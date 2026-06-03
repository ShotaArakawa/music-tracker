package com.portfolio.musictracker.controller;

import com.portfolio.musictracker.dto.TemplateSaveRequest;
import com.portfolio.musictracker.entity.SectionTemplate;
import com.portfolio.musictracker.entity.SectionTemplateItem;
import com.portfolio.musictracker.service.SectionTemplateService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * セクション構成テンプレートの保存・一覧・取得を提供する非同期 API。
 * 作曲コア画面の「構成をテンプレ保存」「テンプレ適用」から呼ばれる。
 */
@RestController
@RequestMapping("/templates")
public class TemplateController {

    private final SectionTemplateService templateService;

    public TemplateController(SectionTemplateService templateService) {
        this.templateService = templateService;
    }

    /** 現在の構成をテンプレートとして保存する。 */
    @PostMapping
    public ResponseEntity<Map<String, Object>> save(@RequestBody TemplateSaveRequest request) {
        try {
            SectionTemplate saved = templateService.save(request);
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("id", saved.getId());
            body.put("name", saved.getName());
            return ResponseEntity.ok(body);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /** 指定エリア（LYRIC / CHORD）のテンプレート一覧を返す。 */
    @GetMapping
    public List<Map<String, Object>> list(@RequestParam("type") String type) {
        return templateService.findByType(type).stream()
                .map(t -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", t.getId());
                    m.put("name", t.getName());
                    m.put("count", t.getItems().size());
                    return m;
                })
                .toList();
    }

    /** テンプレートの中身（ブロック一覧）を返す。フロントで適用するために使う。 */
    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> detail(@PathVariable Long id) {
        try {
            SectionTemplate t = templateService.findById(id);
            List<Map<String, Object>> sections = t.getItems().stream()
                    .map(this::toItemMap)
                    .toList();
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("id", t.getId());
            body.put("name", t.getName());
            body.put("type", t.getType().name());
            body.put("sections", sections);
            return ResponseEntity.ok(body);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /** テンプレート名を変更する。 */
    @PostMapping("/{id}/rename")
    public ResponseEntity<Map<String, Object>> rename(@PathVariable Long id,
                                                      @RequestBody Map<String, String> body) {
        try {
            SectionTemplate t = templateService.rename(id, body.get("name"));
            return ResponseEntity.ok(Map.of("id", t.getId(), "name", t.getName()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /** 既存テンプレートを現在の構成で上書きする。 */
    @PostMapping("/{id}/overwrite")
    public ResponseEntity<Map<String, Object>> overwrite(@PathVariable Long id,
                                                         @RequestBody TemplateSaveRequest request) {
        try {
            SectionTemplate t = templateService.overwrite(id, request);
            return ResponseEntity.ok(Map.of("id", t.getId(), "name", t.getName(),
                    "count", t.getItems().size()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /** テンプレートを削除する。 */
    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> delete(@PathVariable Long id) {
        templateService.delete(id);
        return ResponseEntity.ok(Map.of("status", "ok"));
    }

    private Map<String, Object> toItemMap(SectionTemplateItem item) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", item.getName());
        m.put("content", item.getContent() == null ? "" : item.getContent());
        m.put("sectionKey", item.getSectionKey() == null ? "" : item.getSectionKey());
        return m;
    }
}
