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

        // [LOG] DB에 저장되는 암호문의 앞부분(버전 확인용)을 출력
        if (encrypted != null && encrypted.length() > 5) {
            log.info("[Crypto] Encrypting to DB: {}", encrypted.substring(0, 5) + "...");
        }

        return encrypted;
    }

    @Override
    public String convertToEntityAttribute(String dbData) {
        if (dbData == null) return null;

        // [LOG] DB에서 읽어온 데이터의 버전 체크
        boolean isLatest = encryptPort.isLatestVersion(dbData);
        log.debug("[Crypto] Loading from DB: {}, isLatest: {}",
                dbData.substring(0, Math.min(dbData.length(), 5)) + "...", isLatest);

        if (!isLatest) {
            log.warn("[Crypto] Migration Triggered! Legacy data detected: {}",
                    dbData.substring(0, Math.min(dbData.length(), 5)) + "...");
            EncryptionContext.setMigrationRequired(true);
        }

        return encryptPort.decrypt(dbData);
    }
}