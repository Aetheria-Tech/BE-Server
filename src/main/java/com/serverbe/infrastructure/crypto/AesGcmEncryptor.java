package com.serverbe.infrastructure.crypto;

import com.serverbe.application.port.out.crypto.EncryptPort;
import com.serverbe.infrastructure.config.properties.EncryptionProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

@Component
@RequiredArgsConstructor
public class AesGcmEncryptor implements EncryptPort {
    private final EncryptionProperties properties;
    private static final String DELIMITER = ":";

    @Override
    public String encrypt(String plainText) {
        if (plainText == null) return null;

        try {
            byte[] iv = generateIv();
            Cipher cipher = Cipher.getInstance(properties.algorithm());
            GCMParameterSpec spec = new GCMParameterSpec(properties.tagLengthBit(), iv);
            SecretKeySpec keySpec = new SecretKeySpec(properties.getActiveKey(), properties.keyAlgorithm());

            cipher.init(Cipher.ENCRYPT_MODE, keySpec, spec);
            byte[] cipherText = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));

            // 최종 포맷: v1:Base64(IV):Base64(Cipher)
            return String.format("%s%s%s%s%s",
                    properties.activeVersion(), DELIMITER,
                    Base64.getEncoder().encodeToString(iv), DELIMITER,
                    Base64.getEncoder().encodeToString(cipherText));
        } catch (Exception e) {
            throw new RuntimeException("암호화 실패", e);
        }
    }

    @Override
    public String decrypt(String encryptedData) {
        if (encryptedData == null) return null;

        try {
            String[] parts = encryptedData.split(DELIMITER);
            if (parts.length != 3) throw new IllegalArgumentException("잘못된 암호문 포맷입니다.");

            String version = parts[0];
            byte[] iv = Base64.getDecoder().decode(parts[1]);
            byte[] cipherText = Base64.getDecoder().decode(parts[2]);
            byte[] key = properties.getKeyByVersion(version);

            Cipher cipher = Cipher.getInstance(properties.algorithm());
            GCMParameterSpec spec = new GCMParameterSpec(properties.tagLengthBit(), iv);
            SecretKeySpec keySpec = new SecretKeySpec(key, properties.keyAlgorithm());

            cipher.init(Cipher.DECRYPT_MODE, keySpec, spec);
            return new String(cipher.doFinal(cipherText), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("복호화 실패", e);
        }
    }

    private byte[] generateIv() {
        byte[] iv = new byte[properties.ivLengthByte()];
        new SecureRandom().nextBytes(iv);
        return iv;
    }

    @Override
    public boolean isLatestVersion(String cipherText) {
        if (cipherText == null) return false;
        return cipherText.startsWith(properties.activeVersion() + DELIMITER);
    }
}