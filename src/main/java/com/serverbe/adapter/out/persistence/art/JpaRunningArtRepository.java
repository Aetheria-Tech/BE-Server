package com.serverbe.adapter.out.persistence.art;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface JpaRunningArtRepository extends JpaRepository<RunningArtEntity, Long> {
    Page<RunningArtEntity> findByUser_Id(Long userId, Pageable pageable);

    @Modifying
    @Query("DELETE FROM RunningArtEntity r WHERE r.user.id = :userId")
    void deleteByUserId(@Param("userId")Long userId);

    @Query("SELECT r.id FROM RunningArtEntity r WHERE r.user.id = :userId")
    List<Long> findIdsByUserId(@Param("userId") Long userId);
}
