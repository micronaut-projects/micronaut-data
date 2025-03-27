package io.micronaut.data.model.schema;

import java.util.List;

public interface Constraint {

    Table table();

    List<Column> columns();
}
