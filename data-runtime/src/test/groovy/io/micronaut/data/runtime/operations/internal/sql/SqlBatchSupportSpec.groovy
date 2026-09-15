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
package io.micronaut.data.runtime.operations.internal.sql

import io.micronaut.core.type.Argument
import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.model.runtime.InsertBatchOperation
import io.micronaut.data.model.runtime.RuntimePersistentEntity
import io.micronaut.data.model.runtime.RuntimePersistentProperty
import io.micronaut.data.model.runtime.StoredQuery
import io.micronaut.data.runtime.operations.internal.sql.SqlBatchSupport.JdbcBatchInsertMode
import io.micronaut.data.runtime.operations.internal.sql.SqlBatchSupport.JdbcBatchMetadata
import org.reactivestreams.Publisher
import spock.lang.Specification
import spock.lang.Unroll

import java.util.Optional
import java.util.concurrent.CompletionStage

class SqlBatchSupportSpec extends Specification {
    void "mysql dialect stays conservative for generated identities by default"() {
        expect:
        !SqlBatchSupport.isSupportsBatchInsert(entityWithGeneratedId(), Dialect.MYSQL)
    }

    void "stored query preserves sqlite batch opt-out"() {
        given:
        SqlStoredQuery<?, ?> storedQuery = Stub(SqlStoredQuery) {
            getDialect() >> Dialect.SQLITE
            getOperationType() >> StoredQuery.OperationType.INSERT
        }

        expect:
        !SqlBatchSupport.isSupportsBatchInsert(entityWithGeneratedId(), storedQuery)
    }

    @Unroll
    void "jdbc mysql batches generated-id inserts with generated keys for #scenario"() {
        expect:
        resolve(entityWithGeneratedId(), Dialect.MYSQL, metadata(databaseProductName, null, driverName), true) == JdbcBatchInsertMode.BATCH

        where:
        scenario           | databaseProductName | driverName
        "product metadata" | "MySQL"             | "MySQL Connector/J"
        "driver metadata"  | null                | "MySQL Connector/J"
    }

    @Unroll
    void "jdbc mariadb falls back for generated-id inserts that need generated keys for #scenario"() {
        expect:
        resolve(entityWithGeneratedId(), Dialect.MYSQL, metadata(databaseProductName, databaseProductVersion, driverName), true) == JdbcBatchInsertMode.FALLBACK

        where:
        scenario                          | databaseProductName | databaseProductVersion | driverName
        "product metadata"                | "MariaDB"           | "10.11.2-MariaDB"      | "MariaDB Connector/J"
        "driver metadata"                 | null                | null                   | "MariaDB Connector/J"
        "mariadb behind mysql connector"  | "MySQL"             | "5.5.5-10.11.2-MariaDB" | "MySQL Connector/J"
    }

    @Unroll
    void "jdbc mariadb batches generated-id inserts without generated keys for #scenario"() {
        expect:
        resolve(entityWithGeneratedId(), Dialect.MYSQL, metadata(databaseProductName, databaseProductVersion, driverName), false) == JdbcBatchInsertMode.BATCH_WITHOUT_GENERATED_KEYS

        where:
        scenario                         | databaseProductName | databaseProductVersion  | driverName
        "product metadata"               | "MariaDB"           | "10.11.2-MariaDB"       | "MariaDB Connector/J"
        "mariadb behind mysql connector" | "MySQL"             | "5.5.5-10.11.2-MariaDB" | "MySQL Connector/J"
    }

    void "jdbc mysql batches generated-id inserts that do not need generated keys"() {
        expect:
        resolve(entityWithGeneratedId(), Dialect.MYSQL, metadata("MySQL", "8.4.0", "MySQL Connector/J"), false) == JdbcBatchInsertMode.BATCH
    }

    @Unroll
    void "jdbc mariadb #scenario"() {
        given:
        boolean requiresGeneratedKeys = SqlBatchSupport.requiresBatchGeneratedKeys(entityWithGeneratedId(), operation(resultArgument))

        expect:
        resolve(entityWithGeneratedId(), Dialect.MYSQL, mariaDbMetadata(), requiresGeneratedKeys) == mode

        where:
        scenario                                 | resultArgument              || mode
        "falls back for entity-returning saveAll" | Argument.listOf(TestEntity) || JdbcBatchInsertMode.FALLBACK
        "can batch for void insertAll"            | Argument.of(Void)           || JdbcBatchInsertMode.BATCH_WITHOUT_GENERATED_KEYS
        "can batch for count-returning insertAll" | Argument.of(Long)           || JdbcBatchInsertMode.BATCH_WITHOUT_GENERATED_KEYS
    }

    @Unroll
    void "jdbc mysql #scenario"() {
        given:
        boolean requiresGeneratedKeys = SqlBatchSupport.requiresBatchGeneratedKeys(entityWithGeneratedId(), operation(resultArgument))

        expect:
        resolve(entityWithGeneratedId(), Dialect.MYSQL, mySqlMetadata(), requiresGeneratedKeys) == JdbcBatchInsertMode.BATCH

        where:
        scenario                                  | resultArgument
        "can batch for entity-returning saveAll"  | Argument.listOf(TestEntity)
        "can batch for void insertAll"            | Argument.of(Void)
        "can batch for count-returning insertAll" | Argument.of(Long)
    }

    void "jdbc mysql does not batch generated-id inserts when generated keys are unsupported"() {
        given:
        def metadata = new JdbcBatchMetadata("MySQL", "8.4.0", "MySQL Connector/J", true, false)

        expect:
        resolve(entityWithGeneratedId(), Dialect.MYSQL, metadata, true) == JdbcBatchInsertMode.FALLBACK
    }

    void "jdbc mysql batches without generated keys when they are unsupported and not required"() {
        given:
        def metadata = new JdbcBatchMetadata("MySQL", "8.4.0", "MySQL Connector/J", true, false)

        expect:
        resolve(entityWithGeneratedId(), Dialect.MYSQL, metadata, false) == JdbcBatchInsertMode.BATCH_WITHOUT_GENERATED_KEYS
    }

    @Unroll
    void "jdbc mysql family does not batch generated-id inserts when batch updates are unsupported for #scenario"() {
        given:
        def metadata = new JdbcBatchMetadata(databaseProductName, null, driverName, supportsBatchUpdates, true)

        expect:
        resolve(entityWithGeneratedId(), Dialect.MYSQL, metadata, false) == JdbcBatchInsertMode.FALLBACK

        where:
        scenario             | databaseProductName | driverName            | supportsBatchUpdates
        "mysql reports false" | "MySQL"            | "MySQL Connector/J"   | false
        "mysql reports null"  | "MySQL"            | "MySQL Connector/J"   | null
        "mariadb reports false" | "MariaDB"        | "MariaDB Connector/J" | false
        "mariadb reports null"  | "MariaDB"        | "MariaDB Connector/J" | null
    }

    @Unroll
    void "jdbc mysql family keeps batching non-generated identities when batch updates are unsupported for #scenario"() {
        given:
        def metadata = new JdbcBatchMetadata(databaseProductName, null, driverName, false, true)

        expect:
        resolve(entityWithNonGeneratedId(), Dialect.MYSQL, metadata, false) == JdbcBatchInsertMode.BATCH

        where:
        scenario           | databaseProductName | driverName
        "mysql metadata"   | "MySQL"             | "MySQL Connector/J"
        "mariadb metadata" | "MariaDB"           | "MariaDB Connector/J"
    }

    @Unroll
    void "jdbc mysql family stays conservative for identity-less inserts for #scenario"() {
        expect:
        resolve(entityWithoutIdentity(), Dialect.MYSQL, metadata(databaseProductName, null, driverName), false) == JdbcBatchInsertMode.FALLBACK

        where:
        scenario           | databaseProductName | driverName
        "MariaDB metadata" | "MariaDB"           | "MariaDB Connector/J"
        "MySQL metadata"   | "MySQL"             | "MySQL Connector/J"
    }

    void "jdbc non-mysql dialect keeps batching for identity-less inserts"() {
        expect:
        resolve(entityWithoutIdentity(), Dialect.POSTGRES, metadata("PostgreSQL", "17.0", "PostgreSQL JDBC Driver"), false) == JdbcBatchInsertMode.BATCH
    }

    void "jdbc unknown mysql metadata stays conservative for generated identities"() {
        expect:
        resolve(entityWithGeneratedId(), Dialect.MYSQL, JdbcBatchMetadata.UNKNOWN, true) == JdbcBatchInsertMode.FALLBACK
        resolve(entityWithGeneratedId(), Dialect.MYSQL, JdbcBatchMetadata.UNKNOWN, false) == JdbcBatchInsertMode.FALLBACK
    }

    @Unroll
    void "jdbc mysql metadata does not change #dialect generated-id batch support"() {
        expect:
        resolve(entityWithGeneratedId(), dialect, mySqlMetadata(), requiresGeneratedKeys) == mode

        where:
        dialect            | requiresGeneratedKeys || mode
        Dialect.ORACLE     | true                  || JdbcBatchInsertMode.FALLBACK
        Dialect.ORACLE     | false                 || JdbcBatchInsertMode.FALLBACK
        Dialect.SQL_SERVER | true                  || JdbcBatchInsertMode.FALLBACK
        Dialect.SQL_SERVER | false                 || JdbcBatchInsertMode.FALLBACK
        // Other dialects keep reading generated keys back even when the caller does not need them
        Dialect.POSTGRES   | true                  || JdbcBatchInsertMode.BATCH
        Dialect.POSTGRES   | false                 || JdbcBatchInsertMode.BATCH
        Dialect.H2         | false                 || JdbcBatchInsertMode.BATCH
    }

    @Unroll
    void "generated keys are required for #scenario"() {
        expect:
        SqlBatchSupport.requiresBatchGeneratedKeys(entity(cascadesPersist, postPersist), operation(resultArgument)) == required

        where:
        scenario                         | cascadesPersist | postPersist | resultArgument                                            || required
        "entity lists"                   | false           | false       | Argument.listOf(TestEntity)                               || true
        "completion stage entity lists"  | false           | false       | Argument.of(CompletionStage, Argument.listOf(TestEntity)) || true
        "optional numeric returns"       | false           | false       | Argument.of(Optional, Argument.of(Long))                  || false
        "publisher numeric returns"      | false           | false       | Argument.of(Publisher, Argument.of(Long))                 || false
        "void returns"                   | false           | false       | Argument.of(Void)                                         || false
        "boxed boolean returns"          | false           | false       | Argument.of(Boolean)                                      || false
        "primitive count arrays"         | false           | false       | Argument.of(long[].class)                                 || false
        "boxed count arrays"             | false           | false       | Argument.of(Long[].class)                                 || false
        "boxed boolean arrays"           | false           | false       | Argument.of(Boolean[].class)                              || false
        "entity arrays"                  | false           | false       | Argument.of(TestEntity[].class)                           || true
        "numeric returns"                | false           | false       | Argument.of(Long)                                         || false
        "primitive numeric returns"      | false           | false       | Argument.of(Long.TYPE)                                    || false
        "post persist listeners"         | false           | true        | Argument.of(Long)                                         || true
        "cascade persist associations"   | true            | false       | Argument.of(Void)                                         || true
    }

    private static JdbcBatchInsertMode resolve(RuntimePersistentEntity<?> entity,
                                               Dialect dialect,
                                               JdbcBatchMetadata metadata,
                                               boolean requiresGeneratedKeys) {
        SqlBatchSupport.resolveJdbcBatchInsertMode(entity, dialect, metadata, requiresGeneratedKeys)
    }

    private static JdbcBatchMetadata metadata(String databaseProductName, String databaseProductVersion, String driverName) {
        new JdbcBatchMetadata(databaseProductName, databaseProductVersion, driverName, true, true)
    }

    private static JdbcBatchMetadata mariaDbMetadata() {
        metadata("MariaDB", "10.11.2-MariaDB", "MariaDB Connector/J")
    }

    private static JdbcBatchMetadata mySqlMetadata() {
        metadata("MySQL", "8.4.0", "MySQL Connector/J")
    }

    private RuntimePersistentEntity<?> entityWithNonGeneratedId() {
        Stub(RuntimePersistentEntity) {
            hasIdentity() >> true
            getIdentity() >> Stub(RuntimePersistentProperty) {
                isGenerated() >> false
            }
        }
    }


    private InsertBatchOperation<?> operation(Argument<?> resultArgument) {
        Stub(InsertBatchOperation) {
            getResultArgument() >> resultArgument
        }
    }

    private RuntimePersistentEntity<?> entityWithGeneratedId() {
        entity(false, false)
    }

    private RuntimePersistentEntity<?> entityWithoutIdentity() {
        Stub(RuntimePersistentEntity) {
            hasIdentity() >> false
        }
    }

    private RuntimePersistentEntity<?> entity(boolean cascadesPersistAssociations, boolean postPersist) {
        Stub(RuntimePersistentEntity) {
            hasIdentity() >> true
            getIdentity() >> Stub(RuntimePersistentProperty) {
                isGenerated() >> true
            }
            cascadesPersist() >> cascadesPersistAssociations
            hasPostPersistEventListeners() >> postPersist
        }
    }

    private static final class TestEntity {
    }
}
