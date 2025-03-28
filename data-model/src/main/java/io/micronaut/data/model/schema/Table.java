package io.micronaut.data.model.schema;

import io.micronaut.core.annotation.Internal;

import java.util.List;

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
