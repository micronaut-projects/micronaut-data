package io.micronaut.data.cosmos.operations;

import io.micronaut.core.annotation.Internal;
import io.micronaut.data.model.query.builder.sql.SqlQueryBuilder;
import io.micronaut.data.model.runtime.RuntimePersistentEntity;
import io.micronaut.data.model.runtime.StoredQuery;
import io.micronaut.data.runtime.operations.internal.sql.DefaultSqlStoredQuery;

/**
 * Cosmos Azure implementation fo {@link StoredQuery}.
 *
 * @param <E> the entity type
 * @param <R> the result type
 */
@Internal
final class CosmosSqlStoredQuery<E, R> extends DefaultSqlStoredQuery<E, R> {

    private final String update;

    /**
     * @param storedQuery             The stored query
     * @param runtimePersistentEntity The persistent entity
     * @param queryBuilder            The query builder
     * @param update                  The update statement. In this case list of properties to update via API.
     */
    public CosmosSqlStoredQuery(StoredQuery<E, R> storedQuery, RuntimePersistentEntity<E> runtimePersistentEntity, SqlQueryBuilder queryBuilder,
                                String update) {
        super(storedQuery, runtimePersistentEntity, queryBuilder);
        this.update = update;
    }

    /**
     * @return the update query for Cosmos Azure. In this implementation, list of properties to be updated via Cosmos API.
     */
    public String getUpdate() {
        return update;
    }
}
