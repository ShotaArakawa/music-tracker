package com.portfolio.musictracker.repository;

import com.portfolio.musictracker.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByUsername(String username);

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);

    /** 現在のお試しアカウントの数。 */
    long countByDemoTrue();

    /** 指定日時より前に作られたお試しアカウント（削除対象）。 */
    List<User> findByDemoTrueAndCreatedAtBefore(LocalDateTime time);
}
