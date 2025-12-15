package com.serverbe.infrastructure.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "encryption")
public record EncryptionProperties(
        String secretKey,
        String algorithm,
        int tagLengthBit,
        int ivLengthByte,
        String keyAlgorithm
) {
}