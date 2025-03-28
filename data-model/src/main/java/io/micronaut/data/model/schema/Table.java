package io.micronaut.data.model.schema;

import io.micronaut.core.annotation.Internal;

import java.util.List;

/**
 * The table definition. The information is extracted from the {@link io.micronaut.data.model.PersistentEntity}.
 *
 * @param schema The schema name, not required
 * @param name The table name
 * @param primaryKeyColumns The list of primary key columns, can be null or empty
 * @param columns The list of columns
 * @param sequences The list of table sequences, can be null or empty
 */
@Internal
public record Table(
    String schema,
    String name,
    List<Column> primaryKeyColumns,
    List<Column> columns,
    List<Sequence> sequences
) {
    public Table(String schema, String name, List<Column> primaryKeyColumns, List<Column> columns) {
        this(schema, name, primaryKeyColumns, columns, null);
    }
}
