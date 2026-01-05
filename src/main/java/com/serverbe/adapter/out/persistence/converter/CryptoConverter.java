package com.serverbe.adapter.out.persistence.converter;

import com.serverbe.application.port.out.crypto.EncryptPort;
import com.serverbe.infrastructure.crypto.EncryptionContext;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j; // 추가

@Slf4j // 로깅 어노테이션 추가
@Converter
@RequiredArgsConstructor
public class CryptoConverter implements AttributeConverter<String, String> {

    private final EncryptPort encryptPort;

    @Override
    public String convertToDatabaseColumn(String attribute) {
        if (attribute == null) return null;

        String encrypted = encryptPort.encrypt(attribute);

        if (encrypted != null && encrypted.length() > 5) {
            log.debug("[Crypto] DB 데이터 암호화 완료: {}", encrypted.substring(0, 5) + "...");
        }

        return encrypted;
    }

    @Override
    public String convertToEntityAttribute(String dbData) {
        if (dbData == null) return null;

        boolean isLatest = encryptPort.isLatestVersion(dbData);
        log.debug("[Crypto] DB 데이터 로드: {}, 최신 버전 여부: {}", dbData.substring(0, Math.min(dbData.length(), 5)) + "...", isLatest);

        if (!isLatest) {
            log.warn("[Crypto] 마이그레이션 실행 대상 감지(구버전 데이터 발견): {}", dbData.substring(0, Math.min(dbData.length(), 5)) + "...");
            EncryptionContext.setMigrationRequired(true);
        }

        return encryptPort.decrypt(dbData);
    }
}