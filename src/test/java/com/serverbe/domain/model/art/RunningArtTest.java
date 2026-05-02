package com.serverbe.domain.model.art;

import com.serverbe.domain.model.art.vo.Proficiency;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RunningArtTest {

    @Test
    @DisplayName("런닝 아트 메타데이터(제목, 내용) 수정 시, 기존 값은 유지되고 변경된 값을 가진 새로운 인스턴스가 반환된다 (불변성 검증)")
    void updateMetadata_ReturnsNewInstanceWithUpdatedValues() {
        // given: 기존 런닝 아트 데이터 세팅
        RunningArt originalArt = RunningArt.builder()
                .id(1L)
                .userId(100L)
                .title("기존 제목")
                .content("기존 내용")
                .shape("강아지")
                .proficiency(Proficiency.EXPERT)
                .gpx("q`jdFub_fW@B?BAD")
                .startLat(37.323)
                .startLon(127.106)
                .build();

        String newTitle = "수정된 댕댕런";
        String newContent = "내용을 새롭게 업데이트했습니다!";

        // when: 메타데이터 업데이트 실행
        RunningArt updatedArt = originalArt.updateMetadata(newTitle, newContent);

        // then: 결과 검증
        // 1. 객체가 새로 생성되었는지 확인 (메모리 주소가 다른지 = 불변 객체인지)
        assertThat(updatedArt).isNotSameAs(originalArt);

        // 2. 변경을 요청한 값(제목, 내용)이 정상적으로 바뀌었는지 확인
        assertThat(updatedArt.title()).isEqualTo(newTitle);
        assertThat(updatedArt.content()).isEqualTo(newContent);

        // 3. 변경하지 않은 나머지 핵심 필드들은 원본 그대로 유지되었는지 확인
        assertThat(updatedArt.id()).isEqualTo(originalArt.id());
        assertThat(updatedArt.userId()).isEqualTo(originalArt.userId());
        assertThat(updatedArt.shape()).isEqualTo(originalArt.shape());
        assertThat(updatedArt.proficiency()).isEqualTo(originalArt.proficiency());
        assertThat(updatedArt.gpx()).isEqualTo(originalArt.gpx());
        assertThat(updatedArt.startLat()).isEqualTo(originalArt.startLat());
        assertThat(updatedArt.startLon()).isEqualTo(originalArt.startLon());
    }
}