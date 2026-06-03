package com.portfolio.musictracker.repository;

import com.portfolio.musictracker.entity.SectionAreaType;
import com.portfolio.musictracker.entity.SectionTemplate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SectionTemplateRepository extends JpaRepository<SectionTemplate, Long> {

    /** 指定エリアのテンプレートを新しい順に取得する。 */
    List<SectionTemplate> findByTypeOrderByCreatedAtDesc(SectionAreaType type);
}
