package com.serverbe.application.port.out.crypto;

public interface EncryptPort {
    String encrypt(String plainText);
    String decrypt(String cipherText);
}