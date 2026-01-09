package com.serverbe.infrastructure.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * @param algorithm     사용할 암호화 알고리즘 (예: AES/GCM/NoPadding)
 * @param tagLengthBit  인증 태그 길이 (Bit 단위)
 * @param ivLengthByte  초기화 벡터(IV) 길이 (Byte 단위)
 * @param keyAlgorithm  키 생성 알고리즘 (예: AES)
 * @param activeVersion 현재 암호화에 사용할 주(Primary) 키 버전
 * @param keys          버전별 암호화 키 맵 (Key: 버전, Value: 실제 키 값)
 * @responsibility 시스템 내 민감 정보 암호화에 사용되는 <b>알고리즘 및 다중 키 설정</b>을 관리하는 프로퍼티 객체입니다.
 * @implSpec <b>encryption</b> 접두사 설정을 바인딩하며, 키 로테이션 대응을 위해 버전별 키 관리 기능을 제공합니다.
 */
@ConfigurationProperties(prefix = "encryption")
public record EncryptionProperties(
        String algorithm,
        int tagLengthBit,
        int ivLengthByte,
        String keyAlgorithm,
        String activeVersion,
        Map<String, String> keys
) {
    /**
     * @return 활성 암호화 키의 바이트 배열
     * @responsibility 현재 활성화된 버전({@link #activeVersion})의 키를 바이트 배열로 반환합니다.
     */
    public byte[] getActiveKey() {
        return keys.get(activeVersion).getBytes(StandardCharsets.UTF_8);
    }

    /**
     * @param version 조회할 키의 버전 명칭
     * @return 해당 버전의 암호화 키 바이트 배열
     * @throws IllegalArgumentException 존재하지 않는 키 버전을 요청했을 경우 발생
     * @responsibility 특정 버전의 키를 바이트 배열로 반환합니다.
     * @implNote 과거에 암호화된 데이터를 복호화할 때, 데이터와 함께 저장된 버전 정보를 바탕으로 해당 키를 조회할 때 사용합니다.
     */
    public byte[] getKeyByVersion(String version) {
        String key = keys.get(version);
        if (key == null) throw new IllegalArgumentException("지원되지 않는 키 버전입니다: " + version);
        return key.getBytes(StandardCharsets.UTF_8);
    }
}