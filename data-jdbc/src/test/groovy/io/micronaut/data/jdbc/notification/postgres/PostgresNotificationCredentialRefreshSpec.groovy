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
package io.micronaut.data.jdbc.notification.postgres

import io.micronaut.context.ApplicationContext
import io.micronaut.context.BeanContext
import io.micronaut.data.connection.jdbc.advice.DelegatingDataSource
import io.micronaut.data.jdbc.postgres.PostgresTestPropertyProvider
import io.micronaut.data.runtime.config.SchemaGenerate
import io.micronaut.jdbc.BasicJdbcConfiguration
import spock.lang.Specification

import javax.sql.DataSource

class PostgresNotificationCredentialRefreshSpec extends Specification implements PostgresTestPropertyProvider {
    @Override
    SchemaGenerate schemaGenerate() { SchemaGenerate.NONE }

    void "reconnection reads a rotated password from the datasource configuration"() {
        given:
        def application = ApplicationContext.run(properties)
        def admin = DelegatingDataSource.unwrapDataSource(application.getBean(DataSource)).connection
        def role = 'notification_' + UUID.randomUUID().toString().replace('-', '')
        String password = 'initial_password'
        def configuration = Stub(BasicJdbcConfiguration) {
            getConfiguredUrl() >> admin.metaData.URL
            getUsername() >> role
            getPassword() >> { password }
        }
        def context = Stub(BeanContext) {
            findBean(BasicJdbcConfiguration, _) >> Optional.of(configuration)
        }
        def factory = new PostgresNotificationConnectionFactory(context, application.environment)
        try (def statement = admin.createStatement()) {
            statement.execute("CREATE ROLE $role LOGIN PASSWORD 'initial_password'")
        }

        when:
        def first = factory.openConnection('default')

        then:
        first.isValid(5)

        when:
        first.close()
        try (def statement = admin.createStatement()) {
            statement.execute("ALTER ROLE $role PASSWORD 'rotated_password'")
        }
        password = 'rotated_password'
        def replacement = factory.openConnection('default')

        then:
        replacement.isValid(5)

        cleanup:
        first?.close()
        replacement?.close()
        if (admin != null) {
            try (def statement = admin.createStatement()) {
                statement.execute("DROP ROLE IF EXISTS $role")
            }
            admin.close()
        }
        application?.close()
    }
}
