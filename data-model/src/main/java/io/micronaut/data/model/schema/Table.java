package io.micronaut.data.model.schema;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.util.StringUtils;
import io.micronaut.data.model.query.builder.sql.Dialect;

import java.util.ArrayList;
import java.util.List;

public record Table(
    String schema,
    String name,
    String unescapedName,
    @Nullable
    PrimaryKey primaryKey,
    List<Column> columns,
    Dialect dialect,
    boolean escape
) {
    public String[] buildCreateStatements() {
        List<String> statements = new ArrayList<>();
        if (StringUtils.isNotEmpty(schema)) {
            statements.add(String.format("CREATE SCHEMA %s;", schema));
        }
        // TODO: Other statements
        return statements.toArray(new String[0]);
    }
}
