package com.serverbe.adapter.out.persistence.art;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface JpaRunningArtRepository extends JpaRepository<RunningArtEntity, Long> {
    List<RunningArtEntity> findByUser_Id(Long userId);

    @Modifying
    @Query("DELETE FROM RunningArtEntity r WHERE r.user.id = :userId")
    void deleteByUserId(Long userId);
}
