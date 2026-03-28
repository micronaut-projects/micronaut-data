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
 * Converts {@link MongoGeoMultiPolygon} to and from a GeoJSON persisted map.
 *
 * @author radovanradic
 * @since 5.0.0
 */
@Singleton
public final class MongoGeoMultiPolygonConverter implements AttributeConverter<Object, Map<String, Object>> {

    @Override
    public @Nullable Map<String, Object> convertToPersistedValue(@Nullable Object entityValue,
                                                                  @NonNull ConversionContext context) {
        if (entityValue == null) {
            return null;
        }
        if (!(entityValue instanceof MongoGeoMultiPolygon multiPolygon)) {
            throw new IllegalArgumentException("Unsupported Mongo geospatial multi-polygon value type: " + entityValue.getClass().getName());
        }
        List<List<List<List<Double>>>> coordinates = new ArrayList<>(multiPolygon.coordinates().size());
        for (List<List<MongoGeoPoint>> polygon : multiPolygon.coordinates()) {
            List<List<List<Double>>> persistedPolygon = new ArrayList<>(polygon.size());
            for (List<MongoGeoPoint> ring : polygon) {
                List<List<Double>> persistedRing = new ArrayList<>(ring.size());
                for (MongoGeoPoint point : ring) {
                    persistedRing.add(List.of(point.x(), point.y()));
                }
                persistedPolygon.add(persistedRing);
            }
            coordinates.add(persistedPolygon);
        }
        Map<String, Object> geoJson = new LinkedHashMap<>();
        geoJson.put("type", "MultiPolygon");
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
        if (type != null && !"MultiPolygon".equals(type)) {
            throw new IllegalArgumentException("Invalid GeoJSON multi-polygon type: " + type);
        }
        Object coordinates = persistedValue.get("coordinates");
        if (!(coordinates instanceof List<?> polygons)) {
            throw new IllegalArgumentException("Invalid persisted MongoGeoMultiPolygon value: " + persistedValue);
        }
        List<List<List<MongoGeoPoint>>> multiPolygonCoordinates = new ArrayList<>(polygons.size());
        for (Object polygonValue : polygons) {
            if (!(polygonValue instanceof List<?> rings)) {
                throw new IllegalArgumentException("Invalid GeoJSON multi-polygon polygon: " + polygonValue);
            }
            List<List<MongoGeoPoint>> polygonRings = new ArrayList<>(rings.size());
            for (Object ringValue : rings) {
                if (!(ringValue instanceof List<?> ring)) {
                    throw new IllegalArgumentException("Invalid GeoJSON multi-polygon ring: " + ringValue);
                }
                List<MongoGeoPoint> polygonRing = new ArrayList<>(ring.size());
                for (Object pointValue : ring) {
                    if (!(pointValue instanceof List<?> coordinatePair) || coordinatePair.size() != 2) {
                        throw new IllegalArgumentException("Invalid GeoJSON multi-polygon coordinate pair: " + pointValue);
                    }
                    Object x = coordinatePair.get(0);
                    Object y = coordinatePair.get(1);
                    if (!(x instanceof Number xNumber) || !(y instanceof Number yNumber)) {
                        throw new IllegalArgumentException("Invalid GeoJSON multi-polygon numeric coordinates: " + pointValue);
                    }
                    polygonRing.add(new MongoGeoPoint(xNumber.doubleValue(), yNumber.doubleValue()));
                }
                polygonRings.add(polygonRing);
            }
            multiPolygonCoordinates.add(polygonRings);
        }
        return new MongoGeoMultiPolygon(List.copyOf(multiPolygonCoordinates));
    }

    /**
     * @param type The property type
     * @return Whether this type should use implicit multi-polygon conversion
     */
    public static boolean supportsImplicitMultiPolygonType(Class<?> type) {
        return type == MongoGeoMultiPolygon.class || type.isAssignableFrom(MongoGeoMultiPolygon.class);
    }
}
