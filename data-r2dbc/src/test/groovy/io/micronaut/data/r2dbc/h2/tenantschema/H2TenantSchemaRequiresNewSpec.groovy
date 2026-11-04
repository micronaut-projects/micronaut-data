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
import io.micronaut.data.runtime.config.SchemaGenerate
import io.r2dbc.spi.Connection
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

/**
 * The pool has two connections: a scope with a new connection inside another scope uses both.
 */
class H2TenantSchemaRequiresNewSpec extends Specification {

    @Shared
    @AutoCleanup
    ApplicationContext context = ApplicationContext.run([
            'tenant-schema-spec'                              : true,
            'r2dbc.datasources.default.url'                   : "r2dbc:pool:h2:mem:///tenantschemarequiresnew",
            'r2dbc.datasources.default.username'              : '',
            'r2dbc.datasources.default.password'              : '',
            'r2dbc.datasources.default.options.maxSize'       : '2',
            'r2dbc.datasources.default.options.initialSize'   : '2',
            'r2dbc.datasources.default.options.DB_CLOSE_DELAY': '-1',
            'r2dbc.datasources.default.dialect'               : 'h2',
            'r2dbc.datasources.default.schema-generate'       : SchemaGenerate.CREATE.name(),
            'r2dbc.datasources.default.schema-generate-names' : ['foo', 'bar'],
            'r2dbc.datasources.default.packages'              : getClass().package.name,
            'micronaut.data.multi-tenancy.mode'               : 'SCHEMA'
    ])

    @Shared
    TenantNoteRepository repository = context.getBean(TenantNoteRepository)

    @Shared
    DefaultR2dbcReactorConnectionOperations connectionOperations = context.getBean(DefaultR2dbcReactorConnectionOperations)

    @Shared
    TestTenantResolver tenantResolver = context.getBean(TestTenantResolver)

    void "the connection of a nested scope with a new connection is restored, the connection of the outer scope is not changed"() {
        given:
            tenantResolver.tenant = "foo"
            repository.deleteAll().block()
            repository.save(new TenantNote(text: "foo note")).block()
            tenantResolver.tenant = null

        when: "the outer scope has no tenant, the nested scope with a new connection has one"
            def schemas = connectionOperations.withConnectionMono(ConnectionDefinition.DEFAULT, outer ->
                    connectionOperations.withConnectionMono(ConnectionDefinition.REQUIRES_NEW, nested -> {
                        tenantResolver.tenant = "foo"
                        return repository.count()
                    }).doFinally(signal -> tenantResolver.tenant = null)
                            .flatMap(countInNested -> currentSchema(outer.connection).map(outerSchema -> [countInNested, outerSchema]))
            ).block()

        then:
            schemas == [1L, "PUBLIC"]

        when: "both connections of the pool are used without a tenant"
            def schemasAfter = connectionOperations.withConnectionMono(ConnectionDefinition.DEFAULT, outer ->
                    connectionOperations.withConnectionMono(ConnectionDefinition.REQUIRES_NEW, nested -> currentSchema(nested.connection))
                            .flatMap(nestedSchema -> currentSchema(outer.connection).map(outerSchema -> [outerSchema, nestedSchema]))
            ).block()

        then:
            schemasAfter == ["PUBLIC", "PUBLIC"]
    }

    private static Mono<String> currentSchema(Connection connection) {
        return Flux.from(connection.createStatement("SELECT SCHEMA()").execute())
                .flatMap(result -> result.map((row, metadata) -> String.valueOf(row.get(0))))
                .next()
    }
}
