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

import java.util.Map;

/**
 * Utility methods for implicit Mongo geospatial converter resolution.
 *
 * @author radovanradic
 * @since 5.0.0
 */
public final class MongoGeoConverters {

    private MongoGeoConverters() {
    }

    /**
     * @param type The property type
     * @return Whether this type has implicit geospatial converter support
     */
    public static boolean supportsImplicitGeoType(Class<?> type) {
        return MongoGeoPointConverter.supportsImplicitPointType(type)
                || MongoGeoLineStringConverter.supportsImplicitLineStringType(type)
                || MongoGeoMultiLineStringConverter.supportsImplicitMultiLineStringType(type)
                || MongoGeoMultiPointConverter.supportsImplicitMultiPointType(type)
                || MongoGeoGeometryCollectionConverter.supportsImplicitGeometryCollectionType(type)
                || MongoGeoMultiPolygonConverter.supportsImplicitMultiPolygonType(type)
                || MongoGeoPolygonConverter.supportsImplicitPolygonType(type);
    }

    /**
     * @param type The property type
     * @return The implicit converter class for this geospatial type
     */
    public static Class<?> resolveImplicitGeoConverterClass(Class<?> type) {
        if (MongoGeoMultiPointConverter.supportsImplicitMultiPointType(type)) {
            return MongoGeoMultiPointConverter.class;
        }
        if (MongoGeoGeometryCollectionConverter.supportsImplicitGeometryCollectionType(type)) {
            return MongoGeoGeometryCollectionConverter.class;
        }
        if (MongoGeoMultiLineStringConverter.supportsImplicitMultiLineStringType(type)) {
            return MongoGeoMultiLineStringConverter.class;
        }
        if (MongoGeoMultiPolygonConverter.supportsImplicitMultiPolygonType(type)) {
            return MongoGeoMultiPolygonConverter.class;
        }
        if (MongoGeoLineStringConverter.supportsImplicitLineStringType(type)) {
            return MongoGeoLineStringConverter.class;
        }
        if (MongoGeoPolygonConverter.supportsImplicitPolygonType(type)) {
            return MongoGeoPolygonConverter.class;
        }
        return MongoGeoPointConverter.class;
    }

    /**
     * @param type The property type
     * @return Whether this type is supported for Mongo geospatial indexing
     */
    public static boolean supportsGeoIndexedPropertyType(Class<?> type) {
        return Map.class.isAssignableFrom(type) || supportsImplicitGeoType(type);
    }
}
