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
import io.micronaut.context.exceptions.ConfigurationException
import io.micronaut.core.annotation.AnnotationValue
import io.micronaut.core.convert.ConversionService
import io.micronaut.core.type.Argument
import io.micronaut.data.annotation.ChangeListener
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.data.jdbc.annotation.PostgresChangeNotification
import io.micronaut.data.jdbc.config.DataJdbcConfiguration
import io.micronaut.data.jdbc.notification.ChangeListenerMethod
import io.micronaut.data.jdbc.notification.ChangeListenerRegistry
import io.micronaut.data.jdbc.operations.JdbcRepositoryOperations
import io.micronaut.data.model.PersistentEntity
import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.model.runtime.RuntimePersistentEntity
import io.micronaut.data.notification.ChangeEvent
import io.micronaut.data.runtime.config.SchemaGenerate
import io.micronaut.inject.BeanDefinition
import io.micronaut.inject.ExecutableMethod
import spock.lang.Specification

import java.sql.Connection
import java.sql.ResultSet
import java.sql.Statement

class PostgresNotificationChannelSpec extends Specification {
    private ApplicationContext context
    private final RuntimePersistentEntity<ChannelBook> entity = new RuntimePersistentEntity<>(ChannelBook)
    private final JdbcRepositoryOperations operations = Stub(JdbcRepositoryOperations) {
        getEntity(ChannelBook) >> entity
        getConversionService() >> ConversionService.SHARED
    }
    private final Connection connection = Mock(Connection) {
        getAutoCommit() >> true
        getSchema() >> 'public'
    }

    void cleanup() {
        context?.close()
    }

    void "#phase preserves the resolved channel exactly: #description"() {
        given:
        context = ApplicationContext.run(['app.channel': channel])
        def listener = listener('${app.channel}')
        def statements = []
        connection.createStatement() >> Stub(Statement) {
            executeQuery(_ as String) >> Stub(ResultSet) {
                next() >> true
                getInt(1) >> 0
            }
            execute(_ as String) >> { String sql -> statements.add(sql); true }
        }
        PostgresChangeListenerDefinition definition

        when:
        if (phase == 'listener') {
            definition = new PostgresChangeListenerDefinitionFactory(operations, context.environment).create(listener)
        } else {
            generate(phase, listener)
        }

        then:
        if (phase == 'listener') {
            assert definition.channel() == channel
        } else {
            assert statements.any { it.startsWith('CREATE TRIGGER') && it.contains("'$channel'") }
            assert !statements.any { it.contains('\${app.channel}') }
        }

        where:
        [phase, description, channel] << ['listener', 'ROW', 'STATEMENT'].collectMany { phase ->
            [['case and spaces', 'Book Changes '], ['ASCII boundary', 'a' * 63], ['UTF-8 boundary', 'é' * 31 + 'a']]
                .collect { [phase, it[0], it[1]] }
        }
    }

    void "#phase rejects a resolved #description channel before subscription or trigger SQL"() {
        given:
        context = ApplicationContext.run(['app.channel': channel])
        def listener = listener('${app.channel}')

        when:
        if (phase == 'listener') {
            new PostgresChangeListenerDefinitionFactory(operations, context.environment).create(listener)
        } else {
            generate(phase, listener)
        }

        then:
        def failure = thrown(IllegalArgumentException)
        failure.message.contains('channel must be non-blank, contain no NUL characters, and occupy at most 63 UTF-8 bytes')
        0 * connection.createStatement()
        0 * connection.setAutoCommit(_)

        where:
        [phase, description, channel] << ['listener', 'ROW', 'STATEMENT'].collectMany { phase ->
            [['empty', ''], ['blank', ' \t'], ['NUL', 'book\0changes'], ['overlong ASCII', 'a' * 64], ['overlong UTF-8', 'é' * 32]]
                .collect { [phase, it[0], it[1]] }
        }
    }

    void "#phase rejects an unresolved channel placeholder"() {
        given:
        context = ApplicationContext.run()
        def listener = listener('${app.missing-channel}')

        when:
        if (phase == 'listener') {
            new PostgresChangeListenerDefinitionFactory(operations, context.environment).create(listener)
        } else {
            generate(phase, listener)
        }

        then:
        def failure = thrown(ConfigurationException)
        failure.message.contains('app.missing-channel')
        0 * connection.createStatement()
        0 * connection.setAutoCommit(_)

        where:
        phase << ['listener', 'ROW', 'STATEMENT']
    }

    private ChangeListenerMethod listener(String channel) {
        def method = Stub(ExecutableMethod) {
            getAnnotation(PostgresChangeNotification) >> AnnotationValue.builder(PostgresChangeNotification).member('channel', channel).build()
            getAnnotation(PostgresChangeListenerQuery) >> AnnotationValue.builder(PostgresChangeListenerQuery)
                .value('SELECT id FROM public.channel_book WHERE id = ?')
                .member('columns', ['id'] as String[]).member('paths', ['id'] as String[]).build()
            getDescription(true) >> 'void changed(ChangeEvent<ChannelBook>)'
            getArguments() >> ([Argument.of(ChangeEvent, Argument.of(ChannelBook))] as Argument[])
            hasStereotype(PostgresChangeNotification) >> true
            stringValue(PostgresChangeNotification, 'channel') >> Optional.of(channel)
            stringValue(ChangeListener, 'dataSource') >> Optional.of('default')
        }
        new ChangeListenerMethod(Stub(BeanDefinition), method, Argument.of(ChannelBook))
    }

    private void generate(String mode, ChangeListenerMethod listener) {
        def registry = new ChangeListenerRegistry()
        registry.process(listener.beanDefinition(), listener.method())
        def configuration = new DataJdbcConfiguration('default')
        configuration.dialect = Dialect.POSTGRES
        configuration.schemaGenerate = SchemaGenerate.CREATE
        def notifications = new PostgresNotificationConfiguration()
        notifications.schemaGenerateMode = PostgresNotificationConfiguration.SchemaGenerationMode.valueOf(mode)
        new PostgresNotificationSchemaGenerator(registry, context.environment, notifications)
            .generate(connection, configuration, [entity] as PersistentEntity[])
    }

    @MappedEntity(value = 'channel_book', schema = 'public')
    private static class ChannelBook {
        @Id Long id
    }
}
