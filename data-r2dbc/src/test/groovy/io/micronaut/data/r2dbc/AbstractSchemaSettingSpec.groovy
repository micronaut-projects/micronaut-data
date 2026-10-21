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
package io.micronaut.data.r2dbc

import io.micronaut.context.ApplicationContext
import io.micronaut.data.r2dbc.config.DataR2dbcConfiguration
import io.micronaut.data.r2dbc.operations.R2dbcSchemaHandler
import io.r2dbc.spi.Connection
import io.r2dbc.spi.ConnectionFactory
import reactor.core.publisher.Mono
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

/**
 * The schema setting of a connection read by the schema handler is restored after the connection was switched
 * to another schema, as the schema of a tenant is when the connection is returned to the pool.
 */
abstract class AbstractSchemaSettingSpec extends Specification {

    @AutoCleanup
    @Shared
    ApplicationContext context = ApplicationContext.run(properties)

    /**
     * @return A schema that exists without being created, so that switching to it needs no privilege
     */
    abstract String otherSchema()

    void "the schema setting of the connection is read and restored"() {
        given:
            def schemaHandler = context.getBean(R2dbcSchemaHandler)
            def dialect = context.getBean(DataR2dbcConfiguration).dialect

        when:
            List<String> settings = Mono.usingWhen(Mono.from(context.getBean(ConnectionFactory).create()), (Connection connection) -> {
                Mono<String> read = Mono.defer(() -> Mono.from(schemaHandler.readSchemaSetting(connection, dialect)))
                return read.flatMap(before -> Mono.from(schemaHandler.useSchema(connection, dialect, otherSchema()))
                        .then(read)
                        .flatMap(switched -> Mono.from(schemaHandler.restoreSchemaSetting(connection, dialect, before))
                                .then(read)
                                .map(after -> [before, switched, after])))
            }, (Connection connection) -> connection.close()).block()

        then:
            settings[0] != null
            settings[1].equalsIgnoreCase(otherSchema())
            settings[2] == settings[0]
    }
}
