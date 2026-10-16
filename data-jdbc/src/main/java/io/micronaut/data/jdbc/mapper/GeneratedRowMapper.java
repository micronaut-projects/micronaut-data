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
import io.micronaut.core.convert.ConversionService;

import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * A row mapper the annotation processor generates for a {@link io.micronaut.data.annotation.MappedEntity}: reads
 * one row of a {@link ResultSet} into the entity with the typed getters by column index, without reflection or
 * bean introspection. A generated mapper is named {@code <Entity>$JdbcRowMapper}, lives in the entity's package
 * and is registered as a service of this interface, see {@link GeneratedRowMappers}.
 *
 * <p>The columns are the ones a query returning the entity without joins selects: the identity, the version, the
 * persistent properties, the leaves of embedded values and the identity of single ended associations, named by
 * the entity's naming strategy. The mapper is stateless: the caller resolves the ordinal of each column of
 * {@link #columnNames()} in its result set once and passes them to every {@link #map} call.</p>
 *
 * <p>The values a generated mapper produces are the ones {@link FlatEntityResultSetMapper} produces for the same
 * row, and that mapper is what uses it; the annotation processor generates no mapper for an entity it cannot
 * read exactly that way, which then keeps the flat or the general mapper.</p>
 *
 * @param <T> The entity type
 * @since 5.3.0
 */
@Internal
public interface GeneratedRowMapper<T> {

    /**
     * The suffix of the generated class name.
     */
    String CLASS_NAME_SUFFIX = "$JdbcRowMapper";

    /**
     * @return The entity type
     */
    Class<T> entityType();

    /**
     * @return The column name of every value the mapper reads, in the order of the ordinals {@link #map} expects
     */
    String[] columnNames();

    /**
     * Reads the current row.
     *
     * @param rs                The result set, positioned on a row
     * @param columns           The 1-based ordinal of every column of {@link #columnNames()}, in that order
     * @param conversionService The conversion service, for the values a getter doesn't return in the property type
     * @return The entity, declared as {@link Object} so that a generated implementation needs no bridge method
     * whatever generator wrote it
     * @throws SQLException If a column cannot be read
     */
    Object map(ResultSet rs, int[] columns, ConversionService conversionService) throws SQLException;
}
