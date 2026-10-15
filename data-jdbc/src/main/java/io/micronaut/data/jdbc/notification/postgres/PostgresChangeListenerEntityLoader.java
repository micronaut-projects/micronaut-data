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
package io.micronaut.data.jdbc.notification.postgres;

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
import java.util.Map;
import java.util.Set;

/**
 * Reloads a changed entity by its PostgreSQL primary-key values.
 *
 * <p>The loader caches an immutable stored-query template and ordered key bindings. Each reload
 * creates a prepared query with its own key values, allowing the template to be shared across
 * reloads. Notification key strings are converted to the mapped Java types when parameters
 * are bound.</p>
 *
 * @param <E> The entity type.
 */
final class PostgresChangeListenerEntityLoader<E> {
    private static final String[] EMPTY_EXPANDABLE_QUERY_PARTS = new String[0];

    private final JdbcRepositoryOperations operations;
    private final DefaultSqlStoredQuery<E, E> sqlStoredQuery;
    private final List<KeyBinding> bindings;
    private final Set<String> columns;

    /**
     * Creates an immutable stored-query template for loading the entity by primary key.
     *
     * @param operations the datasource operations used to load the entity
     * @param entityType the persistent entity class
     * @param query the generated primary-key reload query
     * @param bindings the validated key bindings in query parameter order
     */
    @SuppressWarnings("unchecked")
    PostgresChangeListenerEntityLoader(JdbcRepositoryOperations operations, Class<?> entityType,
                                       String query, List<KeyBinding> bindings) {
        this.operations = operations;
        this.bindings = List.copyOf(bindings);
        columns = Set.copyOf(this.bindings.stream().map(KeyBinding::column).toList());
        Class<E> resolvedEntityType = (Class<E>) entityType;
        RuntimePersistentEntity<E> entity = operations.getEntity(resolvedEntityType);
        StoredQuery<E, E> storedQuery = new BasicStoredQuery<>(query, EMPTY_EXPANDABLE_QUERY_PARTS,
            List.copyOf(this.bindings), resolvedEntityType, resolvedEntityType, StoredQuery.OperationType.QUERY);
        SqlQueryBuilder queryBuilder = new SqlQueryBuilder(Dialect.POSTGRES);
        sqlStoredQuery = new DefaultSqlStoredQuery<>(storedQuery, entity, queryBuilder, operations.getConversionService());
    }

    /**
     * @return the mapped primary-key column names expected in notification payloads
     */
    Set<String> columns() {
        return columns;
    }

    /**
     * Loads the current entity state using the notification's primary-key values.
     *
     * @param keys the primary-key values indexed by mapped column name
     * @return the current mapped entity, or {@code null} if no row is found
     * @throws IllegalArgumentException if the supplied key columns differ from the mapping
     */
    @Nullable E reload(Map<String, String> keys) {
        if (!columns.equals(keys.keySet())) {
            throw new IllegalArgumentException("Notification keys do not match the mapped primary key");
        }
        return operations.findOne(new PrimaryKeyPreparedQuery(keys));
    }

    /**
     * Supplies notification-specific primary-key values to the immutable stored-query template.
     */
    private final class PrimaryKeyPreparedQuery extends DefaultSqlPreparedQuery<E, E> {
        private final Map<String, String> keys;

        PrimaryKeyPreparedQuery(Map<String, String> keys) {
            super(PostgresChangeListenerEntityLoader.this.sqlStoredQuery);
            this.keys = Map.copyOf(keys);
        }

        @Override
        public void bindParameters(BindableParametersStoredQuery.Binder binder) {
            for (KeyBinding binding : bindings) {
                Object value = operations.getConversionService().convertRequired(keys.get(binding.column()), binding.type());
                binder.bindOne(binding, value);
            }
        }
    }

    /**
     * Describes the mapped column and Java/JDBC types for one primary-key parameter.
     *
     * @param column the mapped primary-key column name
     * @param type the Java type used to convert notification key strings
     * @param dataType the JDBC binding data type
     */
    record KeyBinding(String column, Class<?> type, DataType dataType) implements QueryParameterBinding {
        @Override
        public DataType getDataType() {
            return dataType;
        }
    }
}
