package com.portfolio.musictracker.service;

import com.portfolio.musictracker.dto.SongDetailForm.SectionDto;
import com.portfolio.musictracker.dto.TemplateSaveRequest;
import com.portfolio.musictracker.entity.SectionAreaType;
import com.portfolio.musictracker.entity.SectionTemplate;
import com.portfolio.musictracker.entity.SectionTemplateItem;
import com.portfolio.musictracker.entity.User;
import com.portfolio.musictracker.repository.SectionTemplateRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * セクション構成テンプレートの保存・取得を担う。
 * <p>
 * テンプレートは作成したユーザーのもの。持ち主のいない共有テンプレートは
 * 全員が閲覧・適用できるが、名前変更・上書き・削除はできない。
 */
@Service
@Transactional(readOnly = true)
public class SectionTemplateService {

    private final SectionTemplateRepository templateRepository;

    public SectionTemplateService(SectionTemplateRepository templateRepository) {
        this.templateRepository = templateRepository;
    }

    /**
     * 現在の構成をログインユーザーのテンプレートとして保存する。
     */
    @Transactional
    public SectionTemplate save(TemplateSaveRequest request, User user) {
        String name = (request.getName() == null) ? "" : request.getName().trim();
        if (name.isEmpty()) {
            throw new IllegalArgumentException("テンプレート名を入力してください");
        }
        SectionAreaType type = parseType(request.getType());

        SectionTemplate template = new SectionTemplate(name, type, user);
        addItems(template, request.getSections());
        return templateRepository.save(template);
    }

    /** 指定ユーザーが使える指定エリアのテンプレート（自分のもの → 共有）を取得する。 */
    public List<SectionTemplate> findAccessible(String type, User user) {
        List<SectionTemplate> templates = templateRepository.findAccessible(parseType(type), user);
        // open-in-view=false のため、トランザクション内で items を初期化しておく
        // （Controller での件数参照時の LazyInitializationException を防ぐ）
        templates.forEach(t -> t.getItems().size());
        return templates;
    }

    /** テンプレート名を変更する（自分のテンプレートのみ）。 */
    @Transactional
    public SectionTemplate rename(Long id, String newName, User user) {
        String name = (newName == null) ? "" : newName.trim();
        if (name.isEmpty()) {
            throw new IllegalArgumentException("テンプレート名を入力してください");
        }
        SectionTemplate template = findOwned(id, user);
        template.setName(name);
        return templateRepository.save(template);
    }

    /**
     * 既存テンプレートを現在の構成で上書きする（ブロック一覧を入れ替える）。
     * 名前・エリア種別は維持する。自分のテンプレートのみ。
     */
    @Transactional
    public SectionTemplate overwrite(Long id, TemplateSaveRequest request, User user) {
        SectionTemplate template = findOwned(id, user);
        template.getItems().clear();
        addItems(template, request.getSections());
        return templateRepository.save(template);
    }

    /** テンプレートを削除する（自分のテンプレートのみ）。 */
    @Transactional
    public void delete(Long id, User user) {
        templateRepository.delete(findOwned(id, user));
    }

    /** 閲覧可能（自分のもの or 共有）なテンプレートをブロック込みで取得する。 */
    public SectionTemplate findAccessible(Long id, User user) {
        SectionTemplate template = findById(id);
        if (template.getUser() != null && !template.isOwnedBy(user)) {
            throw new AccessDeniedException("このテンプレートにアクセスする権限がありません");
        }
        return template;
    }

    /** 自分が作成したテンプレートを取得する。共有・他人のテンプレートは変更不可として例外。 */
    private SectionTemplate findOwned(Long id, User user) {
        SectionTemplate template = findById(id);
        if (!template.isOwnedBy(user)) {
            throw new AccessDeniedException("このテンプレートを変更する権限がありません");
        }
        return template;
    }

    private SectionTemplate findById(Long id) {
        SectionTemplate template = templateRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("テンプレートが見つかりません: id=" + id));
        // Controller でブロック一覧を参照するためトランザクション内で初期化しておく
        template.getItems().size();
        return template;
    }

    /** 画面のセクション一覧をテンプレートのブロックとして追加する。 */
    private void addItems(SectionTemplate template, List<SectionDto> sections) {
        int order = 0;
        for (SectionDto dto : sections) {
            String itemName = (dto.getName() == null || dto.getName().isBlank())
                    ? "無題" : dto.getName().trim();
            template.addItem(new SectionTemplateItem(itemName, order++, dto.getContent()));
        }
        if (template.getItems().isEmpty()) {
            throw new IllegalArgumentException("保存できるブロックがありません");
        }
    }

    /** 対象エリアを解釈する。コード進行のテンプレート（CHORD）は廃止したため受け付けない。 */
    private SectionAreaType parseType(String type) {
        SectionAreaType parsed;
        try {
            parsed = SectionAreaType.valueOf(type == null ? "" : type.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("不正なエリア種別です: " + type);
        }
        if (parsed != SectionAreaType.LYRIC) {
            throw new IllegalArgumentException("コード進行のテンプレートは廃止しました");
        }
        return parsed;
    }
}
