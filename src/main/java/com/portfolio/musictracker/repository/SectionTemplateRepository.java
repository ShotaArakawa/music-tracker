package com.portfolio.musictracker.repository;

import com.portfolio.musictracker.entity.SectionAreaType;
import com.portfolio.musictracker.entity.SectionTemplate;
import com.portfolio.musictracker.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface SectionTemplateRepository extends JpaRepository<SectionTemplate, Long> {

    /**
     * 指定ユーザーが使える指定エリアのテンプレート（自分のもの＋共有テンプレート）を
     * 自分のものを先に、それぞれ新しい順で取得する。
     */
    @Query("SELECT t FROM SectionTemplate t "
            + "WHERE t.type = :type AND (t.user = :user OR t.user IS NULL) "
            + "ORDER BY CASE WHEN t.user IS NULL THEN 1 ELSE 0 END, t.createdAt DESC")
    List<SectionTemplate> findAccessible(@Param("type") SectionAreaType type, @Param("user") User user);
}
