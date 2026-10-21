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
package io.micronaut.data.r2dbc.h2.tenantschema

import io.micronaut.context.ApplicationContext
import io.micronaut.data.connection.ConnectionDefinition
import io.micronaut.data.r2dbc.connection.DefaultR2dbcReactorConnectionOperations
import io.micronaut.data.r2dbc.operations.R2dbcOperations
import io.micronaut.data.r2dbc.transaction.R2dbcReactorTransactionOperations
import io.micronaut.data.runtime.config.SchemaGenerate
import io.micronaut.transaction.support.DefaultTransactionDefinition
import reactor.core.publisher.Flux
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

/**
 * The pool has a single connection, so every call reuses the physical connection of the previous one.
 */
class H2TenantSchemaPooledConnectionSpec extends Specification {

    @Shared
    @AutoCleanup
    ApplicationContext context = ApplicationContext.run([
            'tenant-schema-spec'                             : true,
            'r2dbc.datasources.default.url'                  : "r2dbc:pool:h2:mem:///tenantschemapooled",
            'r2dbc.datasources.default.username'             : '',
            'r2dbc.datasources.default.password'             : '',
            'r2dbc.datasources.default.options.maxSize'      : '1',
            'r2dbc.datasources.default.options.initialSize'  : '1',
            'r2dbc.datasources.default.options.DB_CLOSE_DELAY': '-1',
            'r2dbc.datasources.default.dialect'              : 'h2',
            'r2dbc.datasources.default.schema-generate'      : SchemaGenerate.CREATE.name(),
            'r2dbc.datasources.default.schema-generate-names': ['foo', 'bar'],
            'r2dbc.datasources.default.packages'             : getClass().package.name,
            'micronaut.data.multi-tenancy.mode'              : 'SCHEMA'
    ])

    @Shared
    TenantNoteRepository repository = context.getBean(TenantNoteRepository)

    @Shared
    DefaultR2dbcReactorConnectionOperations connectionOperations = context.getBean(DefaultR2dbcReactorConnectionOperations)

    @Shared
    R2dbcReactorTransactionOperations transactionOperations = context.getBean(R2dbcReactorTransactionOperations)

    @Shared
    TestTenantResolver tenantResolver = context.getBean(TestTenantResolver)

    void setupSpec() {
        // The table of the default schema, used by the calls without a tenant
        tenantResolver.tenant = null
        query("CREATE TABLE IF NOT EXISTS PUBLIC.TENANT_NOTE (ID BIGINT AUTO_INCREMENT PRIMARY KEY, TEXT VARCHAR(255))")
    }

    void "a call without a tenant made after the schema generation runs in the default schema"() {
        given:
            tenantResolver.tenant = null

        expect:
            currentSchema() == "PUBLIC"
    }

    void "a call without a tenant made after a call of a tenant runs in the default schema"() {
        given:
            tenantResolver.tenant = "foo"
            repository.deleteAll().block()
            repository.save(new TenantNote(text: "foo note")).block()

        when:
            tenantResolver.tenant = null

        then: "the row of the tenant is not visible"
            repository.count().block() == 0
            currentSchema() == "PUBLIC"
    }

    void "a call without a tenant made after a transaction of a tenant runs in the default schema"() {
        given:
            tenantResolver.tenant = "foo"
            repository.deleteAll().block()

        when:
            transactionOperations.withTransaction(new DefaultTransactionDefinition(), status ->
                    repository.save(new TenantNote(text: "foo note")).then(repository.count())
            ).blockLast()
            tenantResolver.tenant = null

        then:
            repository.count().block() == 0
            currentSchema() == "PUBLIC"
    }

    void "a connection opened without a tenant is restored when a call of a tenant reused it"() {
        given:
            tenantResolver.tenant = "foo"
            repository.deleteAll().block()
            repository.save(new TenantNote(text: "foo note")).block()
            tenantResolver.tenant = null

        when: "the scope that opened the connection has no tenant, the call that reuses it has one"
            def countInScope = Flux.from(connectionOperations.withConnectionFlux(ConnectionDefinition.DEFAULT, status -> {
                tenantResolver.tenant = "foo"
                return Flux.from(repository.count())
            })).blockLast()
            tenantResolver.tenant = null

        then:
            countInScope == 1
            repository.count().block() == 0
            currentSchema() == "PUBLIC"
    }

    void "a call without a tenant made after a connection of a tenant opened by the R2DBC operations runs in the default schema"() {
        given:
            tenantResolver.tenant = "foo"

        when:
            def schemaInScope = Flux.from(context.getBean(R2dbcOperations).withConnection { connection ->
                Flux.from(connection.createStatement("SELECT SCHEMA()").execute())
                        .flatMap(result -> result.map((row, metadata) -> String.valueOf(row.get(0))))
            }).blockLast()
            tenantResolver.tenant = null

        then:
            schemaInScope == "FOO"
            currentSchema() == "PUBLIC"
    }

    private String currentSchema() {
        return query("SELECT SCHEMA()")
    }

    private String query(String sql) {
        return Flux.from(connectionOperations.withConnection(ConnectionDefinition.DEFAULT, status ->
                Flux.from(status.connection.createStatement(sql).execute())
                        .flatMap(result -> result.map((row, metadata) -> String.valueOf(row.get(0))))
        )).blockLast()
    }
}
