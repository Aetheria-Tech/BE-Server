package com.serverbe.domain.model.art;


import com.serverbe.domain.model.art.vo.Proficiency;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class RunningArt {
    private final Long id;
    private final String title;
    private final String content;
    private final String shape;
    private final Proficiency proficiency;
    private final String gpx;
    private final Long userId; // Entity의 UserEntity 대신 ID만 보유

    // 비즈니스 로직 예시: 메타데이터 업데이트
    public RunningArt updateMetadata(String title, String content) {
        return new RunningArt(id, title, content, shape, proficiency, gpx, userId);
    }
}