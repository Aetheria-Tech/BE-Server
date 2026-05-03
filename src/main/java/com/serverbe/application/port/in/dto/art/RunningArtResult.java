package com.serverbe.application.port.in.dto.art;

import com.serverbe.domain.model.art.RunningArt;
import com.serverbe.domain.model.art.vo.Proficiency;
import lombok.Builder;

/**
 * @responsibility 런닝 아트의 상세 정보를 외부 계층에 전달하기 위한 데이터 객체입니다.
 * @param id 런닝 아트 고유 식별자
 * @param title 아트 제목
 * @param content 아트 상세 설명
 * @param shape 아트의 외형 데이터
 * @param proficiency 권장 숙련도 {@link Proficiency}
 * @param gpx GPS 경로 데이터
 * @param userId 소유자의 고유 식별자
 */
@Builder
public record RunningArtResult(
        Long id,
        String title,
        String content,
        String shape,
        Proficiency proficiency,
        String gpx,
        Long userId
) {
    /**
     * @responsibility {@link RunningArt} 도메인 모델을 {@link RunningArtResult} DTO로 변환합니다.
     * @implNote 레코드의 표준 접근자를 사용하여 데이터를 추출하며, <b>Lombok Builder</b>를 통해 객체를 생성합니다.
     * @param runningArt 변환할 도메인 엔티티
     * @return 런닝 아트 정보가 담긴 DTO 객체
     */
    public static RunningArtResult toResult(RunningArt runningArt){
        return RunningArtResult.builder()
                .id(runningArt.id())
                .title(runningArt.title())
                .content(runningArt.content())
                .shape(runningArt.shape())
                .proficiency(runningArt.proficiency())
                .gpx(runningArt.gpx())
                .userId(runningArt.userId())
                .build();
    }
}