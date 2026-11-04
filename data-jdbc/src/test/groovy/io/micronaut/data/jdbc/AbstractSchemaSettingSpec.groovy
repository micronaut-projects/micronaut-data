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
package io.micronaut.data.jdbc

import io.micronaut.context.ApplicationContext
import io.micronaut.data.jdbc.config.DataJdbcConfiguration
import io.micronaut.data.jdbc.operations.JdbcSchemaHandler
import io.micronaut.data.jdbc.runtime.JdbcOperations
import io.micronaut.test.support.TestPropertyProvider
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

/**
 * The schema setting of a connection read by the schema handler is restored after the connection was switched
 * to another schema, as the schema of a tenant is when the connection is returned to the pool.
 */
abstract class AbstractSchemaSettingSpec extends Specification implements TestPropertyProvider {

    @AutoCleanup
    @Shared
    ApplicationContext context = ApplicationContext.run(getProperties())

    /**
     * @return A schema that exists without being created, so that switching to it needs no privilege
     */
    abstract String otherSchema()

    void "the schema setting of the connection is read and restored"() {
        given:
            def schemaHandler = context.getBean(JdbcSchemaHandler)
            def dialect = context.getBean(DataJdbcConfiguration).dialect

        when:
            def settings = context.getBean(JdbcOperations).execute { connection ->
                String before = schemaHandler.readSchemaSetting(connection, dialect)
                schemaHandler.useSchema(connection, dialect, otherSchema())
                String switched = schemaHandler.readSchemaSetting(connection, dialect)
                schemaHandler.restoreSchemaSetting(connection, dialect, before)
                String after = schemaHandler.readSchemaSetting(connection, dialect)
                [before, switched, after]
            }

        then:
            settings[0] != null
            settings[1].equalsIgnoreCase(otherSchema())
            settings[2] == settings[0]
    }
}
