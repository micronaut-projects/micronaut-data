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
package io.micronaut.data.runtime.operations.internal.sql;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.type.Argument;
import io.micronaut.data.model.PersistentEntity;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.model.runtime.InsertBatchOperation;
import io.micronaut.data.model.runtime.RuntimePersistentEntity;
import io.micronaut.data.model.runtime.StoredQuery;
import org.jspecify.annotations.Nullable;
import org.reactivestreams.Publisher;

import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.CompletionStage;

/**
 * Shared SQL batch-operation capability checks.
 *
 * <p>Micronaut Data uses {@link Dialect#MYSQL} for both MySQL and MariaDB SQL generation. Runtime
 * driver behavior can still diverge, so runtime capability checks stay internal and separate from
 * the public dialect enum.</p>
 *
 * @since 5.1.0
 */
@Internal
public final class SqlBatchSupport {

    private static final String MARIADB_PRODUCT_NAME = "MARIADB";
    private static final String MYSQL_PRODUCT_NAME = "MYSQL";

    private SqlBatchSupport() {
    }

    /**
     * Resolves whether insert batching is supported for a stored query.
     *
     * @param persistentEntity The persistent entity
     * @param sqlStoredQuery The SQL stored query
     * @return {@code true} if insert batching is supported
     */
    public static boolean isSupportsBatchInsert(PersistentEntity persistentEntity,
                                                SqlStoredQuery<?, ?> sqlStoredQuery) {
        if (sqlStoredQuery.getOperationType() == StoredQuery.OperationType.INSERT_RETURNING) {
            return false;
        }
        return isSupportsBatchInsert(persistentEntity, sqlStoredQuery.getDialect());
    }

    /**
     * Resolves whether insert batching is supported for a SQL dialect.
     *
     * @param persistentEntity The persistent entity
     * @param dialect The SQL dialect
     * @return {@code true} if insert batching is supported
     */
    public static boolean isSupportsBatchInsert(PersistentEntity persistentEntity,
                                                Dialect dialect) {
        return switch (dialect) {
            case SQL_SERVER -> false;
            // Preserve the generic SQL/R2DBC rule for dialects where generated IDs cannot be
            // assumed to come back reliably from a batch insert.
            case MYSQL, ORACLE -> hasNonGeneratedIdentity(persistentEntity);
            default -> true;
        };
    }

    /**
     * Resolves whether a JDBC connection can use batch inserts.
     *
     * @param persistentEntity The persistent entity
     * @param dialect The SQL dialect
     * @param databaseProductName The concrete database product name if available
     * @param driverName The JDBC driver name if available
     * @param supportsBatchUpdates Whether the driver reports batch-update support
     * @param supportsGetGeneratedKeys Whether the driver reports generated-key support
     * @param requiresGeneratedKeys Whether generated keys are needed back from the batch
     * @return {@code true} if JDBC can use the batch path
     */
    public static boolean isSupportsJdbcBatchInsert(PersistentEntity persistentEntity,
                                                    Dialect dialect,
                                                    @Nullable String databaseProductName,
                                                    @Nullable String driverName,
                                                    @Nullable Boolean supportsBatchUpdates,
                                                    @Nullable Boolean supportsGetGeneratedKeys,
                                                    boolean requiresGeneratedKeys) {
        if (dialect == Dialect.SQLITE) {
            // SQLite JDBC does not reliably expose one generated key per row for executeBatch(),
            // so entity-returning saveAll operations must fall back to per-row inserts.
            return !requiresGeneratedKeys;
        }
        if (dialect == Dialect.MYSQL) {
            if (isMariaDb(databaseProductName, driverName)) {
                // MariaDB reports generated-key support generally, but complete generated keys for
                // batched multi-value inserts depend on driver options. Only batch when the caller
                // does not need generated keys back.
                return !requiresGeneratedKeys && Boolean.TRUE.equals(supportsBatchUpdates);
            }
            if (isMySql(databaseProductName, driverName)) {
                // MySQL Connector/J can return generated keys for JDBC batches when both metadata
                // capabilities are reported, so generated-key batches can be enabled there.
                return Boolean.TRUE.equals(supportsBatchUpdates)
                    && (!requiresGeneratedKeys || Boolean.TRUE.equals(supportsGetGeneratedKeys));
            }
        }
        return isSupportsBatchInsert(persistentEntity, dialect);
    }

    /**
     * Resolves whether a batch insert operation needs generated keys to be returned.
     *
     * @param persistentEntity The runtime persistent entity
     * @param operation The insert batch operation
     * @return {@code true} if generated keys must be requested
     */
    public static boolean requiresBatchGeneratedKeys(RuntimePersistentEntity<?> persistentEntity,
                                                     InsertBatchOperation<?> operation) {
        if (!persistentEntity.hasIdentity() || !persistentEntity.getIdentity().isGenerated()) {
            return false;
        }
        if (persistentEntity.cascadesPersist() || persistentEntity.hasPostPersistEventListeners()) {
            return true;
        }
        return returnsEntities(operation.getResultArgument());
    }

    private static boolean hasNonGeneratedIdentity(PersistentEntity persistentEntity) {
        return persistentEntity.hasIdentity() && !persistentEntity.getIdentity().isGenerated();
    }

    private static boolean isMariaDb(@Nullable String databaseProductName, @Nullable String driverName) {
        return containsIgnoreCase(databaseProductName, MARIADB_PRODUCT_NAME)
            || containsIgnoreCase(driverName, MARIADB_PRODUCT_NAME);
    }

    private static boolean isMySql(@Nullable String databaseProductName, @Nullable String driverName) {
        return containsIgnoreCase(databaseProductName, MYSQL_PRODUCT_NAME)
            || containsIgnoreCase(driverName, MYSQL_PRODUCT_NAME);
    }

    private static boolean containsIgnoreCase(@Nullable String value, String expected) {
        if (value == null) {
            return false;
        }
        return value.toUpperCase(Locale.ENGLISH).contains(expected);
    }

    private static boolean returnsEntities(Argument<?> resultArgument) {
        Argument<?> unwrapped = unwrapResultArgument(resultArgument);
        Class<?> type = unwrapped.getType();
        if (unwrapped.isVoid() || type == Void.class || type == void.class) {
            return false;
        }
        if (type.isPrimitive()) {
            return false;
        }
        return !Number.class.isAssignableFrom(type);
    }

    private static Argument<?> unwrapResultArgument(Argument<?> argument) {
        Argument<?> current = argument;
        while (shouldUnwrap(current)) {
            current = current.getFirstTypeVariable().orElse(Argument.OBJECT_ARGUMENT);
        }
        return current;
    }

    private static boolean shouldUnwrap(Argument<?> argument) {
        Class<?> type = argument.getType();
        if (type.isArray()) {
            return false;
        }
        return Iterable.class.isAssignableFrom(type)
            || Publisher.class.isAssignableFrom(type)
            || CompletionStage.class.isAssignableFrom(type)
            || Optional.class.isAssignableFrom(type);
    }
}
