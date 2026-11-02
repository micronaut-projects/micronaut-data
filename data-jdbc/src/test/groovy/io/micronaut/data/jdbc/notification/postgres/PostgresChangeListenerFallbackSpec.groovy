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
import io.micronaut.core.annotation.AnnotationValue
import io.micronaut.core.type.Argument
import io.micronaut.data.annotation.ChangeListener
import io.micronaut.data.annotation.Embeddable
import io.micronaut.data.annotation.EmbeddedId
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.data.annotation.MappedProperty
import io.micronaut.data.connection.jdbc.advice.DelegatingDataSource
import io.micronaut.data.jdbc.annotation.PostgresChangeNotification
import io.micronaut.data.jdbc.config.DataJdbcConfiguration
import io.micronaut.data.jdbc.notification.ChangeListenerRegistry
import io.micronaut.data.jdbc.operations.JdbcRepositoryOperations
import io.micronaut.data.jdbc.postgres.PostgresTestPropertyProvider
import io.micronaut.data.model.PersistentEntity
import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.notification.ChangeEvent
import io.micronaut.data.notification.ChangeOperation
import io.micronaut.data.runtime.config.SchemaGenerate
import io.micronaut.inject.BeanDefinition
import io.micronaut.inject.ExecutableMethod
import io.micronaut.json.JsonMapper
import org.postgresql.PGConnection
import spock.lang.Specification

import javax.sql.DataSource
import java.sql.Connection

class PostgresChangeListenerFallbackSpec extends Specification implements PostgresTestPropertyProvider {
    private static final String TENANT = 'b3d93d9a-1b6c-40ae-a761-f15d6010ad67'

    @Override
    SchemaGenerate schemaGenerate() { SchemaGenerate.NONE }

    void "fallback trigger keys and reload SQL work together with #mode notifications"() {
        given:
        def context = ApplicationContext.run(properties + [
            'notification.fallback.table': 'Books',
            'notification.fallback.schema': 'notification_fallback_test'
        ])
        def operations = context.getBean(JdbcRepositoryOperations)
        def entity = operations.getEntity(FallbackReloadBook)
        def mapper = context.getBean(JsonMapper)
        def connection = DelegatingDataSource.unwrapDataSource(context.getBean(DataSource)).connection
        execute(connection, 'CREATE SCHEMA notification_fallback_test')
        execute(connection, 'CREATE TABLE notification_fallback_test."Books" ("TenantId" uuid, "BookId" bigint, book_title text, PRIMARY KEY ("TenantId", "BookId"))')
        def method = Mock(ExecutableMethod)
        method.getDescription(true) >> 'void inherited(ChangeEvent<FallbackReloadBook>)'
        method.getAnnotation(PostgresChangeNotification) >> AnnotationValue.builder(PostgresChangeNotification)
            .member('channel', 'fallback_changes').build()
        method.hasStereotype(PostgresChangeNotification) >> true
        method.stringValue(PostgresChangeNotification, 'channel') >> Optional.of('fallback_changes')
        method.stringValue(ChangeListener, 'dataSource') >> Optional.empty()
        method.getArguments() >> ([Argument.of(ChangeEvent, Argument.of(FallbackReloadBook))] as Argument[])
        def registry = new ChangeListenerRegistry()
        registry.process(Mock(BeanDefinition), method)
        def listener = registry.getListeners('default').first()
        def configuration = new DataJdbcConfiguration('default')
        configuration.dialect = Dialect.POSTGRES
        configuration.schemaGenerate = SchemaGenerate.CREATE
        def notificationConfiguration = new PostgresNotificationConfiguration()
        notificationConfiguration.schemaGenerateMode = mode

        when:
        new PostgresNotificationSchemaGenerator(registry, context.environment, notificationConfiguration)
            .generate(connection, configuration, [entity] as PersistentEntity[])
        def definition = new PostgresChangeListenerDefinitionFactory(operations, context.environment).create(listener)
        execute(connection, 'LISTEN fallback_changes')
        execute(connection, "INSERT INTO notification_fallback_test.\"Books\" VALUES ('$TENANT', 42, 'inserted')")
        def inserted = next(connection, mapper)
        def loaded = definition.loader().reload([TenantId: TENANT, BookId: '42'])

        then:
        inserted.operation() == (rowMode ? ChangeOperation.INSERT : ChangeOperation.INVALIDATE)
        inserted.metadata().newKeys() == (rowMode ? [TenantId: TENANT, BookId: '42'] : [:])
        loaded.title == 'inserted'
        loaded.id.tenant == UUID.fromString(TENANT)
        loaded.id.number == 42L

        when:
        execute(connection, 'UPDATE notification_fallback_test."Books" SET "BookId" = 43, book_title = \'updated\'')
        def updated = next(connection, mapper)
        def moved = definition.loader().reload([TenantId: TENANT, BookId: '43'])

        then:
        updated.operation() == (rowMode ? ChangeOperation.UPDATE : ChangeOperation.INVALIDATE)
        updated.metadata().oldKeys() == (rowMode ? [TenantId: TENANT, BookId: '42'] : [:])
        updated.metadata().newKeys() == (rowMode ? [TenantId: TENANT, BookId: '43'] : [:])
        moved.title == 'updated'
        moved.id.number == 43L
        definition.loader().reload([TenantId: TENANT, BookId: '42']) == null

        cleanup:
        if (connection != null) {
            execute(connection, 'DROP SCHEMA IF EXISTS notification_fallback_test CASCADE')
            connection.close()
        }
        context?.close()

        where:
        mode                                                                 | rowMode
        PostgresNotificationConfiguration.SchemaGenerationMode.ROW           | true
        PostgresNotificationConfiguration.SchemaGenerationMode.STATEMENT     | false
    }

    private static PostgresNotificationPayload next(Connection connection, JsonMapper mapper) {
        def notifications = connection.unwrap(PGConnection).getNotifications(5000)
        assert notifications != null && notifications.length == 1
        PostgresNotificationPayload.decode(mapper, notifications[0].name, notifications[0].parameter)
    }

    private static void execute(Connection connection, String sql) {
        try (def statement = connection.createStatement()) {
            statement.execute(sql)
        }
    }

    @MappedEntity(value = '${notification.fallback.table}', schema = '${notification.fallback.schema}', alias = 'book_')
    private static class FallbackReloadBook {
        @EmbeddedId
        FallbackReloadId id
        @MappedProperty('book_title')
        String title
    }

    @Embeddable
    private static class FallbackReloadId {
        @MappedProperty('TenantId')
        UUID tenant
        @MappedProperty('BookId')
        Long number
    }
}
