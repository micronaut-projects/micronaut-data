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
 * <p>MySQL and MariaDB are told apart by the product and driver names reported by JDBC metadata.
 * The {@code supportsBatchUpdates}/{@code supportsGetGeneratedKeys} metadata flags are only used to
 * keep drivers that do not advertise batch support on the conservative path; both MySQL
 * Connector/J and the MariaDB driver report them unconditionally, so they cannot be relied on to
 * describe batched generated-key behavior.</p>
 *
 * @since 5.2.0
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
        if (!dialect.allowBatch()) {
            return false;
        }
        return switch (dialect) {
            // Preserve the generic SQL/R2DBC rule: MySQL and Oracle only batch entities with a
            // non-generated identity, keeping generated and identity-less entities conservative.
            case MYSQL, ORACLE -> hasNonGeneratedIdentity(persistentEntity);
            default -> true;
        };
    }

    /**
     * Resolves how a JDBC insert batch operation has to be executed.
     *
     * <p>Only the MySQL dialect can resolve to {@link JdbcBatchInsertMode#BATCH_WITHOUT_GENERATED_KEYS}.
     * Every other dialect keeps its previous behavior of reading generated keys back whenever the
     * entity has a generated identity.</p>
     *
     * @param persistentEntity The persistent entity
     * @param dialect The SQL dialect
     * @param metadata The JDBC metadata of the connection
     * @param requiresGeneratedKeys Whether generated keys are needed back from the batch
     * @return The batch insert mode
     */
    public static JdbcBatchInsertMode resolveJdbcBatchInsertMode(PersistentEntity persistentEntity,
                                                                 Dialect dialect,
                                                                 JdbcBatchMetadata metadata,
                                                                 boolean requiresGeneratedKeys) {
        if (!dialect.allowBatch()) {
            return JdbcBatchInsertMode.FALLBACK;
        }
        if (dialect == Dialect.MYSQL && persistentEntity.hasIdentity()) {
            boolean supportsBatchUpdates = Boolean.TRUE.equals(metadata.supportsBatchUpdates());
            if (metadata.isMariaDb()) {
                // MariaDB reports generated-key support generally, but complete generated keys for
                // batched multi-value inserts depend on driver options. Only batch when the caller
                // does not need generated keys back.
                if (requiresGeneratedKeys) {
                    return JdbcBatchInsertMode.FALLBACK;
                }
                if (supportsBatchUpdates) {
                    return JdbcBatchInsertMode.BATCH_WITHOUT_GENERATED_KEYS;
                }
            } else if (metadata.isMySql() && supportsBatchUpdates) {
                // MySQL Connector/J can return generated keys for JDBC batches, so generated-key
                // batches can be enabled there.
                if (!requiresGeneratedKeys || Boolean.TRUE.equals(metadata.supportsGetGeneratedKeys())) {
                    return JdbcBatchInsertMode.BATCH;
                }
                return JdbcBatchInsertMode.FALLBACK;
            }
        }
        return isSupportsBatchInsert(persistentEntity, dialect) ? JdbcBatchInsertMode.BATCH : JdbcBatchInsertMode.FALLBACK;
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

    private static boolean containsIgnoreCase(@Nullable String value, String expected) {
        if (value == null) {
            return false;
        }
        return value.toUpperCase(Locale.ENGLISH).contains(expected);
    }

    private static boolean returnsEntities(Argument<?> resultArgument) {
        Argument<?> unwrapped = unwrapResultArgument(resultArgument);
        Class<?> type = unwrapped.getType();
        if (unwrapped.isVoid() || type == Void.class || type == void.class || type == Boolean.class) {
            return false;
        }
        if (type.isArray()) {
            Class<?> componentType = type.getComponentType();
            if (componentType.isPrimitive()
                || Number.class.isAssignableFrom(componentType)
                || componentType == Boolean.class) {
                return false;
            }
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

    /**
     * How a JDBC insert batch operation has to be executed.
     *
     * @since 5.2.0
     */
    public enum JdbcBatchInsertMode {

        /**
         * The entities have to be inserted one by one.
         */
        FALLBACK,

        /**
         * The entities can be inserted using a JDBC batch, reading generated keys back when the
         * entity has a generated identity.
         */
        BATCH,

        /**
         * The entities can be inserted using a JDBC batch, but generated keys must not be requested.
         */
        BATCH_WITHOUT_GENERATED_KEYS;

        /**
         * @return {@code true} if a JDBC batch can be used
         */
        public boolean isBatch() {
            return this != FALLBACK;
        }

        /**
         * @return {@code true} if generated keys can be read back from the batch
         */
        public boolean isReadGeneratedKeys() {
            return this == BATCH;
        }
    }

    /**
     * The JDBC metadata used to resolve batch insert capabilities. Any value can be {@code null}
     * when the metadata could not be read.
     *
     * @param databaseProductName The database product name
     * @param databaseProductVersion The database product version
     * @param driverName The JDBC driver name
     * @param supportsBatchUpdates Whether the driver reports batch-update support
     * @param supportsGetGeneratedKeys Whether the driver reports generated-key support
     * @since 5.2.0
     */
    public record JdbcBatchMetadata(@Nullable String databaseProductName,
                                    @Nullable String databaseProductVersion,
                                    @Nullable String driverName,
                                    @Nullable Boolean supportsBatchUpdates,
                                    @Nullable Boolean supportsGetGeneratedKeys) {

        /**
         * The metadata used when the JDBC metadata could not be read.
         */
        public static final JdbcBatchMetadata UNKNOWN = new JdbcBatchMetadata(null, null, null, null, null);

        /**
         * @return {@code true} if the connection is known to target a MariaDB server
         */
        public boolean isMariaDb() {
            // MySQL Connector/J reports "MySQL" as the product name for a MariaDB server, which
            // only identifies itself in the version string, so the version is checked as well.
            return containsIgnoreCase(databaseProductName, MARIADB_PRODUCT_NAME)
                || containsIgnoreCase(databaseProductVersion, MARIADB_PRODUCT_NAME)
                || containsIgnoreCase(driverName, MARIADB_PRODUCT_NAME);
        }

        /**
         * @return {@code true} if the connection is known to target a MySQL server
         */
        public boolean isMySql() {
            return containsIgnoreCase(databaseProductName, MYSQL_PRODUCT_NAME)
                || containsIgnoreCase(driverName, MYSQL_PRODUCT_NAME);
        }
    }
}
