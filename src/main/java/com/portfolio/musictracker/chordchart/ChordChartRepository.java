package com.portfolio.musictracker.chordchart;

import com.portfolio.musictracker.entity.Song;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ChordChartRepository extends JpaRepository<ChordChart, Long> {

    Optional<ChordChart> findBySong(Song song);

    boolean existsBySongId(Long songId);

    @Modifying
    @Query("DELETE FROM ChordChart c WHERE c.song = :song")
    void deleteBySong(@Param("song") Song song);
}
