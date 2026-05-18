/*
 * Copyright 2017-2025 original authors
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
package io.micronaut.data.model.runtime.convert;

import io.micronaut.core.annotation.Indexed;
import io.micronaut.core.annotation.Experimental;
import org.jspecify.annotations.Nullable;
import io.micronaut.core.convert.ConversionContext;
import io.micronaut.data.runtime.mapper.ResultReader;

/**
 * ResultReader-aware attribute converter contract for SQL-like stores (JDBC/R2DBC).
 *
 * <p>Extends the generic {@link AttributeConverter} with a helper to read raw column values via a
 * {@link ResultReader}. The returned raw value is then converted to the entity value using
 * {@link #convertToEntityValue(Object, ConversionContext)}.</p>
 *
 * <p>The {@link ConversionContext} may carry additional metadata and, when it is an instance of
 * {@link DatabaseTypeConversionContext}, it exposes the database type
 * associated with the current operation.</p>
 *
 * <p>Implementations adapt between the entity-facing type {@code X} and the persisted driver type {@code Y}.</p>
 *
 * @param <X> The entity value type
 * @param <Y> The persisted value type (driver/native type)
 * @author Nemanja Mikic
 * @since 5.0.0
 */
@Indexed(ResultReaderAttributeConverter.class)
@Experimental
public interface ResultReaderAttributeConverter<X, Y> extends AttributeConverter<X, Y> {
    /**
     * Read a column value from a native result set via the provided converter-aware reader.
     *
     * <p>The returned value should be of the persisted type ({@code Y}) expected by
     * {@link #convertToEntityValue(Object, ConversionContext)}.</p>
     *
     * <p>The result set and column identifier types are tied to the reader generics.</p>
     *
     * @param <R> The native result set type (e.g. {@code java.sql.ResultSet} or {@code io.r2dbc.spi.Row})
     * @param <I> The column identifier type expected by the reader (e.g. {@code Integer} index or {@code String} name)
     * @param conversionContext the conversion context (may carry dialect and argument metadata)
     * @param reader the converter-aware result reader for the underlying driver
     * @param resultSet the native row/result set object (e.g. {@code java.sql.ResultSet}, R2DBC {@code io.r2dbc.spi.Row}, etc.)
     * @param columnName the column identifier (index or name) as required by the reader
     * @return the raw persisted value to be converted into the entity value, or {@code null} if the column is SQL NULL
     * @since 5.0.0
     */
    @Nullable
    <R, I> Object readFromResultSet(DatabaseTypeConversionContext conversionContext,
                                    ResultReader<R, I> reader,
                                    R resultSet,
                                    I columnName);
}
