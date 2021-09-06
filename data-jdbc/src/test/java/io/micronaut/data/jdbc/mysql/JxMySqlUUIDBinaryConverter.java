package io.micronaut.data.jdbc.mysql;

import javax.persistence.AttributeConverter;
import javax.persistence.Converter;
import java.util.UUID;

@Converter
public class JxMySqlUUIDBinaryConverter implements AttributeConverter<UUID, byte[]> {

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
