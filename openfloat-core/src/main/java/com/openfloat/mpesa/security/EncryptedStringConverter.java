package com.openfloat.mpesa.security;

import com.openfloat.mpesa.common.util.EncryptionUtils;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * JPA AttributeConverter that transparently encrypts/decrypts
 * sensitive string fields (phone numbers, account references) at rest.
 * <p>
 * Uses AES-256-GCM encryption via {@link EncryptionUtils}.
 * Applied to entity fields with {@code @Convert(converter = EncryptedStringConverter.class)}.
 * </p>
 */
@Component
@Converter
public class EncryptedStringConverter implements AttributeConverter<String, String> {

    private final String encryptionKey;

    public EncryptedStringConverter(
            @Value("${openfloat.encryption.key}") String encryptionKey) {
        this.encryptionKey = encryptionKey;
    }

    @Override
    public String convertToDatabaseColumn(String attribute) {
        if (attribute == null || attribute.isEmpty()) {
            return attribute;
        }
        return EncryptionUtils.encrypt(attribute, encryptionKey);
    }

    @Override
    public String convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isEmpty()) {
            return dbData;
        }
        try {
            return EncryptionUtils.decrypt(dbData, encryptionKey);
        } catch (Exception e) {
            // If decryption fails (e.g., key rotation), return raw data
            // This should be logged and investigated
            return dbData;
        }
    }
}
