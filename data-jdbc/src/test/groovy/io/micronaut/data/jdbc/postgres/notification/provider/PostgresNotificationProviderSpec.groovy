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
import io.micronaut.data.jdbc.postgres.PostgresTestPropertyProvider
import io.micronaut.data.runtime.config.SchemaGenerate
import spock.lang.Specification
import spock.lang.Unroll
import spock.util.concurrent.PollingConditions
import jakarta.inject.Singleton
import javax.sql.DataSource
import java.sql.Connection
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
                'spec.name'                                              : 'PostgresNotificationProviderSpec',
                'micronaut.data.jdbc.notifications.postgres.poll-timeout': '100ms',
                'micronaut.data.jdbc.notifications.postgres.retry-delay' : '100ms'
        ] + additionalSettings)
        listener = application.getBean(BookChangeListener)
    }

    void "startup emits no invalidation or events for existing rows"() {
        given:
        execute("INSERT INTO \"ProviderBook\" VALUES ('$TENANT', 1, 'existing')")

        when:
        start()

        then:
        listener.events.poll(300, TimeUnit.MILLISECONDS) == null
        listener.attempts.get() == 0

        when:
        execute('UPDATE "ProviderBook" SET title = \'changed after startup\'')
        def event = next()

        then:
        event.operation() == ChangeOperation.UPDATE
        event.entity().orElseThrow().title == 'changed after startup'
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

    void "bulk changes load every affected entity without requiring callback order"() {
        given:
        start()

        when:
        execute("""INSERT INTO "ProviderBook" VALUES
            ('$TENANT', 1, 'first'), ('$TENANT', 2, 'second'), ('$TENANT', 3, 'third')""")
        def inserted = (1..3).collect { next() }

        then:
        inserted.every { it.operation() == ChangeOperation.INSERT }
        inserted.collect { it.entity().orElseThrow().id }.toSet() == [1L, 2L, 3L].toSet()

        when:
        execute("UPDATE \"ProviderBook\" SET title = 'bulk update'")
        def updated = (1..3).collect { next() }

        then:
        updated.every { it.operation() == ChangeOperation.UPDATE }
        updated.every { it.entity().orElseThrow().title == 'bulk update' }
        updated.collect { it.entity().orElseThrow().id }.toSet() == [1L, 2L, 3L].toSet()

        when:
        execute('DELETE FROM "ProviderBook"')
        def deleted = (1..3).collect { next() }

        then:
        deleted.every { it.operation() == ChangeOperation.DELETE && it.entity().isEmpty() }
        deleted.collect { it.metadata(PostgresChangeEventMetadata).orElseThrow().oldKeys().BookId }.toSet() == ['1', '2', '3'].toSet()
    }

    @Unroll
    void "overload collapses into invalidation and row delivery resumes with work limit #limit"() {
        given:
        start([
            'micronaut.data.jdbc.notifications.postgres.max-outstanding-tasks': limit,
            'spec.overload-probe': true
        ])
        listener.entered = new CountDownLatch(limit)
        listener.release = new CountDownLatch(1)
        def probe = application.getBean(OverloadNotificationProbe)
        (1..limit).each { id ->
            execute("INSERT INTO \"ProviderBook\" VALUES ('$TENANT', $id, 'initial')")
        }
        assert listener.entered.await(10, TimeUnit.SECONDS)

        when: 'the listener holds every slot while a committed bulk insert arrives'
        execute("""INSERT INTO "ProviderBook"
            SELECT '$TENANT'::uuid, id, 'burst' FROM generate_series(100, 109) AS id""")
        // PostgreSQL preserves notification order. Receiving this probe means the preceding row
        // notifications have passed through the receiver, even though their listener is blocked.
        execute("""SELECT pg_notify('provider_probe', '{"version":1,"schema":"notification_provider_test",
            "table":"ProviderBook","operation":"INVALIDATE","oldKeys":{},"newKeys":{}}')""")

        then:
        probe.events.poll(10, TimeUnit.SECONDS) != null
        listener.attempts.get() == limit
        listener.events.isEmpty()

        when: 'completion alone must dispatch the pending invalidation, without another database event'
        listener.release.countDown()
        def delivered = (1..(limit + 1)).collect { next() }
        then:
        def initial = delivered.findAll { it.operation() == ChangeOperation.INSERT }
        initial.size() == limit
        initial.collect { it.metadata(PostgresChangeEventMetadata).orElseThrow().newKeys().BookId }.toSet() ==
            (1..limit).collect { it.toString() }.toSet()
        def invalidations = delivered.findAll { it.operation() == ChangeOperation.INVALIDATE }
        invalidations.size() == 1
        invalidations.first().entity().isEmpty()
        invalidations.first().metadata(PostgresChangeEventMetadata).isEmpty()
        listener.attempts.get() == limit + 1
        listener.events.isEmpty()

        when: 'the listener catches up and a later change is delivered individually'
        new PollingConditions(timeout: 10).eventually {
            // The invalidation is recorded just before its callback returns. A write racing with
            // that return may still collapse at limit one; keep checking observable row delivery.
            execute('UPDATE "ProviderBook" SET title = \'resumed\' WHERE "BookId" = 1')
            def resumed = next()
            assert resumed.operation() == ChangeOperation.UPDATE
            assert resumed.metadata(PostgresChangeEventMetadata).orElseThrow().newKeys() == [tenant: TENANT, BookId: '1']
            assert resumed.entity().orElseThrow().title == 'resumed'
        }

        then:
        listener.attempts.get() >= limit + 2
        listener.events.poll(300, TimeUnit.MILLISECONDS) == null

        where:
        limit << [1, 2]
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

    @Unroll
    void "unusable payload invalidates without terminating reception: #description"() {
        given:
        start()

        when:
        try (def statement = writer.prepareStatement('SELECT pg_notify(?, ?)')) {
            statement.setString(1, 'provider_changes')
            statement.setString(2, payload)
            statement.execute()
        }

        then:
        next().operation() == ChangeOperation.INVALIDATE

        when:
        execute("INSERT INTO \"ProviderBook\" VALUES ('$TENANT', 3, 'healthy')")

        then:
        next().entity().orElseThrow().title == 'healthy'

        where:
        description           | payload
        'invalid JSON'        | 'invalid JSON'
        'unsupported version' | '{"version":2}'
        'wrong key names'     | '{"version":1,"schema":"notification_provider_test","table":"ProviderBook","operation":"INSERT","oldKeys":{},"newKeys":{"unexpected":"1"}}'
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
        def events = [next(), next()]

        then:
        events*.operation().toSet() == [ChangeOperation.INSERT, ChangeOperation.DELETE].toSet()
        events.find { it.operation() == ChangeOperation.INSERT }.entity().isEmpty()
    }

    void "shutdown lifecycle starts cleanup asynchronously and waits for running callbacks"() {
        given:
        start()
        listener.entered = new CountDownLatch(1)
        listener.release = new CountDownLatch(1)
        execute('TRUNCATE "ProviderBook"')
        assert listener.entered.await(10, TimeUnit.SECONDS)
        def provider = application.getBeansOfType(ChangeNotificationProvider)
                .find { it.class.simpleName == 'PostgresChangeNotificationProvider' }

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
                'datasources.default.url'  : url + (url.contains('?') ? '&' : '?') + 'currentSchema=notification_provider_test'
        ])
        def unqualified = application.getBean(UnqualifiedBookChangeListener)
        assert unqualified.events.poll(300, TimeUnit.MILLISECONDS) == null
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
    @Id
    UUID tenant
    @Id
    @MappedProperty('BookId')
    Long id
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

@Singleton
@Requires(property = 'spec.name', value = 'PostgresNotificationProviderSpec')
@Requires(property = 'spec.overload-probe', value = 'true')
class OverloadNotificationProbe {
    final LinkedBlockingQueue<ChangeEvent<NotificationBook>> events = new LinkedBlockingQueue<>()

    @ChangeListener
    @PostgresChangeNotification(channel = 'provider_probe')
    void changed(ChangeEvent<NotificationBook> event) {
        events.add(event)
    }
}
