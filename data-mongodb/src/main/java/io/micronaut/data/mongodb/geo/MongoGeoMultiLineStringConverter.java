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
 * Converts {@link MongoGeoMultiLineString} to and from a GeoJSON persisted map.
 *
 * @author radovanradic
 * @since 5.0.0
 */
@Singleton
public final class MongoGeoMultiLineStringConverter implements AttributeConverter<Object, Map<String, Object>> {

    @Override
    public @Nullable Map<String, Object> convertToPersistedValue(@Nullable Object entityValue,
                                                                  @NonNull ConversionContext context) {
        if (entityValue == null) {
            return null;
        }
        if (!(entityValue instanceof MongoGeoMultiLineString multiLineString)) {
            throw new IllegalArgumentException("Unsupported Mongo geospatial multi-line-string value type: " + entityValue.getClass().getName());
        }
        List<List<List<Double>>> coordinates = new ArrayList<>(multiLineString.coordinates().size());
        for (List<MongoGeoPoint> lineString : multiLineString.coordinates()) {
            List<List<Double>> persistedLineString = new ArrayList<>(lineString.size());
            for (MongoGeoPoint point : lineString) {
                persistedLineString.add(List.of(point.x(), point.y()));
            }
            coordinates.add(persistedLineString);
        }
        Map<String, Object> geoJson = new LinkedHashMap<>();
        geoJson.put("type", "MultiLineString");
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
        if (type != null && !"MultiLineString".equals(type)) {
            throw new IllegalArgumentException("Invalid GeoJSON multi-line-string type: " + type);
        }
        Object coordinates = persistedValue.get("coordinates");
        if (!(coordinates instanceof List<?> lineStrings)) {
            throw new IllegalArgumentException("Invalid persisted MongoGeoMultiLineString value: " + persistedValue);
        }
        List<List<MongoGeoPoint>> multiLineStringCoordinates = new ArrayList<>(lineStrings.size());
        for (Object lineStringValue : lineStrings) {
            if (!(lineStringValue instanceof List<?> lineString)) {
                throw new IllegalArgumentException("Invalid GeoJSON multi-line-string line-string: " + lineStringValue);
            }
            List<MongoGeoPoint> lineCoordinates = new ArrayList<>(lineString.size());
            for (Object pointValue : lineString) {
                if (!(pointValue instanceof List<?> coordinatePair) || coordinatePair.size() != 2) {
                    throw new IllegalArgumentException("Invalid GeoJSON multi-line-string coordinate pair: " + pointValue);
                }
                Object x = coordinatePair.get(0);
                Object y = coordinatePair.get(1);
                if (!(x instanceof Number xNumber) || !(y instanceof Number yNumber)) {
                    throw new IllegalArgumentException("Invalid GeoJSON multi-line-string numeric coordinates: " + pointValue);
                }
                lineCoordinates.add(new MongoGeoPoint(xNumber.doubleValue(), yNumber.doubleValue()));
            }
            multiLineStringCoordinates.add(lineCoordinates);
        }
        return new MongoGeoMultiLineString(List.copyOf(multiLineStringCoordinates));
    }

    /**
     * @param type The property type
     * @return Whether this type should use implicit multi-line-string conversion
     */
    public static boolean supportsImplicitMultiLineStringType(Class<?> type) {
        return type == MongoGeoMultiLineString.class || type.isAssignableFrom(MongoGeoMultiLineString.class);
    }
}
