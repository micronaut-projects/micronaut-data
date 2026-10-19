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
package io.micronaut.data.jdbc.mapper;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.convert.ConversionContext;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.type.Argument;
import io.micronaut.data.model.JsonDataType;
import io.micronaut.data.model.runtime.convert.AttributeConverter;
import io.micronaut.data.runtime.mapper.ResultReader;
import io.micronaut.data.runtime.mapper.sql.SqlJsonColumnReader;
import org.jspecify.annotations.Nullable;

import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * What a {@link GeneratedRowMapper} needs beyond the result set: the conversion service, for the values a getter
 * does not return in the property type; the attribute converter of every column that has one, with the
 * conversion context of its property; and, for a JSON column, the reader of the query with the column's name,
 * JSON type and argument. The per-column facts are resolved once per plan by
 * {@link FlatEntityMappingPlan#createContext}, the JSON reader is the query's.
 *
 * @since 5.3.0
 */
@Internal
public final class GeneratedRowMapperContext {

    private final ConversionService conversionService;
    private final FlatEntityMappingPlan.Columns columns;
    @Nullable
    private final SqlJsonColumnReader<ResultSet> jsonColumnReader;
    @Nullable
    private final ResultReader<ResultSet, String> resultReader;

    GeneratedRowMapperContext(ConversionService conversionService,
                              FlatEntityMappingPlan.Columns columns,
                              @Nullable SqlJsonColumnReader<ResultSet> jsonColumnReader,
                              @Nullable ResultReader<ResultSet, String> resultReader) {
        this.conversionService = conversionService;
        this.columns = columns;
        this.jsonColumnReader = jsonColumnReader;
        this.resultReader = resultReader;
    }

    /**
     * @return The conversion service
     */
    public ConversionService conversionService() {
        return conversionService;
    }

    /**
     * Applies the attribute converter of a column to the value read, as the general mapper does, {@code null}
     * included.
     *
     * @param column         The index of the column in {@link GeneratedRowMapper#columnNames()}
     * @param persistedValue The value read with the typed getter of the persisted type
     * @return The entity value
     */
    @Nullable
    public Object convertToEntityValue(int column, @Nullable Object persistedValue) {
        AttributeConverter<Object, Object> converter = columns.converters[column];
        ConversionContext context = columns.conversionContexts[column];
        if (converter == null || context == null) {
            throw new IllegalStateException("No attribute converter was resolved for column " + column);
        }
        return converter.convertToEntityValue(persistedValue, context);
    }

    /**
     * Reads a JSON column the way the general mapper reads it: through the query's JSON column reader, by column
     * name, with the property's JSON type and argument; without a reader, as the string the column holds,
     * converted to the property type.
     *
     * @param column  The index of the column in {@link GeneratedRowMapper#columnNames()}
     * @param rs      The result set
     * @param ordinal The ordinal of the column
     * @return The entity value
     * @throws SQLException If the column cannot be read
     */
    @Nullable
    public Object readJson(int column, ResultSet rs, int ordinal) throws SQLException {
        Argument<?> argument = columns.arguments[column];
        JsonDataType jsonDataType = columns.jsonDataTypes[column];
        if (argument == null || jsonDataType == null) {
            throw new IllegalStateException("No JSON type was resolved for column " + column);
        }
        if (jsonColumnReader != null && resultReader != null) {
            return jsonColumnReader.readJsonColumn(resultReader, rs, columns.names[column], jsonDataType, argument);
        }
        return GeneratedRowMapperSupport.convert(rs.getString(ordinal), argument.getType(), conversionService);
    }
}
