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
package io.micronaut.data.runtime.convert;

import io.micronaut.core.annotation.Internal;
import io.micronaut.data.model.runtime.convert.AttributeConverter;
import io.micronaut.data.model.runtime.convert.DatabaseType;
import io.micronaut.data.model.runtime.convert.GeometryJsonConverter;
import io.micronaut.data.model.runtime.convert.GeometryWktConverter;

/**
 * Selects converters to match the spatial representation used by SQL operations,
 * without changing converters cached in shared entity metadata.
 */
@Internal
public final class SqlAttributeConverterResolver {

    private static final GeometryWktConverter WKT_CONVERTER = new GeometryWktConverter();

    private SqlAttributeConverterResolver() {
    }

    /**
     * Resolve the configured converter for the database executing the operation.
     *
     * @param converter The configured converter
     * @param databaseType The database executing the operation
     * @param <X> The entity value type
     * @param <Y> The persisted value type
     * @return The WKT converter for SQL Server's GeoJSON fallback, or the configured converter
     */
    @SuppressWarnings("unchecked")
    public static <X, Y> AttributeConverter<X, Y> resolve(AttributeConverter<X, Y> converter, DatabaseType databaseType) {
        if (databaseType == DatabaseType.SQL_SERVER && converter instanceof GeometryJsonConverter) {
            return (AttributeConverter<X, Y>) WKT_CONVERTER;
        }
        return converter;
    }
}
