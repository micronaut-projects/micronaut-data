package io.micronaut.data.document.mongodb.geovalue.rawquery;

import io.micronaut.core.convert.ConversionContext;
import io.micronaut.data.model.runtime.convert.AttributeConverter;
import io.micronaut.data.mongodb.geo.MongoGeoPoint;
import jakarta.inject.Singleton;

import java.util.List;
import java.util.Map;

@Singleton
final class ShiftedGeoPointConverter implements AttributeConverter<MongoGeoPoint, Map<String, Object>> {

    @Override
    public Map<String, Object> convertToPersistedValue(MongoGeoPoint entityValue, ConversionContext context) {
        return Map.of(
            "type", "Point",
            "coordinates", List.of(entityValue.x() + 100, entityValue.y() + 100)
        );
    }

    @Override
    public MongoGeoPoint convertToEntityValue(Map<String, Object> persistedValue, ConversionContext context) {
        Object coordinates = persistedValue.get("coordinates");
        if (coordinates instanceof List<?> list && list.size() >= 2) {
            Number x = (Number) list.get(0);
            Number y = (Number) list.get(1);
            return new MongoGeoPoint(x.doubleValue() - 100, y.doubleValue() - 100);
        }
        throw new IllegalArgumentException("Invalid persisted geo point: " + persistedValue);
    }
}
