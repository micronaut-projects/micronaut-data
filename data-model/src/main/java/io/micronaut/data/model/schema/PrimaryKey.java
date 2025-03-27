package io.micronaut.data.model.schema;

import java.util.List;

public record PrimaryKey(
    Table table,
    List<Column> columns
) implements Constraint {
}
