package com.portfolio.musictracker.repository;

import com.portfolio.musictracker.entity.Tag;
import com.portfolio.musictracker.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface TagRepository extends JpaRepository<Tag, Long> {

    /** 指定ユーザーのタグ（作成順）。 */
    List<Tag> findByUserOrderByIdAsc(User user);

    Optional<Tag> findByIdAndUser(Long id, User user);

    /** 指定ユーザーのタグのうち、指定IDのもの（他人のタグIDは含まれない）。 */
    List<Tag> findByIdInAndUser(Collection<Long> ids, User user);

    Optional<Tag> findByUserAndName(User user, String name);

    boolean existsByUserAndName(User user, String name);

    long countByUser(User user);

    /** 持ち主のいない（ユーザーごとにする前の）共通タグ。 */
    List<Tag> findByUserIsNull();
}
