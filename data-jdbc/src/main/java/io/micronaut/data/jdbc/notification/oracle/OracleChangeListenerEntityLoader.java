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

import io.micronaut.data.jdbc.operations.JdbcRepositoryOperations;
import io.micronaut.data.model.DataType;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.model.query.builder.sql.SqlQueryBuilder;
import io.micronaut.data.model.runtime.QueryParameterBinding;
import io.micronaut.data.model.runtime.RuntimePersistentEntity;
import io.micronaut.data.model.runtime.StoredQuery;
import io.micronaut.data.runtime.operations.internal.query.BindableParametersStoredQuery;
import io.micronaut.data.runtime.operations.internal.sql.DefaultSqlPreparedQuery;
import io.micronaut.data.runtime.operations.internal.sql.DefaultSqlStoredQuery;
import io.micronaut.data.runtime.query.internal.BasicStoredQuery;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * Loads the current entity state using the {@code ROWID} from an Oracle notification.
 *
 * <p>The query reads the row that matches the {@code ROWID} when the reload runs, or returns
 * {@code null} if no row matches. See {@link OracleChangeEventMetadata} for the limitations of
 * using a {@code ROWID} to identify a changed entity.</p>
 *
 * <p>The loader shares one immutable stored-query template across reloads. Each reload creates
 * a prepared query with its own {@code ROWID} value, allowing concurrent reloads without sharing
 * parameter values.</p>
 *
 * @param <E> The entity type.
 */
final class OracleChangeListenerEntityLoader<E> {
    private static final String[] EMPTY_EXPANDABLE_QUERY_PARTS = new String[0];
    private static final QueryParameterBinding ROW_ID_BINDING = new RowIdQueryParameterBinding();

    private final JdbcRepositoryOperations operations;
    private final DefaultSqlStoredQuery<E, E> sqlStoredQuery;

    /**
     * Creates an immutable stored-query template for loading the entity by ROWID.
     *
     * @param operations the datasource operations used to load the entity
     * @param entityType the persistent entity class
     * @param query      the generated ROWID reload query
     */
    @SuppressWarnings("unchecked")
    OracleChangeListenerEntityLoader(JdbcRepositoryOperations operations, Class<?> entityType, String query) {
        this.operations = operations;
        Class<E> resolvedEntityType = (Class<E>) entityType;
        RuntimePersistentEntity<E> entity = operations.getEntity(resolvedEntityType);
        StoredQuery<E, E> storedQuery = new BasicStoredQuery<>(query, EMPTY_EXPANDABLE_QUERY_PARTS,
            List.of(ROW_ID_BINDING), resolvedEntityType, resolvedEntityType, StoredQuery.OperationType.QUERY);
        SqlQueryBuilder queryBuilder = new SqlQueryBuilder(Dialect.ORACLE);
        this.sqlStoredQuery = new DefaultSqlStoredQuery<>(storedQuery, entity, queryBuilder,
            operations.getConversionService());
    }

    /**
     * Loads the current entity state for a notification's ROWID.
     *
     * @param rowId the affected Oracle ROWID
     * @return the current mapped entity, or {@code null} if no row is found
     */
    @Nullable E reload(String rowId) {
        return operations.findOne(new RowIdPreparedQuery(rowId));
    }

    /**
     * Supplies the notification-specific ROWID to an otherwise immutable SQL query template.
     */
    private final class RowIdPreparedQuery extends DefaultSqlPreparedQuery<E, E> {
        private final String rowId;

        RowIdPreparedQuery(String rowId) {
            super(OracleChangeListenerEntityLoader.this.sqlStoredQuery);
            this.rowId = rowId;
        }

        @Override
        public void bindParameters(BindableParametersStoredQuery.Binder binder) {
            binder.bindOne(ROW_ID_BINDING, rowId);
        }
    }

    private static final class RowIdQueryParameterBinding implements QueryParameterBinding {
        @Override
        public DataType getDataType() {
            return DataType.STRING;
        }
    }
}
