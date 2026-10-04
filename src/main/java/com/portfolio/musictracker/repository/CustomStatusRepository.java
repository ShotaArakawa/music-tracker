package com.portfolio.musictracker.repository;

import com.portfolio.musictracker.entity.CustomStatus;
import com.portfolio.musictracker.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CustomStatusRepository extends JpaRepository<CustomStatus, Long> {

    /** 指定ユーザーが追加したステータス（追加順）。 */
    List<CustomStatus> findByUserOrderByIdAsc(User user);

    Optional<CustomStatus> findByIdAndUser(Long id, User user);

    boolean existsByUserAndName(User user, String name);

    long countByUser(User user);
}
