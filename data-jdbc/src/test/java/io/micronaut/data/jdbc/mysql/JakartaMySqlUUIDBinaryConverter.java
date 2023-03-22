package io.micronaut.data.jdbc.mysql;

import jakarta.persistence.AttributeConverter;

import jakarta.persistence.Converter;
import java.util.UUID;

@Converter
public class JakartaMySqlUUIDBinaryConverter implements AttributeConverter<UUID, byte[]> {

    MySqlUUIDBinaryConverter converter = new MySqlUUIDBinaryConverter();

    @Override
    public byte[] convertToDatabaseColumn(UUID attribute) {
        return converter.convertToPersistedValue(attribute, null);
    }

    @Override
    public UUID convertToEntityAttribute(byte[] dbData) {
        return converter.convertToEntityValue(dbData, null);
    }
}
