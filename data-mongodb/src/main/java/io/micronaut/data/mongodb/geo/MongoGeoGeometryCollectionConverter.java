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
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Converts {@link MongoGeoGeometryCollection} to and from a GeoJSON persisted map.
 *
 * @author radovanradic
 * @since 5.0.0
 */
@Singleton
public final class MongoGeoGeometryCollectionConverter implements AttributeConverter<Object, Map<String, Object>> {

    private final MongoGeoPointConverter pointConverter;
    private final MongoGeoMultiPointConverter multiPointConverter;
    private final MongoGeoLineStringConverter lineStringConverter;
    private final MongoGeoMultiLineStringConverter multiLineStringConverter;
    private final MongoGeoPolygonConverter polygonConverter;
    private final MongoGeoMultiPolygonConverter multiPolygonConverter;

    @Inject
    public MongoGeoGeometryCollectionConverter(MongoGeoPointConverter pointConverter,
                                              MongoGeoMultiPointConverter multiPointConverter,
                                              MongoGeoLineStringConverter lineStringConverter,
                                              MongoGeoMultiLineStringConverter multiLineStringConverter,
                                              MongoGeoPolygonConverter polygonConverter,
                                              MongoGeoMultiPolygonConverter multiPolygonConverter) {
        this.pointConverter = pointConverter;
        this.multiPointConverter = multiPointConverter;
        this.lineStringConverter = lineStringConverter;
        this.multiLineStringConverter = multiLineStringConverter;
        this.polygonConverter = polygonConverter;
        this.multiPolygonConverter = multiPolygonConverter;
    }

    @Override
    public @Nullable Map<String, Object> convertToPersistedValue(@Nullable Object entityValue,
                                                                  @NonNull ConversionContext context) {
        if (entityValue == null) {
            return null;
        }
        if (!(entityValue instanceof MongoGeoGeometryCollection geometryCollection)) {
            throw new IllegalArgumentException("Unsupported Mongo geospatial geometry-collection value type: " + entityValue.getClass().getName());
        }
        List<Map<String, Object>> geometries = new ArrayList<>(geometryCollection.geometries().size());
        for (MongoGeoGeometry geometry : geometryCollection.geometries()) {
            geometries.add(convertGeometryToMap(geometry));
        }
        Map<String, Object> geoJson = new LinkedHashMap<>();
        geoJson.put("type", "GeometryCollection");
        geoJson.put("geometries", geometries);
        return geoJson;
    }

    @Override
    public @Nullable Object convertToEntityValue(@Nullable Map<String, Object> persistedValue,
                                                 @NonNull ConversionContext context) {
        if (persistedValue == null) {
            return null;
        }
        Object type = persistedValue.get("type");
        if (type != null && !"GeometryCollection".equals(type)) {
            throw new IllegalArgumentException("Invalid GeoJSON geometry-collection type: " + type);
        }
        Object geometries = persistedValue.get("geometries");
        if (!(geometries instanceof List<?> geometryList)) {
            throw new IllegalArgumentException("Invalid persisted MongoGeoGeometryCollection value: " + persistedValue);
        }
        List<MongoGeoGeometry> geometryValues = new ArrayList<>(geometryList.size());
        for (Object geometry : geometryList) {
            if (!(geometry instanceof Map<?, ?> geometryMapRaw)) {
                throw new IllegalArgumentException("Invalid GeoJSON geometry entry: " + geometry);
            }
            Map<String, Object> geometryMap = (Map<String, Object>) geometryMapRaw;
            geometryValues.add(convertMapToGeometry(geometryMap));
        }
        return new MongoGeoGeometryCollection(List.copyOf(geometryValues));
    }

    /**
     * @param type The property type
     * @return Whether this type should use implicit geometry-collection conversion
     */
    public static boolean supportsImplicitGeometryCollectionType(Class<?> type) {
        return type == MongoGeoGeometryCollection.class || type.isAssignableFrom(MongoGeoGeometryCollection.class);
    }

    private Map<String, Object> convertGeometryToMap(MongoGeoGeometry geometry) {
        if (geometry instanceof MongoGeoPoint point) {
            return Objects.requireNonNull(pointConverter.convertToPersistedValue(point, ConversionContext.DEFAULT));
        }
        if (geometry instanceof MongoGeoMultiPoint multiPoint) {
            return Objects.requireNonNull(multiPointConverter.convertToPersistedValue(multiPoint, ConversionContext.DEFAULT));
        }
        if (geometry instanceof MongoGeoLineString lineString) {
            return Objects.requireNonNull(lineStringConverter.convertToPersistedValue(lineString, ConversionContext.DEFAULT));
        }
        if (geometry instanceof MongoGeoMultiLineString multiLineString) {
            return Objects.requireNonNull(multiLineStringConverter.convertToPersistedValue(multiLineString, ConversionContext.DEFAULT));
        }
        if (geometry instanceof MongoGeoPolygon polygon) {
            return Objects.requireNonNull(polygonConverter.convertToPersistedValue(polygon, ConversionContext.DEFAULT));
        }
        if (geometry instanceof MongoGeoMultiPolygon multiPolygon) {
            return Objects.requireNonNull(multiPolygonConverter.convertToPersistedValue(multiPolygon, ConversionContext.DEFAULT));
        }
        if (geometry instanceof MongoGeoGeometryCollection geometryCollection) {
            return Objects.requireNonNull(convertToPersistedValue(geometryCollection, ConversionContext.DEFAULT));
        }
        throw new IllegalArgumentException("Unsupported geometry collection entry type: " + geometry.getClass().getName());
    }

    private MongoGeoGeometry convertMapToGeometry(Map<String, Object> geometryMap) {
        Object type = geometryMap.get("type");
        if (!(type instanceof String geometryType)) {
            throw new IllegalArgumentException("Invalid GeoJSON geometry entry type: " + geometryMap);
        }
        return switch (geometryType) {
            case "Point" -> (MongoGeoGeometry) Objects.requireNonNull(pointConverter.convertToEntityValue(geometryMap, ConversionContext.DEFAULT));
            case "MultiPoint" -> (MongoGeoGeometry) Objects.requireNonNull(multiPointConverter.convertToEntityValue(geometryMap, ConversionContext.DEFAULT));
            case "LineString" -> (MongoGeoGeometry) Objects.requireNonNull(lineStringConverter.convertToEntityValue(geometryMap, ConversionContext.DEFAULT));
            case "MultiLineString" -> (MongoGeoGeometry) Objects.requireNonNull(multiLineStringConverter.convertToEntityValue(geometryMap, ConversionContext.DEFAULT));
            case "Polygon" -> (MongoGeoGeometry) Objects.requireNonNull(polygonConverter.convertToEntityValue(geometryMap, ConversionContext.DEFAULT));
            case "MultiPolygon" -> (MongoGeoGeometry) Objects.requireNonNull(multiPolygonConverter.convertToEntityValue(geometryMap, ConversionContext.DEFAULT));
            case "GeometryCollection" -> (MongoGeoGeometry) Objects.requireNonNull(convertToEntityValue(geometryMap, ConversionContext.DEFAULT));
            default -> throw new IllegalArgumentException("Unsupported GeoJSON geometry collection entry type: " + geometryType);
        };
    }
}
