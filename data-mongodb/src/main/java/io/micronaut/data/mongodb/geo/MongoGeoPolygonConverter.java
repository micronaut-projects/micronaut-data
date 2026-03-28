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
 * Converts {@link MongoGeoPolygon} to and from a GeoJSON persisted map.
 *
 * @author radovanradic
 * @since 5.0.0
 */
@Singleton
public final class MongoGeoPolygonConverter implements AttributeConverter<Object, Map<String, Object>> {

    @Override
    public @Nullable Map<String, Object> convertToPersistedValue(@Nullable Object entityValue,
                                                                  @NonNull ConversionContext context) {
        if (entityValue == null) {
            return null;
        }
        if (!(entityValue instanceof MongoGeoPolygon polygon)) {
            throw new IllegalArgumentException("Unsupported Mongo geospatial polygon value type: " + entityValue.getClass().getName());
        }
        List<List<List<Double>>> coordinates = new ArrayList<>(polygon.coordinates().size());
        for (List<MongoGeoPoint> ring : polygon.coordinates()) {
            List<List<Double>> persistedRing = new ArrayList<>(ring.size());
            for (MongoGeoPoint point : ring) {
                persistedRing.add(List.of(point.x(), point.y()));
            }
            coordinates.add(persistedRing);
        }
        Map<String, Object> geoJson = new LinkedHashMap<>();
        geoJson.put("type", "Polygon");
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
        if (type != null && !"Polygon".equals(type)) {
            throw new IllegalArgumentException("Invalid GeoJSON polygon type: " + type);
        }
        Object coordinates = persistedValue.get("coordinates");
        if (!(coordinates instanceof List<?> rings)) {
            throw new IllegalArgumentException("Invalid persisted MongoGeoPolygon value: " + persistedValue);
        }
        List<List<MongoGeoPoint>> polygonRings = new ArrayList<>(rings.size());
        for (Object ringValue : rings) {
            if (!(ringValue instanceof List<?> ring)) {
                throw new IllegalArgumentException("Invalid GeoJSON polygon ring: " + ringValue);
            }
            List<MongoGeoPoint> polygonRing = new ArrayList<>(ring.size());
            for (Object pointValue : ring) {
                if (!(pointValue instanceof List<?> coordinatePair) || coordinatePair.size() != 2) {
                    throw new IllegalArgumentException("Invalid GeoJSON polygon coordinate pair: " + pointValue);
                }
                Object x = coordinatePair.get(0);
                Object y = coordinatePair.get(1);
                if (!(x instanceof Number xNumber) || !(y instanceof Number yNumber)) {
                    throw new IllegalArgumentException("Invalid GeoJSON polygon numeric coordinates: " + pointValue);
                }
                polygonRing.add(new MongoGeoPoint(xNumber.doubleValue(), yNumber.doubleValue()));
            }
            polygonRings.add(polygonRing);
        }
        return new MongoGeoPolygon(List.copyOf(polygonRings));
    }

    /**
     * @param type The property type
     * @return Whether this type should use implicit polygon conversion
     */
    public static boolean supportsImplicitPolygonType(Class<?> type) {
        return type == MongoGeoPolygon.class || type.isAssignableFrom(MongoGeoPolygon.class);
    }
}
