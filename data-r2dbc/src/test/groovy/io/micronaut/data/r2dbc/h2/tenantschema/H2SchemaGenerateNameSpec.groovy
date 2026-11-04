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
import reactor.core.publisher.Flux
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

/**
 * The pool has a single connection: the connection the schema generator used is the one every later call uses.
 */
class H2SchemaGenerateNameSpec extends Specification {

    @Shared
    @AutoCleanup
    ApplicationContext context = ApplicationContext.run([
            'r2dbc.datasources.default.url'                   : "r2dbc:pool:h2:mem:///schemagenerated",
            'r2dbc.datasources.default.username'              : '',
            'r2dbc.datasources.default.password'              : '',
            'r2dbc.datasources.default.options.maxSize'       : '1',
            'r2dbc.datasources.default.options.initialSize'   : '1',
            'r2dbc.datasources.default.options.DB_CLOSE_DELAY': '-1',
            'r2dbc.datasources.default.dialect'               : 'h2',
            'r2dbc.datasources.default.schema-generate'       : SchemaGenerate.CREATE.name(),
            'r2dbc.datasources.default.schema-generate-name'  : 'generated',
            'r2dbc.datasources.default.packages'              : getClass().package.name
    ])

    void "the connection the schema generator used is switched back to its schema"() {
        expect: "the tables are generated in the configured schema"
            query("SELECT TABLE_SCHEMA FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_NAME = 'TENANT_NOTE'") == "GENERATED"
        and: "the connection is back in its default schema"
            query("SELECT SCHEMA()") == "PUBLIC"
    }

    private String query(String sql) {
        return Flux.from(context.getBean(DefaultR2dbcReactorConnectionOperations).withConnection(ConnectionDefinition.DEFAULT, status ->
                Flux.from(status.connection.createStatement(sql).execute())
                        .flatMap(result -> result.map((row, metadata) -> String.valueOf(row.get(0))))
        )).blockLast()
    }
}
