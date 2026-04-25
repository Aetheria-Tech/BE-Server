package com.serverbe.domain.model.art;

import com.serverbe.domain.model.art.vo.Proficiency;
import lombok.Builder;

/**
 * @responsibility 사용자가 생성한 <b>런닝 아트</b>의 데이터를 관리하는 불변 객체입니다.
 * @param id 시스템 내부 고유 식별자
 * @param title 아트의 제목
 * @param content 아트에 대한 상세 설명
 * @param shape 아트의 외형 정보를 나타내는 데이터
 * @param distance 런닝 아트의 거리
 * @param proficiency 아트를 완성하기 위한 권장 숙련도 {@link Proficiency}
 * @param gpx GPS 경로 데이터 (GPX 포맷)
 * @param userId 아트를 소유한 유저의 고유 식별자
 */
@Builder(toBuilder = true)
public record RunningArt(
        Long id,
        String title,
        String content,
        String shape,
        Double distance,
        Proficiency proficiency,
        String gpx,
        Long userId,
        Double startLat,
        Double startLon
) {
    /**
     * @responsibility 제목과 설명 등 아트의 메타데이터를 갱신한 새로운 인스턴스를 생성합니다.
     * @param title 수정할 제목
     * @param content 수정할 설명
     * @return 정보가 수정된 새로운 {@link RunningArt} 객체
     */
    public RunningArt updateMetadata(String title, String content) {
        return new RunningArt(
                this.id,
                title,
                content,
                this.shape,
                this.distance,
                this.proficiency,
                this.gpx,
                this.userId,
                this.startLat,
                this.startLon
        );
    }
}