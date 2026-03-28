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
 * Converts {@link MongoGeoLineString} to and from a GeoJSON persisted map.
 *
 * @author radovanradic
 * @since 5.0.0
 */
@Singleton
public final class MongoGeoLineStringConverter implements AttributeConverter<Object, Map<String, Object>> {

    /**
     * Converts a line string modeled value to a persisted GeoJSON map.
     *
     * @param entityValue The modeled geospatial value
     * @param context The conversion context
     * @return The persisted GeoJSON map or {@code null}
     */
    @Override
    public @Nullable Map<String, Object> convertToPersistedValue(@Nullable Object entityValue,
                                                                  @NonNull ConversionContext context) {
        if (entityValue == null) {
            return null;
        }
        if (!(entityValue instanceof MongoGeoLineString lineString)) {
            throw new IllegalArgumentException("Unsupported Mongo geospatial line string value type: " + entityValue.getClass().getName());
        }
        List<List<Double>> coordinates = new ArrayList<>(lineString.coordinates().size());
        for (MongoGeoPoint point : lineString.coordinates()) {
            coordinates.add(List.of(point.x(), point.y()));
        }
        Map<String, Object> geoJson = new LinkedHashMap<>();
        geoJson.put("type", "LineString");
        geoJson.put("coordinates", coordinates);
        return geoJson;
    }

    /**
     * Converts a persisted GeoJSON map to a line string modeled value.
     *
     * @param persistedValue The persisted GeoJSON map
     * @param context The conversion context
     * @return The line string modeled value or {@code null}
     */
    @Override
    public @Nullable Object convertToEntityValue(@Nullable Map<String, Object> persistedValue,
                                                 @NonNull ConversionContext context) {
        if (persistedValue == null) {
            return null;
        }
        Object type = persistedValue.get("type");
        if (type != null && !"LineString".equals(type)) {
            throw new IllegalArgumentException("Invalid GeoJSON line string type: " + type);
        }
        Object coordinates = persistedValue.get("coordinates");
        if (!(coordinates instanceof List<?> coordinatePairs)) {
            throw new IllegalArgumentException("Invalid persisted MongoGeoLineString value: " + persistedValue);
        }
        List<MongoGeoPoint> lineCoordinates = new ArrayList<>(coordinatePairs.size());
        for (Object pointValue : coordinatePairs) {
            if (!(pointValue instanceof List<?> coordinatePair) || coordinatePair.size() != 2) {
                throw new IllegalArgumentException("Invalid GeoJSON line string coordinate pair: " + pointValue);
            }
            Object x = coordinatePair.get(0);
            Object y = coordinatePair.get(1);
            if (!(x instanceof Number xNumber) || !(y instanceof Number yNumber)) {
                throw new IllegalArgumentException("Invalid GeoJSON line string numeric coordinates: " + pointValue);
            }
            lineCoordinates.add(new MongoGeoPoint(xNumber.doubleValue(), yNumber.doubleValue()));
        }
        return new MongoGeoLineString(List.copyOf(lineCoordinates));
    }

    /**
     * @param type The property type
     * @return Whether this type should use implicit line string conversion
     */
    public static boolean supportsImplicitLineStringType(Class<?> type) {
        return type == MongoGeoLineString.class || type.isAssignableFrom(MongoGeoLineString.class);
    }
}
