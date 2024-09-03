package io.micronaut.data.query.builder.custom;

import io.micronaut.core.annotation.Internal;
import io.micronaut.data.model.query.builder.sql.SqlQueryBuilder;

/**
 * Custom query builder without actual significant change in implementation, needed
 * for some test cases with custom repositories.
 */
@Internal
public final class CustomQueryBuilder extends SqlQueryBuilder {
}
