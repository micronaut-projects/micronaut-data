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
    void "jdbc mysql can batch generated-id inserts for #scenario"() {
        expect:
        SqlBatchSupport.isSupportsJdbcBatchInsert(
            entityWithGeneratedId(),
            Dialect.MYSQL,
            databaseProductName,
            driverName,
            true,
            true,
            true
        )

        where:
        scenario                 | databaseProductName | driverName
        "product metadata"       | "MySQL"             | "MySQL Connector/J"
        "driver metadata"        | null                | "MySQL Connector/J"
    }

    @Unroll
    void "jdbc mariadb stays conservative for generated-id inserts for #scenario"() {
        expect:
        !SqlBatchSupport.isSupportsJdbcBatchInsert(
            entityWithGeneratedId(),
            Dialect.MYSQL,
            databaseProductName,
            driverName,
            true,
            true,
            true
        )

        where:
        scenario                 | databaseProductName | driverName
        "product metadata"       | "MariaDB"           | "MariaDB Connector/J"
        "driver metadata"        | null                | "MariaDB Connector/J"
    }

    @Unroll
    void "jdbc mysql family can batch generated-id inserts without generated keys for #scenario"() {
        expect:
        SqlBatchSupport.isSupportsJdbcBatchInsert(
            entityWithGeneratedId(),
            Dialect.MYSQL,
            databaseProductName,
            driverName,
            true,
            false,
            false
        )

        where:
        scenario                 | databaseProductName | driverName
        "mariadb metadata"       | "MariaDB"           | "MariaDB Connector/J"
        "mysql metadata"         | "MySQL"             | "MySQL Connector/J"
    }

    @Unroll
    void "jdbc mariadb #scenario"() {
        given:
        boolean requiresGeneratedKeys = SqlBatchSupport.requiresBatchGeneratedKeys(entityWithGeneratedId(), operation(resultArgument))

        expect:
        SqlBatchSupport.isSupportsJdbcBatchInsert(
            entityWithGeneratedId(),
            Dialect.MYSQL,
            "MariaDB",
            "MariaDB Connector/J",
            true,
            true,
            requiresGeneratedKeys
        ) == supported

        where:
        scenario                                      | resultArgument                     || supported
        "falls back for entity-returning saveAll"      | Argument.listOf(TestEntity)         || false
        "can batch for void insertAll"                 | Argument.of(Void)                  || true
        "can batch for count-returning insertAll"      | Argument.of(Long)                  || true
    }

    @Unroll
    void "jdbc mysql #scenario"() {
        given:
        boolean requiresGeneratedKeys = SqlBatchSupport.requiresBatchGeneratedKeys(entityWithGeneratedId(), operation(resultArgument))

        expect:
        SqlBatchSupport.isSupportsJdbcBatchInsert(
            entityWithGeneratedId(),
            Dialect.MYSQL,
            "MySQL",
            "MySQL Connector/J",
            true,
            true,
            requiresGeneratedKeys
        ) == supported

        where:
        scenario                                      | resultArgument                     || supported
        "can batch for entity-returning saveAll"       | Argument.listOf(TestEntity)         || true
        "can batch for void insertAll"                 | Argument.of(Void)                  || true
        "can batch for count-returning insertAll"      | Argument.of(Long)                  || true
    }

    void "jdbc mysql family does not batch generated-id inserts when generated keys are unsupported"() {
        expect:
        !SqlBatchSupport.isSupportsJdbcBatchInsert(
            entityWithGeneratedId(),
            Dialect.MYSQL,
            "MySQL",
            "MySQL Connector/J",
            true,
            false,
            true
        )
    }

    void "jdbc mysql family does not batch inserts when batch updates are unsupported"() {
        expect:
        !SqlBatchSupport.isSupportsJdbcBatchInsert(
            entityWithGeneratedId(),
            Dialect.MYSQL,
            "MySQL",
            "MySQL Connector/J",
            false,
            true,
            false
        )
    }

    @Unroll
    void "jdbc mysql family stays conservative for identity-less inserts for #scenario"() {
        expect:
        !SqlBatchSupport.isSupportsJdbcBatchInsert(
            entityWithoutIdentity(),
            Dialect.MYSQL,
            databaseProductName,
            driverName,
            true,
            true,
            false
        )

        where:
        scenario           | databaseProductName | driverName
        "MariaDB metadata" | "MariaDB"          | "MariaDB Connector/J"
        "MySQL metadata"   | "MySQL"            | "MySQL Connector/J"
    }

    void "jdbc non-mysql dialect keeps batching for identity-less inserts"() {
        expect:
        SqlBatchSupport.isSupportsJdbcBatchInsert(
            entityWithoutIdentity(),
            Dialect.POSTGRES,
            "PostgreSQL",
            "PostgreSQL JDBC Driver",
            true,
            false,
            false
        )
    }

    void "jdbc mysql family requires explicit batch update support"() {
        expect:
        !SqlBatchSupport.isSupportsJdbcBatchInsert(
            entityWithGeneratedId(),
            Dialect.MYSQL,
            "MySQL",
            "MySQL Connector/J",
            null,
            true,
            true
        )
    }

    void "jdbc unknown mysql metadata stays conservative for generated identities"() {
        expect:
        !SqlBatchSupport.isSupportsJdbcBatchInsert(
            entityWithGeneratedId(),
            Dialect.MYSQL,
            null,
            null,
            null,
            null,
            true
        )
    }

    @Unroll
    void "jdbc mysql metadata does not change #dialect generated-id batch support"() {
        expect:
        SqlBatchSupport.isSupportsJdbcBatchInsert(
            entityWithGeneratedId(),
            dialect,
            "MySQL",
            "MySQL Connector/J",
            true,
            true,
            true
        ) == supported

        where:
        dialect            || supported
        Dialect.ORACLE     || false
        Dialect.SQL_SERVER || false
        Dialect.POSTGRES   || true
    }

    @Unroll
    void "generated keys are required for #scenario"() {
        expect:
        SqlBatchSupport.requiresBatchGeneratedKeys(entity(cascadesPersist, postPersist), operation(resultArgument)) == required

        where:
        scenario                         | cascadesPersist | postPersist | resultArgument                                            || required
        "entity lists"                   | false           | false       | Argument.listOf(TestEntity)                               || true
        "completion stage entity lists"  | false           | false       | Argument.of(CompletionStage, Argument.listOf(TestEntity)) || true
        "optional numeric returns"        | false           | false       | Argument.of(Optional, Argument.of(Long))                 || false
        "publisher numeric returns"       | false           | false       | Argument.of(Publisher, Argument.of(Long))                || false
        "void returns"                   | false           | false       | Argument.of(Void)                                          || false
        "boxed boolean returns"          | false           | false       | Argument.of(Boolean)                                       || false
        "primitive count arrays"         | false           | false       | Argument.of(long[].class)                                  || false
        "boxed count arrays"             | false           | false       | Argument.of(Long[].class)                                  || false
        "boxed boolean arrays"           | false           | false       | Argument.of(Boolean[].class)                               || false
        "entity arrays"                  | false           | false       | Argument.of(TestEntity[].class)                            || true
        "numeric returns"                | false           | false       | Argument.of(Long)                                         || false
        "primitive numeric returns"      | false           | false       | Argument.of(Long.TYPE)                                    || false
        "post persist listeners"         | false           | true        | Argument.of(Long)                                         || true
        "cascade persist associations"   | true            | false       | Argument.of(Void)                                          || true
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
