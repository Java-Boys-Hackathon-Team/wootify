package ru.javaboys.wootify.security;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * Прозрачное шифрование строкового атрибута при записи в БД.
 */
@Converter
public class SecretConverter implements AttributeConverter<String, String> {

    @Override
    public String convertToDatabaseColumn(String attribute) {
        return SecretCipher.encrypt(attribute);
    }

    @Override
    public String convertToEntityAttribute(String dbData) {
        return SecretCipher.decrypt(dbData);
    }
}
