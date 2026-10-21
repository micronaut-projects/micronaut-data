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
package io.micronaut.data.jdbc.postgres.notification.schema

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.data.annotation.MappedProperty
import io.micronaut.data.connection.jdbc.advice.DelegatingDataSource
import io.micronaut.data.jdbc.annotation.ChangeListener
import io.micronaut.data.jdbc.annotation.PostgresChangeNotification
import io.micronaut.data.jdbc.notification.ChangeEvent
import io.micronaut.data.jdbc.notification.ChangeOperation
import io.micronaut.data.jdbc.notification.postgres.PostgresChangeEventMetadata
import io.micronaut.data.jdbc.postgres.PostgresTestPropertyProvider
import io.micronaut.data.jdbc.postgres.notification.placeholder.PlaceholderBookListener
import io.micronaut.data.jdbc.postgres.notification.generated.UnqualifiedGeneratedBookListener
import io.micronaut.data.runtime.config.SchemaGenerate
import jakarta.inject.Singleton
import spock.lang.Specification
import spock.lang.Unroll

import javax.sql.DataSource
import java.sql.Connection
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

class PostgresNotificationSchemaSpec extends Specification implements PostgresTestPropertyProvider {
    private ApplicationContext bootstrap
    private ApplicationContext application
    private Connection writer
    private Map settings
    private static final String TENANT = 'b3d93d9a-1b6c-40ae-a761-f15d6010ad67'

    @Override
    SchemaGenerate schemaGenerate() { SchemaGenerate.NONE }

    void setup() {
        settings = properties
        bootstrap = ApplicationContext.run(settings)
        writer = DelegatingDataSource.unwrapDataSource(bootstrap.getBean(DataSource)).connection
    }

    void cleanup() {
        application?.close()
        if (writer != null) {
            execute('DROP SCHEMA IF EXISTS notification_schema_test CASCADE')
            execute('DROP SCHEMA IF EXISTS notification_unqualified_test CASCADE')
            writer.close()
        }
        bootstrap?.close()
    }

    private void start(String mode, boolean enabled, boolean batch = false, String notificationMode = 'ROW') {
        Map configuration = settings + [
            'spec.name': 'PostgresNotificationSchemaSpec',
            'datasources.default.packages': ['io.micronaut.data.jdbc.postgres.notification.schema'],
            'datasources.default.schema-generate': mode,
            'datasources.default.batch-generate': batch,
            'micronaut.data.jdbc.notifications.postgres.schema-generate': enabled,
            'micronaut.data.jdbc.notifications.postgres.schema-generate-mode': notificationMode,
            'micronaut.data.jdbc.notifications.postgres.poll-timeout': '100ms'
        ]
        if (mode == 'VALIDATE') {
            configuration['datasources.default.schema-generate-name'] = 'notification_schema_test'
        }
        application = ApplicationContext.run(configuration)
    }

    @Unroll
    void "#mode installs deduplicated triggers with batch generation #batch and preserves delivery after restart"() {
        when:
        start(mode, true, batch)
        def listeners = application.getBean(GeneratedBookListeners)

        then:
        countFunctions() == 1
        countTriggers() == 4 // One row trigger and one truncate trigger per channel, not per method.

        when:
        execute("""INSERT INTO notification_schema_test."ÄBook" VALUES ('$TENANT', 1, 'inserted')""")

        then:
        next(listeners.first).entity().orElseThrow().title == 'inserted'
        next(listeners.second).entity().orElseThrow().title == 'inserted'
        def inserted = next(listeners.other)
        inserted.metadata(PostgresChangeEventMetadata).orElseThrow().channel() == "other'changes\\quoted"
        inserted.metadata(PostgresChangeEventMetadata).orElseThrow().newKeys() == [tenant: TENANT, 'BookId': '1']

        when:
        execute("""UPDATE notification_schema_test."ÄBook" SET "BookId" = 2, title = 'updated' WHERE "BookId" = 1""")
        def updated = next(listeners.first)

        then:
        updated.operation() == ChangeOperation.UPDATE
        updated.entity().orElseThrow().id == 2L
        updated.metadata(PostgresChangeEventMetadata).orElseThrow().oldKeys().get('BookId') == '1'
        next(listeners.second).operation() == ChangeOperation.UPDATE
        next(listeners.other).operation() == ChangeOperation.UPDATE

        when:
        execute('DELETE FROM notification_schema_test."ÄBook" WHERE "BookId" = 2')

        then:
        next(listeners.first).operation() == ChangeOperation.DELETE
        next(listeners.second).operation() == ChangeOperation.DELETE
        next(listeners.other).operation() == ChangeOperation.DELETE

        when:
        execute('TRUNCATE notification_schema_test."ÄBook"')

        then:
        next(listeners.first).operation() == ChangeOperation.INVALIDATE
        next(listeners.second).operation() == ChangeOperation.INVALIDATE
        next(listeners.other).operation() == ChangeOperation.INVALIDATE

        when: 'schema generation runs again without accumulating triggers'
        application.close()
        start('CREATE', true, false)
        def restarted = application.getBean(GeneratedBookListeners)
        execute("""INSERT INTO notification_schema_test."ÄBook" VALUES ('$TENANT', 3, 'restarted')""")

        then:
        countTriggers() == 4
        next(restarted.first).entity().orElseThrow().title == 'restarted'
        next(restarted.second).entity().orElseThrow().title == 'restarted'
        next(restarted.other).entity().orElseThrow().title == 'restarted'
        restarted.first.poll(200, TimeUnit.MILLISECONDS) == null
        restarted.second.isEmpty()
        restarted.other.isEmpty()

        where:
        mode          | batch
        'CREATE'      | false
        'CREATE_DROP' | true
    }

    @Unroll
    void "#mode installs statement invalidations with batch generation #batch"() {
        when:
        start(mode, true, batch, 'STATEMENT')
        def listeners = application.getBean(GeneratedBookListeners)

        then:
        countFunctions() == 0
        countFunctions('micronaut_data_notify_invalidation') == 1
        countTriggers() == 2 // One statement trigger per channel, shared by matching listener methods.

        when:
        execute("""INSERT INTO notification_schema_test."ÄBook"
            SELECT '$TENANT'::uuid, id, 'bulk' FROM generate_series(1, 1000) AS id""")

        then:
        assertInvalidations(listeners)

        when:
        execute('UPDATE notification_schema_test."ÄBook" SET title = \'updated\'')

        then:
        assertInvalidations(listeners)

        when:
        execute('DELETE FROM notification_schema_test."ÄBook"')

        then:
        assertInvalidations(listeners)

        when: 'an empty statement still invalidates'
        execute('DELETE FROM notification_schema_test."ÄBook"')

        then:
        assertInvalidations(listeners)

        when:
        execute('TRUNCATE notification_schema_test."ÄBook"')

        then:
        assertInvalidations(listeners)

        where:
        mode          | batch
        'CREATE'      | false
        'CREATE_DROP' | true
    }

    void "switching schema generation modes replaces old triggers without duplicates"() {
        given:
        start('CREATE', true)
        application.close()

        when:
        start('CREATE', true, false, 'STATEMENT')
        def listeners = application.getBean(GeneratedBookListeners)
        execute("""INSERT INTO notification_schema_test."ÄBook" VALUES ('$TENANT', 1, 'statement')""")

        then:
        countTriggers() == 2
        assertInvalidations(listeners)

        when: 'repeating statement generation does not add triggers'
        application.close()
        start('CREATE', true, false, 'STATEMENT')
        execute('UPDATE notification_schema_test."ÄBook" SET title = \'again\'')

        then:
        countTriggers() == 2
        assertInvalidations(application.getBean(GeneratedBookListeners))

        when: 'switching back restores row events'
        application.close()
        start('CREATE', true)
        def restarted = application.getBean(GeneratedBookListeners)
        execute('UPDATE notification_schema_test."ÄBook" SET title = \'row\'')

        then:
        countTriggers() == 4
        next(restarted.first).operation() == ChangeOperation.UPDATE
        next(restarted.second).operation() == ChangeOperation.UPDATE
        next(restarted.other).operation() == ChangeOperation.UPDATE
        restarted.first.poll(200, TimeUnit.MILLISECONDS) == null
        restarted.second.isEmpty()
        restarted.other.isEmpty()
    }

    private static boolean assertInvalidations(GeneratedBookListeners listeners) {
        [listeners.first, listeners.second, listeners.other].each { events ->
            def event = next(events)
            assert event.operation() == ChangeOperation.INVALIDATE
            assert event.entity().isEmpty()
            def metadata = event.metadata(PostgresChangeEventMetadata).orElseThrow()
            assert metadata.schema() == 'notification_schema_test'
            assert metadata.table() == 'ÄBook'
            assert metadata.oldKeys().isEmpty()
            assert metadata.newKeys().isEmpty()
            assert events.poll(200, TimeUnit.MILLISECONDS) == null
        }
        true
    }

    void "mapping placeholders support generated triggers and entity delivery"() {
        when:
        application = ApplicationContext.run(settings + [
            'spec.name': 'PostgresNotificationPlaceholderTest',
            'notification.test.schema': 'notification_schema_test',
            'notification.test.table': 'ConfiguredBooks',
            'datasources.default.packages': ['io.micronaut.data.jdbc.postgres.notification.placeholder'],
            'datasources.default.schema-generate': 'CREATE',
            'micronaut.data.jdbc.notifications.postgres.schema-generate': true,
            'micronaut.data.jdbc.notifications.postgres.poll-timeout': '100ms'
        ])
        def listener = application.getBean(PlaceholderBookListener)
        execute("""INSERT INTO notification_schema_test."ConfiguredBooks" VALUES (1, 'resolved')""")

        then:
        countFunctions() == 1
        countTriggers() == 2
        def event = listener.events.poll(10, TimeUnit.SECONDS)
        event != null
        event.entity().orElseThrow().title == 'resolved'
        event.metadata(PostgresChangeEventMetadata).orElseThrow().table() == 'ConfiguredBooks'
    }

    void "notification schema generation is disabled by default"() {
        when:
        start('CREATE', false)

        then:
        countFunctions() == 0
        countTriggers() == 0
    }

    @Unroll
    void "#mode does not install notification objects even when opted in"() {
        given:
        start('CREATE', false)
        application.close()

        when:
        start(mode, true, false, notificationMode)

        then:
        countFunctions('micronaut_data_notify_invalidation') == 0
        countFunctions() == 0
        countTriggers() == 0

        where:
        mode       | notificationMode
        'NONE'     | 'ROW'
        'NONE'     | 'STATEMENT'
        'VALIDATE' | 'ROW'
        'VALIDATE' | 'STATEMENT'
    }

    void "only listeners selecting the datasource receive generated triggers"() {
        when:
        application = ApplicationContext.run(settings + getDataSourceProperties('inventory') + [
            'spec.name': 'PostgresNotificationSchemaSpec',
            'datasources.default.schema-generate': 'NONE',
            'spec.inventory.enabled': true,
            'datasources.inventory.schema-generate': 'CREATE',
            'datasources.inventory.batch-generate': false,
            'datasources.inventory.packages': ['io.micronaut.data.jdbc.postgres.notification.schema'],
            'datasources.inventory.url': writer.metaData.URL,
            'datasources.inventory.username': bootstrap.environment.getRequiredProperty('datasources.default.username', String),
            'datasources.inventory.password': bootstrap.environment.getRequiredProperty('datasources.default.password', String),
            'micronaut.data.jdbc.notifications.postgres.schema-generate': true,
            'micronaut.data.jdbc.notifications.postgres.poll-timeout': '100ms'
        ])
        def listeners = application.getBean(GeneratedBookListeners)
        execute("""INSERT INTO notification_schema_test."ÄBook" VALUES ('$TENANT', 1, 'inventory')""")

        then:
        countFunctions() == 1
        countTriggers() == 2
        next(application.getBean(InventoryBookListener).events).entity().orElseThrow().title == 'inventory'
        listeners.first.poll(200, TimeUnit.MILLISECONDS) == null
        listeners.second.isEmpty()
        listeners.other.isEmpty()
    }

    void "notification installation errors fail application startup"() {
        given:
        execute('CREATE SCHEMA notification_schema_test')
        execute("""CREATE FUNCTION notification_schema_test.micronaut_data_notify_change()
            RETURNS integer LANGUAGE sql AS 'SELECT 1'""")

        when:
        start('CREATE', true)

        then:
        def failure = thrown(Exception)
        failure.message.contains('cannot change return type')
        countTriggers() == 0
    }

    void "unqualified mappings install in the schema selected by schema generation"() {
        when:
        String url = writer.metaData.URL
        application = ApplicationContext.run(settings + [
            'spec.name': 'PostgresNotificationSchemaSpec',
            'spec.unqualified.enabled': true,
            // The shared initializer uses public until schema generation creates the selected schema.
            'datasources.default.url': url + (url.contains('?') ? '&' : '?') + 'currentSchema=notification_unqualified_test,public',
            'datasources.default.packages': ['io.micronaut.data.jdbc.postgres.notification.generated'],
            'datasources.default.schema-generate': 'CREATE',
            'datasources.default.schema-generate-name': 'notification_unqualified_test',
            'micronaut.data.jdbc.notifications.postgres.schema-generate': true,
            'micronaut.data.jdbc.notifications.postgres.poll-timeout': '100ms'
        ])
        def listener = application.getBean(UnqualifiedGeneratedBookListener)
        execute("INSERT INTO notification_unqualified_test.book VALUES (1, 'selected schema')")

        then:
        count("""SELECT count(*) FROM pg_proc p JOIN pg_namespace n ON n.oid = p.pronamespace
            WHERE n.nspname = 'notification_unqualified_test' AND p.proname = 'micronaut_data_notify_change'""") == 1
        listener.events.poll(10, TimeUnit.SECONDS).entity().orElseThrow().title == 'selected schema'
        countFunctions() == 0 // Listeners whose entities are outside the configured packages are ignored.
    }

    private int countFunctions(String function = 'micronaut_data_notify_change') {
        count("""SELECT count(*) FROM pg_proc p JOIN pg_namespace n ON n.oid = p.pronamespace
            WHERE n.nspname = 'notification_schema_test' AND p.proname = '$function'""")
    }

    private int countTriggers() {
        count("""SELECT count(*) FROM pg_trigger t JOIN pg_class c ON c.oid = t.tgrelid
            JOIN pg_namespace n ON n.oid = c.relnamespace
            WHERE n.nspname = 'notification_schema_test' AND NOT t.tgisinternal""")
    }

    private int count(String sql) {
        try (def statement = writer.createStatement(); def rows = statement.executeQuery(sql)) {
            assert rows.next()
            rows.getInt(1)
        }
    }

    private void execute(String sql) {
        try (def statement = writer.createStatement()) {
            statement.execute(sql)
        }
    }

    private static ChangeEvent<GeneratedBook> next(LinkedBlockingQueue<ChangeEvent<GeneratedBook>> events) {
        def event = events.poll(10, TimeUnit.SECONDS)
        assert event != null
        event
    }
}

@MappedEntity(value = 'ÄBook', schema = 'notification_schema_test')
class GeneratedBook {
    @Id UUID tenant
    @Id @MappedProperty('BookId') Long id
    String title
}

@Singleton
@Requires(property = 'spec.name', value = 'PostgresNotificationSchemaSpec')
class GeneratedBookListeners {
    final LinkedBlockingQueue<ChangeEvent<GeneratedBook>> first = new LinkedBlockingQueue<>()
    final LinkedBlockingQueue<ChangeEvent<GeneratedBook>> second = new LinkedBlockingQueue<>()
    final LinkedBlockingQueue<ChangeEvent<GeneratedBook>> other = new LinkedBlockingQueue<>()

    @ChangeListener
    @PostgresChangeNotification(channel = 'generated_changes')
    void first(ChangeEvent<GeneratedBook> event) { first.add(event) }

    @ChangeListener
    @PostgresChangeNotification(channel = 'generated_changes')
    void second(ChangeEvent<GeneratedBook> event) { second.add(event) }

    @ChangeListener
    @PostgresChangeNotification(channel = "other'changes\\quoted")
    void other(ChangeEvent<GeneratedBook> event) { other.add(event) }
}

@Singleton
@Requires(property = 'spec.name', value = 'PostgresNotificationSchemaSpec')
@Requires(property = 'spec.inventory.enabled', value = 'true')
class InventoryBookListener {
    final LinkedBlockingQueue<ChangeEvent<GeneratedBook>> events = new LinkedBlockingQueue<>()

    @ChangeListener(dataSource = 'inventory')
    @PostgresChangeNotification(channel = 'inventory_changes')
    void changed(ChangeEvent<GeneratedBook> event) { events.add(event) }
}
