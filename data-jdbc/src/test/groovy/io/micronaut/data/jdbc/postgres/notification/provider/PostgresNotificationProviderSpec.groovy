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
package io.micronaut.data.jdbc.postgres.notification.provider

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.context.event.ShutdownEvent
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.data.annotation.MappedProperty
import io.micronaut.data.connection.jdbc.advice.DelegatingDataSource
import io.micronaut.data.jdbc.annotation.ChangeListener
import io.micronaut.data.jdbc.annotation.PostgresChangeNotification
import io.micronaut.data.jdbc.notification.ChangeNotificationProvider
import io.micronaut.data.jdbc.notification.ChangeEvent
import io.micronaut.data.jdbc.notification.ChangeOperation
import io.micronaut.data.jdbc.notification.postgres.PostgresChangeEventMetadata
import io.micronaut.data.jdbc.notification.postgres.PostgresNotificationConfiguration
import io.micronaut.data.jdbc.postgres.PostgresTestPropertyProvider
import io.micronaut.data.runtime.config.SchemaGenerate
import spock.lang.Specification
import jakarta.inject.Singleton
import javax.sql.DataSource
import java.sql.Connection
import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeoutException
import java.util.concurrent.TimeUnit
import java.util.concurrent.CountDownLatch
import java.util.concurrent.LinkedBlockingQueue

class PostgresNotificationProviderSpec extends Specification implements PostgresTestPropertyProvider {
    private static final String TENANT = 'b3d93d9a-1b6c-40ae-a761-f15d6010ad67'
    private ApplicationContext bootstrap
    private ApplicationContext application
    private Connection writer
    private Map settings
    private BookChangeListener listener

    @Override
    SchemaGenerate schemaGenerate() { SchemaGenerate.NONE }

    void setup() {
        settings = properties
        bootstrap = ApplicationContext.run(settings)
        writer = DelegatingDataSource.unwrapDataSource(bootstrap.getBean(DataSource)).connection
        execute('CREATE SCHEMA notification_provider_test')
        execute('SET search_path TO notification_provider_test')
        execute(getClass().getResource('/io/micronaut/data/jdbc/notification/postgres/create-notification-function.sql').getText('UTF-8'))
        execute('CREATE TABLE "ProviderBook" (tenant uuid, "BookId" bigint, title text, PRIMARY KEY (tenant, "BookId"))')
        execute("""CREATE TRIGGER changes AFTER INSERT OR UPDATE OR DELETE ON "ProviderBook" FOR EACH ROW
            EXECUTE FUNCTION micronaut_data_notify_change('provider_changes', 'tenant', 'BookId')""")
        execute("""CREATE TRIGGER truncation AFTER TRUNCATE ON "ProviderBook" FOR EACH STATEMENT
            EXECUTE FUNCTION micronaut_data_notify_change('provider_changes', 'tenant', 'BookId')""")
    }

    void cleanup() {
        listener?.release?.countDown()
        application?.close()
        if (writer != null) {
            execute('DROP SCHEMA IF EXISTS notification_provider_other CASCADE')
            execute('DROP SCHEMA IF EXISTS notification_provider_test CASCADE')
            writer.close()
        }
        bootstrap?.close()
    }

    private void start(Map additionalSettings = [:]) {
        application = ApplicationContext.run(settings + [
            'spec.name': 'PostgresNotificationProviderSpec',
            'micronaut.data.jdbc.notifications.postgres.poll-timeout': '100ms',
            'micronaut.data.jdbc.notifications.postgres.retry-delay': '100ms'
        ] + additionalSettings)
        listener = application.getBean(BookChangeListener)
        assert next().operation() == ChangeOperation.INVALIDATE
    }

    void "external committed row changes load entities and retain delete and changed keys"() {
        given:
        start()

        when:
        execute("INSERT INTO \"ProviderBook\" VALUES ('$TENANT', 9007199254740993, 'first')")
        def inserted = next()

        then:
        inserted.operation() == ChangeOperation.INSERT
        inserted.entity().orElseThrow().title == 'first'
        inserted.entity().orElseThrow().id == 9007199254740993L

        when:
        execute("""UPDATE "ProviderBook" SET "BookId" = 9007199254740994, title = 'second'""")
        def updated = next()

        then:
        updated.operation() == ChangeOperation.UPDATE
        updated.entity().orElseThrow().title == 'second'
        updated.metadata(PostgresChangeEventMetadata).orElseThrow().oldKeys().BookId == '9007199254740993'
        updated.metadata(PostgresChangeEventMetadata).orElseThrow().newKeys().BookId == '9007199254740994'

        when:
        execute('DELETE FROM "ProviderBook"')
        def deleted = next()

        then:
        deleted.operation() == ChangeOperation.DELETE
        deleted.entity().isEmpty()
        deleted.metadata(PostgresChangeEventMetadata).orElseThrow().oldKeys().BookId == '9007199254740994'
    }

    void "listener retry stays inside AOP and truncate invalidates"() {
        given:
        start()
        listener.failures.set(1)
        int attempts = listener.attempts.get()

        when:
        execute('TRUNCATE "ProviderBook"')

        then:
        next().operation() == ChangeOperation.INVALIDATE
        listener.attempts.get() == attempts + 2
    }

    void "terminated listening session reconnects and reconciles before new changes"() {
        given:
        start()

        when:
        try (def statement = writer.prepareStatement("SELECT pg_terminate_backend(pid) FROM pg_stat_activity WHERE datname = current_database() AND query = ? AND pid <> pg_backend_pid()")) {
            statement.setString(1, 'LISTEN "provider_changes"')
            try (def rows = statement.executeQuery()) {
                assert rows.next() && rows.getBoolean(1)
            }
        }

        then:
        next().operation() == ChangeOperation.INVALIDATE

        when:
        execute("INSERT INTO \"ProviderBook\" VALUES ('$TENANT', 2, 'recovered')")

        then:
        next().entity().orElseThrow().title == 'recovered'
    }

    void "malformed payload invalidates without terminating reception"() {
        given:
        start()

        when:
        execute("NOTIFY provider_changes, 'invalid JSON'")

        then:
        next().operation() == ChangeOperation.INVALIDATE

        when:
        execute("INSERT INTO \"ProviderBook\" VALUES ('$TENANT', 3, 'healthy')")

        then:
        next().entity().orElseThrow().title == 'healthy'
    }

    void "rollback sends no event and entity loading tolerates a row deleted before reload"() {
        given:
        start()
        writer.autoCommit = false

        when:
        execute("INSERT INTO \"ProviderBook\" VALUES ('$TENANT', 4, 'rolled back')")
        writer.rollback()
        writer.autoCommit = true

        then:
        listener.events.poll(300, TimeUnit.MILLISECONDS) == null

        when:
        writer.autoCommit = false
        execute("INSERT INTO \"ProviderBook\" VALUES ('$TENANT', 5, 'deleted before reload')")
        execute('DELETE FROM "ProviderBook"')
        writer.commit()
        writer.autoCommit = true
        def inserted = next()
        def deleted = next()

        then:
        inserted.operation() == ChangeOperation.INSERT
        inserted.entity().isEmpty()
        deleted.operation() == ChangeOperation.DELETE
    }

    void "shutdown lifecycle starts cleanup asynchronously and waits for accepted callbacks"() {
        given:
        start()
        listener.entered = new CountDownLatch(1)
        listener.release = new CountDownLatch(1)
        execute('TRUNCATE "ProviderBook"')
        assert listener.entered.await(10, TimeUnit.SECONDS)
        def provider = application.getBeansOfType(ChangeNotificationProvider)
            .find { it.class.simpleName == 'PostgresChangeNotificationProvider' }
        application.getBean(PostgresNotificationConfiguration).shutdownTimeout = Duration.ofMillis(1)

        when:
        CompletableFuture.runAsync {
            provider.onApplicationEvent(new ShutdownEvent(application))
            provider.close()
        }.get(5, TimeUnit.SECONDS)
        def stopped = provider.shutdownGracefully().toCompletableFuture()

        then:
        !stopped.done
        provider.shutdownGracefully().is(stopped)

        when:
        stopped.get(200, TimeUnit.MILLISECONDS)

        then:
        thrown(TimeoutException)
        !stopped.done

        when:
        listener.release.countDown()
        stopped.get(10, TimeUnit.SECONDS)

        then:
        provider.reportActiveTasks().orElseThrow() == 0
        next().operation() == ChangeOperation.INVALIDATE

        when:
        execute('TRUNCATE "ProviderBook"')

        then:
        listener.events.poll(300, TimeUnit.MILLISECONDS) == null
    }

    void "startup trusts migrations for primary keys and notification triggers"() {
        given:
        execute('DROP TRIGGER changes ON "ProviderBook"')
        execute('DROP TRIGGER truncation ON "ProviderBook"')
        execute('ALTER TABLE "ProviderBook" DROP CONSTRAINT "ProviderBook_pkey"')

        when:
        start()

        then:
        application.running

        when:
        execute("""CREATE TRIGGER changes AFTER INSERT OR UPDATE OR DELETE ON "ProviderBook" FOR EACH ROW
            EXECUTE FUNCTION micronaut_data_notify_change('provider_changes', 'tenant', 'BookId')""")
        execute("INSERT INTO \"ProviderBook\" VALUES ('$TENANT', 6, 'installed after startup')")

        then:
        next().entity().orElseThrow().title == 'installed after startup'
    }

    void "startup does not require the mapped table to exist"() {
        given:
        execute('DROP TABLE "ProviderBook"')

        when:
        start()

        then:
        application.running

        when:
        execute('CREATE TABLE "ProviderBook" (tenant uuid, "BookId" bigint, title text, PRIMARY KEY (tenant, "BookId"))')
        execute("""CREATE TRIGGER changes AFTER INSERT OR UPDATE OR DELETE ON "ProviderBook" FOR EACH ROW
            EXECUTE FUNCTION micronaut_data_notify_change('provider_changes', 'tenant', 'BookId')""")
        execute("INSERT INTO \"ProviderBook\" VALUES ('$TENANT', 7, 'created after startup')")

        then:
        next().entity().orElseThrow().title == 'created after startup'
    }

    void "unqualified mappings accept other schemas while qualified mappings filter them"() {
        given:
        execute('CREATE SCHEMA notification_provider_other')
        execute('CREATE TABLE notification_provider_other."ProviderBook" (LIKE "ProviderBook" INCLUDING ALL)')
        execute("""CREATE TRIGGER changes AFTER INSERT ON notification_provider_other."ProviderBook" FOR EACH ROW
            EXECUTE FUNCTION notification_provider_test.micronaut_data_notify_change('provider_changes', 'tenant', 'BookId')""")
        String url = writer.metaData.URL
        start([
            'spec.unqualified-listener': 'true',
            'datasources.default.url': url + (url.contains('?') ? '&' : '?') + 'currentSchema=notification_provider_test'
        ])
        def unqualified = application.getBean(UnqualifiedBookChangeListener)
        assert unqualified.events.poll(10, TimeUnit.SECONDS).operation() == ChangeOperation.INVALIDATE
        execute("INSERT INTO \"ProviderBook\" VALUES ('$TENANT', 8, 'local')")
        assert next().entity().orElseThrow().title == 'local'
        assert unqualified.events.poll(10, TimeUnit.SECONDS).entity().orElseThrow().title == 'local'

        when:
        execute("INSERT INTO notification_provider_other.\"ProviderBook\" VALUES ('$TENANT', 8, 'other schema')")
        def event = unqualified.events.poll(10, TimeUnit.SECONDS)

        then:
        event.operation() == ChangeOperation.INSERT
        event.metadata(PostgresChangeEventMetadata).orElseThrow().schema() == 'notification_provider_other'
        event.entity().orElseThrow().title == 'local'
        listener.events.poll(300, TimeUnit.MILLISECONDS) == null
    }

    private ChangeEvent<NotificationBook> next() {
        def event = listener.events.poll(10, TimeUnit.SECONDS)
        assert event != null
        event
    }

    private void execute(String sql) {
        try (def statement = writer.createStatement()) {
            statement.execute(sql)
        }
    }
}

@MappedEntity('ProviderBook')
class UnqualifiedNotificationBook {
    @Id UUID tenant
    @Id @MappedProperty('BookId') Long id
    String title
}

@Singleton
@Requires(property = 'spec.name', value = 'PostgresNotificationProviderSpec')
@Requires(property = 'spec.unqualified-listener', value = 'true')
class UnqualifiedBookChangeListener {
    final LinkedBlockingQueue<ChangeEvent<UnqualifiedNotificationBook>> events = new LinkedBlockingQueue<>()

    @ChangeListener
    @PostgresChangeNotification(channel = 'provider_changes')
    void changed(ChangeEvent<UnqualifiedNotificationBook> event) {
        events.add(event)
    }
}
