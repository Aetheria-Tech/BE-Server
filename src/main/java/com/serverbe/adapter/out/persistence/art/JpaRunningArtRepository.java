package com.serverbe.adapter.out.persistence.art;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface JpaRunningArtRepository extends JpaRepository<RunningArtEntity, Long> {
    List<RunningArtEntity> findByUser_Id(Long userId);

    @Modifying
    @Query("DELETE FROM RunningArtEntity r WHERE r.user.id = :userId")
    void deleteByUserId(@Param("userId")Long userId);
}
