package com.serverbe.adapter.out.persistence.art;

import com.serverbe.domain.model.art.vo.Proficiency;
import com.serverbe.adapter.out.persistence.user.UserEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@Table(name = "running_arts")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RunningArtEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "content", nullable = false)
    private String content;

    @Column(name = "shape", nullable = false)
    private String shape;

    @Enumerated(EnumType.STRING)
    @Column(name = "proficiency", nullable = false)
    private Proficiency proficiency;

    @Lob
    @Column(name = "gpx", columnDefinition = "LONGTEXT")
    private String gpx;

    @Column(name = "start_lat", nullable = false)
    private Double startLat;

    // 반경 검색을 위한 시작점 경도(Longitude)
    @Column(name = "start_lon", nullable = false)
    private Double startLon;

    // 연관관계 설정 (주인)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private UserEntity user;

    @Builder
    public RunningArtEntity(
            String title,
            String content,
            String shape,
            Proficiency proficiency,
            String gpx,
            UserEntity user,
            Double startLat,
            Double startLon
    ) {
        this.title = title;
        this.content = content;
        this.shape = shape;
        this.proficiency = proficiency;
        this.gpx = gpx;
        this.user = user;
        this.startLat = startLat;
        this.startLon = startLon;
    }

    public void assignUser(UserEntity user) {
        this.user = user;
    }

    public void updateMetadata(String title, String content) {
        if (title != null) {
            this.title = title;
        }
        if (content != null) {
            this.content = content;
        }
    }
}