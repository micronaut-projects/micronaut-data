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
package io.micronaut.data.jdbc.notification.oracle;

import io.micronaut.data.jdbc.operations.DefaultJdbcRepositoryOperations;
import io.micronaut.data.model.DataType;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.model.query.builder.sql.SqlQueryBuilder;
import io.micronaut.data.model.runtime.PreparedQuery;
import io.micronaut.data.model.runtime.QueryParameterBinding;
import io.micronaut.data.model.runtime.RuntimePersistentEntity;
import io.micronaut.data.model.runtime.StoredQuery;
import io.micronaut.data.runtime.operations.internal.sql.DefaultSqlPreparedQuery;
import io.micronaut.data.runtime.operations.internal.sql.DefaultSqlStoredQuery;
import io.micronaut.data.runtime.query.internal.BasicStoredQuery;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * Reloads a changed entity by its Oracle {@code ROWID}.
 *
 * <p>Oracle notifications identify changed rows by ROWID rather than supplying entity state. This
 * class caches the entity metadata and generated query infrastructure needed to bind that ROWID
 * and load the current entity before the listener method is invoked.</p>
 *
 * @param <E> The entity type.
 */
final class OracleChangeListenerEntityLoader<E> {
    private final DefaultJdbcRepositoryOperations operations;
    private final Class<E> entityType;
    private final RuntimePersistentEntity<E> entity;
    private final String query;
    private final SqlQueryBuilder queryBuilder = new SqlQueryBuilder(Dialect.ORACLE);

    @SuppressWarnings("unchecked")
    OracleChangeListenerEntityLoader(DefaultJdbcRepositoryOperations operations, Class<?> entityType, String query) {
        this.operations = operations;
        this.entityType = (Class<E>) entityType;
        this.entity = operations.getEntity(this.entityType);
        this.query = query;
    }

    @Nullable E reload(String rowId) {
        StoredQuery<E, E> storedQuery = new BasicStoredQuery<>(query, new String[0],
            List.of(new RowIdQueryParameterBinding(rowId)), entityType, entityType, StoredQuery.OperationType.QUERY);
        DefaultSqlStoredQuery<E, E> sqlStoredQuery = new DefaultSqlStoredQuery<>(storedQuery, entity, queryBuilder,
            operations.getConversionService());
        PreparedQuery<E, E> preparedQuery = new DefaultSqlPreparedQuery<>(sqlStoredQuery);
        return operations.findOne(preparedQuery);
    }

    private record RowIdQueryParameterBinding(String rowId) implements QueryParameterBinding {
        @Override
        public DataType getDataType() {
            return DataType.STRING;
        }

        @Override
        public Object getValue() {
            return rowId;
        }
    }
}
