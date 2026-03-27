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
import org.jspecify.annotations.Nullable;
import io.micronaut.data.model.runtime.convert.AttributeConverter;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Converts {@link MongoGeoPoint} to and from a GeoJSON-like persisted map.
 *
 * @author radovanradic
 * @since 5.0.0
 */
public final class MongoGeoPointConverter implements AttributeConverter<MongoGeoPointLike, Map<String, Object>> {

    @Override
    public @Nullable Map<String, Object> convertToPersistedValue(@Nullable MongoGeoPointLike entityValue, @NonNull io.micronaut.core.convert.ConversionContext context) {
        if (entityValue == null) {
            return null;
        }
        Map<String, Object> point = new LinkedHashMap<>();
        point.put("type", "Point");
        point.put("coordinates", List.of(entityValue.x(), entityValue.y()));
        return point;
    }

    @Override
    public @Nullable MongoGeoPointLike convertToEntityValue(@Nullable Map<String, Object> persistedValue, @NonNull io.micronaut.core.convert.ConversionContext context) {
        if (persistedValue == null) {
            return null;
        }
        Object coordinates = persistedValue.get("coordinates");
        if (coordinates instanceof List<?> list && list.size() == 2) {
            Object x = list.get(0);
            Object y = list.get(1);
            if (x instanceof Number xNumber && y instanceof Number yNumber) {
                return new MongoGeoPoint(xNumber.doubleValue(), yNumber.doubleValue());
            }
        }
        throw new IllegalArgumentException("Invalid persisted MongoGeoPoint value: " + persistedValue);
    }
}
