package com.serverbe.infrastructure.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.charset.StandardCharsets;
import java.util.Map;

@ConfigurationProperties(prefix = "encryption")
public record EncryptionProperties(
        String algorithm,
        int tagLengthBit,
        int ivLengthByte,
        String keyAlgorithm,
        String activeVersion,
        Map<String, String> keys
) {
    public byte[] getActiveKey() {
        return keys.get(activeVersion).getBytes(StandardCharsets.UTF_8);
    }

    public byte[] getKeyByVersion(String version) {
        String key = keys.get(version);
        if (key == null) throw new IllegalArgumentException("지원되지 않는 키 버전입니다: " + version);
        return key.getBytes(StandardCharsets.UTF_8);
    }
}