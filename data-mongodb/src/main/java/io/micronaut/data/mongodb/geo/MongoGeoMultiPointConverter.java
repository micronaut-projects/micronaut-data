/*
 * Copyright 2017-2026 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.data.mongodb.geo;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.convert.ConversionContext;
import io.micronaut.data.model.runtime.convert.AttributeConverter;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Converts {@link MongoGeoMultiPoint} to and from a GeoJSON persisted map.
 *
 * @author radovanradic
 * @since 5.0.0
 */
@Singleton
public final class MongoGeoMultiPointConverter implements AttributeConverter<Object, Map<String, Object>> {

    @Override
    public @Nullable Map<String, Object> convertToPersistedValue(@Nullable Object entityValue,
                                                                  @NonNull ConversionContext context) {
        if (entityValue == null) {
            return null;
        }
        if (!(entityValue instanceof MongoGeoMultiPoint multiPoint)) {
            throw new IllegalArgumentException("Unsupported Mongo geospatial multi-point value type: " + entityValue.getClass().getName());
        }
        List<List<Double>> coordinates = new ArrayList<>(multiPoint.coordinates().size());
        for (MongoGeoPoint point : multiPoint.coordinates()) {
            coordinates.add(List.of(point.x(), point.y()));
        }
        Map<String, Object> geoJson = new LinkedHashMap<>();
        geoJson.put("type", "MultiPoint");
        geoJson.put("coordinates", coordinates);
        return geoJson;
    }

    @Override
    public @Nullable Object convertToEntityValue(@Nullable Map<String, Object> persistedValue,
                                                 @NonNull ConversionContext context) {
        if (persistedValue == null) {
            return null;
        }
        Object type = persistedValue.get("type");
        if (type != null && !"MultiPoint".equals(type)) {
            throw new IllegalArgumentException("Invalid GeoJSON multi-point type: " + type);
        }
        Object coordinates = persistedValue.get("coordinates");
        if (!(coordinates instanceof List<?> points)) {
            throw new IllegalArgumentException("Invalid persisted MongoGeoMultiPoint value: " + persistedValue);
        }
        List<MongoGeoPoint> multiPointCoordinates = new ArrayList<>(points.size());
        for (Object pointValue : points) {
            if (!(pointValue instanceof List<?> coordinatePair) || coordinatePair.size() != 2) {
                throw new IllegalArgumentException("Invalid GeoJSON multi-point coordinate pair: " + pointValue);
            }
            Object x = coordinatePair.get(0);
            Object y = coordinatePair.get(1);
            if (!(x instanceof Number xNumber) || !(y instanceof Number yNumber)) {
                throw new IllegalArgumentException("Invalid GeoJSON multi-point numeric coordinates: " + pointValue);
            }
            multiPointCoordinates.add(new MongoGeoPoint(xNumber.doubleValue(), yNumber.doubleValue()));
        }
        return new MongoGeoMultiPoint(List.copyOf(multiPointCoordinates));
    }

    /**
     * @param type The property type
     * @return Whether this type should use implicit multi-point conversion
     */
    public static boolean supportsImplicitMultiPointType(Class<?> type) {
        return type == MongoGeoMultiPoint.class || type.isAssignableFrom(MongoGeoMultiPoint.class);
    }
}
