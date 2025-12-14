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
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.convert.ConversionContext;
import io.micronaut.data.model.query.builder.sql.Dialect;

import java.util.OptionalInt;

/**
 * SQL-specialized attribute converter contract.
 *
 * <p>Extends the generic {@link AttributeConverter} with helpers required by SQL mappers:</p>
 * <ul>
 *   <li>Reading values from a result set via a {@link ConverterResultReader}</li>
 *   <li>Providing vendor-specific SQL column definitions during schema generation</li>
 * </ul>
 *
 * <p>Implementations adapt between the entity-facing value and the persisted JDBC/R2DBC value.</p>
 *
 * @param <X> The entity value type
 * @param <Y> The persisted value type
 * @author Nemanja Mikic
 * @since 5.0.0
 */
@Indexed(SqlAttributeConverter.class)
public interface SqlAttributeConverter<X, Y> extends AttributeConverter<X, Y> {
    /**
     * Read a column value from a native result set via the provided converter-aware reader.
     *
     * <p>The returned value should be of the persisted type ({@code Y}) expected by
     * {@link #convertToEntityValue(Object, ConversionContext)}.</p>
     *
     * @param conversionContext the conversion context (may carry dialect and argument metadata)
     * @param cr the converter-aware result reader for the underlying driver
     * @param resultSet the native row/result set object (e.g. {@code java.sql.ResultSet}, R2DBC {@code io.r2dbc.spi.Row}, etc.)
     * @param columnName the column identifier (index or name) as required by the reader
     * @return the raw persisted value to be converted into the entity value, or {@code null} if the column is SQL NULL
     * @since 5.0.0
     */
    @Nullable
    Object readFromResultSet(ConversionContext conversionContext,
                             ConverterResultReader<Object, Object> cr,
                             Object resultSet,
                             Object columnName);

    /**
     * Return a vendor-specific SQL column definition for this attribute, or {@code null} to delegate to default mapping.
     *
     * <p>Implementations may use the optional {@code len} to express length/dimensions, and the {@code dialect}
     * to render a proper vendor string (e.g., {@code VECTOR(3,FLOAT64)} on Oracle, {@code vector(3)} on Postgres).</p>
     *
     * @param len optional length/dimensions hint
     * @param dialect the SQL dialect for which a definition should be produced
     * @return the SQL column definition string, or {@code null} to allow default resolution
     * @since 5.0.0
     */
    @Nullable
    String getColumnDefinition(OptionalInt len, Dialect dialect);
}
