package io.micronaut.data.jdbc.h2.remap;

import io.micronaut.core.convert.ConversionContext;
import io.micronaut.data.model.runtime.convert.AttributeConverter;
import jakarta.inject.Singleton;

import java.util.UUID;

record StudentId(UUID id) {
}

@Singleton
class StudentIdAttributeConverter implements AttributeConverter<StudentId, UUID> {
    public UUID convertToPersistedValue(StudentId entityValue, ConversionContext context) {
        return entityValue.id();
    }

    public StudentId convertToEntityValue(UUID persistedValue, ConversionContext context) {
        return new StudentId(persistedValue);
    }
}
