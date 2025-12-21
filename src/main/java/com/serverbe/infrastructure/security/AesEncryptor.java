package com.serverbe.infrastructure.security;

import com.serverbe.infrastructure.config.properties.EncryptionProperties;
import com.serverbe.infrastructure.error.BusinessException;
import com.serverbe.infrastructure.error.ErrorMessage;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

@Component
public class AesEncryptor {

    private final SecretKeySpec keySpec;
    private final String ALGORITHM;
    private final SecureRandom secureRandom;
    private final int tagLengthBit;
    private final int ivLevelByte;

    public AesEncryptor(SecureRandom secureRandom, EncryptionProperties encryptionProperties) {
        this.secureRandom = secureRandom;
        this.keySpec = new SecretKeySpec(
                encryptionProperties.secretKey().getBytes(StandardCharsets.UTF_8), encryptionProperties.keyAlgorithm()
        );
        this.ALGORITHM = encryptionProperties.algorithm();
        this.tagLengthBit = encryptionProperties.tagLengthBit();
        this.ivLevelByte = encryptionProperties.ivLengthByte();
    }

    public String encrypt(String plainText) {
        if (plainText == null) return null;
        try {
            byte[] iv = new byte[ivLevelByte];
            this.secureRandom.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec spec = new GCMParameterSpec(tagLengthBit, iv);
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, spec);

            byte[] cipherText = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));

            byte[] combined = ByteBuffer.allocate(iv.length + cipherText.length)
                    .put(iv)
                    .put(cipherText)
                    .array();
            return Base64.getEncoder().encodeToString(combined);
        } catch (Exception e) {
            throw new BusinessException(ErrorMessage.INTERNAL_SERVER_ERROR, "암호화 중 오류가 발생했습니다.");
        }
    }

    public String decrypt(String cipherText) {
        if (cipherText == null) return null;
        try {
            byte[] combined = Base64.getDecoder().decode(cipherText);
            ByteBuffer buffer = ByteBuffer.wrap(combined);

            byte[] iv = new byte[ivLevelByte];
            buffer.get(iv);
            byte[] cipherBytes = new byte[buffer.remaining()];
            buffer.get(cipherBytes);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec spec = new GCMParameterSpec(tagLengthBit, iv);
            cipher.init(Cipher.DECRYPT_MODE, keySpec, spec);

            return new String(cipher.doFinal(cipherBytes), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new BusinessException(ErrorMessage.INTERNAL_SERVER_ERROR, "복호화 중 오류가 발생했습니다.");
        }
    }
}