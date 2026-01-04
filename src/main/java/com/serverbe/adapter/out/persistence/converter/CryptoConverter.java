package com.serverbe.adapter.out.persistence.converter;

import com.serverbe.application.port.out.crypto.EncryptPort;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import lombok.RequiredArgsConstructor;

@Converter
@RequiredArgsConstructor
public class CryptoConverter implements AttributeConverter<String, String> {

    private final EncryptPort encryptPort;

    @Override
    public String convertToDatabaseColumn(String attribute) {
        return encryptPort.encrypt(attribute);
    }

    @Override
    public String convertToEntityAttribute(String dbData) {
        return encryptPort.decrypt(dbData);
    }
}