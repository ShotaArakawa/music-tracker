package com.portfolio.musictracker.service;

import com.portfolio.musictracker.dto.SongDetailForm.SectionDto;
import com.portfolio.musictracker.dto.TemplateSaveRequest;
import com.portfolio.musictracker.entity.SectionAreaType;
import com.portfolio.musictracker.entity.SectionTemplate;
import com.portfolio.musictracker.entity.SectionTemplateItem;
import com.portfolio.musictracker.repository.SectionTemplateRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * セクション構成テンプレートの保存・取得を担う。
 */
@Service
@Transactional(readOnly = true)
public class SectionTemplateService {

    private final SectionTemplateRepository templateRepository;

    public SectionTemplateService(SectionTemplateRepository templateRepository) {
        this.templateRepository = templateRepository;
    }

    /**
     * 現在の構成をテンプレートとして保存する。
     * 空行（名前も本文もないブロック）は除外する。
     */
    @Transactional
    public SectionTemplate save(TemplateSaveRequest request) {
        String name = (request.getName() == null) ? "" : request.getName().trim();
        if (name.isEmpty()) {
            throw new IllegalArgumentException("テンプレート名を入力してください");
        }
        SectionAreaType type = parseType(request.getType());

        SectionTemplate template = new SectionTemplate(name, type);
        int order = 0;
        for (SectionDto dto : request.getSections()) {
            String itemName = (dto.getName() == null || dto.getName().isBlank())
                    ? "無題" : dto.getName().trim();
            String content = dto.getContent();
            String sectionKey = (type == SectionAreaType.CHORD) ? trimToNull(dto.getSectionKey()) : null;
            template.addItem(new SectionTemplateItem(itemName, order++, content, sectionKey));
        }
        if (template.getItems().isEmpty()) {
            throw new IllegalArgumentException("保存できるブロックがありません");
        }
        return templateRepository.save(template);
    }

    /** 指定エリアのテンプレートを新しい順で取得する。 */
    public List<SectionTemplate> findByType(String type) {
        List<SectionTemplate> templates = templateRepository.findByTypeOrderByCreatedAtDesc(parseType(type));
        // open-in-view=false のため、トランザクション内で items を初期化しておく
        // （Controller での件数参照時の LazyInitializationException を防ぐ）
        templates.forEach(t -> t.getItems().size());
        return templates;
    }

    /** テンプレート名を変更する。 */
    @Transactional
    public SectionTemplate rename(Long id, String newName) {
        String name = (newName == null) ? "" : newName.trim();
        if (name.isEmpty()) {
            throw new IllegalArgumentException("テンプレート名を入力してください");
        }
        SectionTemplate template = findById(id);
        template.setName(name);
        return templateRepository.save(template);
    }

    /**
     * 既存テンプレートを現在の構成で上書きする（ブロック一覧を入れ替える）。
     * 名前・エリア種別は維持する。
     */
    @Transactional
    public SectionTemplate overwrite(Long id, TemplateSaveRequest request) {
        SectionTemplate template = findById(id);
        template.getItems().clear();
        int order = 0;
        for (SectionDto dto : request.getSections()) {
            String itemName = (dto.getName() == null || dto.getName().isBlank())
                    ? "無題" : dto.getName().trim();
            String sectionKey = (template.getType() == SectionAreaType.CHORD)
                    ? trimToNull(dto.getSectionKey()) : null;
            template.addItem(new SectionTemplateItem(itemName, order++, dto.getContent(), sectionKey));
        }
        if (template.getItems().isEmpty()) {
            throw new IllegalArgumentException("保存できるブロックがありません");
        }
        return templateRepository.save(template);
    }

    /** テンプレートを削除する。 */
    @Transactional
    public void delete(Long id) {
        templateRepository.deleteById(id);
    }

    /** ID 指定でテンプレートを取得する（ブロックも含む）。 */
    public SectionTemplate findById(Long id) {
        SectionTemplate template = templateRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("テンプレートが見つかりません: id=" + id));
        // Controller でブロック一覧を参照するためトランザクション内で初期化しておく
        template.getItems().size();
        return template;
    }

    private SectionAreaType parseType(String type) {
        try {
            return SectionAreaType.valueOf(type == null ? "" : type.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("不正なエリア種別です: " + type);
        }
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
