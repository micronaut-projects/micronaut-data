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
package io.micronaut.data.jdbc.notification.postgres.integration.provider

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.context.event.ShutdownEvent
import io.micronaut.data.annotation.ChangeListener
import io.micronaut.data.annotation.Embeddable
import io.micronaut.data.annotation.EmbeddedId
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.data.annotation.MappedProperty
import io.micronaut.data.annotation.Where
import io.micronaut.data.connection.jdbc.advice.DelegatingDataSource
import io.micronaut.data.exceptions.DataAccessException
import io.micronaut.data.jdbc.annotation.PostgresChangeNotification
import io.micronaut.data.jdbc.notification.ChangeListenerException
import io.micronaut.data.jdbc.notification.ChangeListenerExceptionHandler
import io.micronaut.data.jdbc.notification.ChangeListenerRegistry
import io.micronaut.data.jdbc.notification.ChangeNotificationProvider
import io.micronaut.data.jdbc.notification.postgres.PostgresChangeEventMetadata
import io.micronaut.data.jdbc.postgres.PostgresTestPropertyProvider
import io.micronaut.data.notification.ChangeEvent
import io.micronaut.data.notification.ChangeOperation
import io.micronaut.data.runtime.config.SchemaGenerate
import io.micronaut.jdbc.BasicJdbcConfiguration
import jakarta.inject.Singleton
import spock.lang.Specification
import spock.lang.Unroll
import spock.util.concurrent.PollingConditions

import javax.sql.DataSource
import java.sql.Connection
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

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
        execute(getClass().getResource('/io/micronaut/data/jdbc/notification/postgres/sql/create-notification-function.sql').getText('UTF-8'))
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
                'spec.name'                                                                 : 'PostgresNotificationProviderSpec',
                'micronaut.data.jdbc.notifications.postgres.poll-timeout'                   : '100ms',
                'micronaut.data.jdbc.notifications.postgres.connection-recovery.retry-delay': '100ms'
        ] + additionalSettings)
        listener = application.getBean(BookChangeListener)
    }

    @Unroll
    void "global notification switch controls PostgreSQL reception with enabled=#enabled"() {
        given:
        def applicationName = "postgres-notifications-enabled-$enabled"
        start([
                'micronaut.data.jdbc.notifications.enabled'        : enabled,
                'datasources.default.db-properties.ApplicationName': applicationName
        ])

        expect:
        application.running
        listener != null
        application.getBean(ChangeListenerRegistry).getListeners('default').size() == 1
        application.getActiveBeanRegistrations(ChangeNotificationProvider).empty == !enabled
        listeningConnectionCount(applicationName) == (enabled ? 1 : 0)

        when: 'ordinary JDBC access remains available regardless of the notification switch'
        execute("INSERT INTO \"ProviderBook\" VALUES ('$TENANT', 1, 'committed row')")
        String title
        try (def connection = DelegatingDataSource.unwrapDataSource(application.getBean(DataSource)).connection;
             def statement = connection.createStatement();
             def rows = statement.executeQuery('SELECT title FROM notification_provider_test."ProviderBook" WHERE "BookId" = 1')) {
            assert rows.next()
            title = rows.getString(1)
        }
        def event = listener.events.poll(enabled ? 10000 : 300, TimeUnit.MILLISECONDS)

        then:
        title == 'committed row'
        (event != null) == enabled
        listener.attempts.get() == (enabled ? 1 : 0)
        listeningConnectionCount(applicationName) == (enabled ? 1 : 0)
        if (enabled) {
            assert event.operation() == ChangeOperation.INSERT
            assert event.entity().orElseThrow().title == 'committed row'
            assert event.metadata(PostgresChangeEventMetadata).orElseThrow().newKeys() == [tenant: TENANT, BookId: '1']
        }

        where:
        enabled << [false, true]
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

    void "embedded primary keys load entities and retain old and new key components"() {
        given:
        start(['spec.embedded-listener': 'true'])
        def embedded = application.getBean(EmbeddedBookChangeListener)
        def tenant = UUID.fromString(TENANT)
        def otherTenant = UUID.fromString('9ae24496-5404-498c-9c43-ed6267a1284d')
        execute("INSERT INTO \"ProviderBook\" VALUES ('$otherTenant', 41, 'other tenant')")
        assert embedded.events.poll(10, TimeUnit.SECONDS).entity().orElseThrow().id.tenant == otherTenant

        when:
        execute("INSERT INTO \"ProviderBook\" VALUES ('$TENANT', 41, 'first')")
        def inserted = embedded.events.poll(10, TimeUnit.SECONDS)

        then:
        inserted != null
        inserted.operation() == ChangeOperation.INSERT
        inserted.entity().orElseThrow().id.tenant == tenant
        inserted.entity().orElseThrow().id.number == 41L
        inserted.entity().orElseThrow().title == 'first'
        inserted.metadata(PostgresChangeEventMetadata).orElseThrow().oldKeys() == [:]
        inserted.metadata(PostgresChangeEventMetadata).orElseThrow().newKeys() == [tenant: TENANT, BookId: '41']

        when:
        execute("UPDATE \"ProviderBook\" SET tenant = '$otherTenant', \"BookId\" = 42, title = 'second' WHERE tenant = '$TENANT'")
        def updated = embedded.events.poll(10, TimeUnit.SECONDS)

        then:
        updated != null
        updated.operation() == ChangeOperation.UPDATE
        updated.entity().orElseThrow().id.tenant == otherTenant
        updated.entity().orElseThrow().id.number == 42L
        updated.entity().orElseThrow().title == 'second'
        updated.metadata(PostgresChangeEventMetadata).orElseThrow().oldKeys() == [tenant: TENANT, BookId: '41']
        updated.metadata(PostgresChangeEventMetadata).orElseThrow().newKeys() == [tenant: otherTenant.toString(), BookId: '42']

        when:
        execute('DELETE FROM "ProviderBook" WHERE "BookId" = 42')
        def deleted = embedded.events.poll(10, TimeUnit.SECONDS)

        then:
        deleted != null
        deleted.operation() == ChangeOperation.DELETE
        deleted.entity().isEmpty()
        deleted.metadata(PostgresChangeEventMetadata).orElseThrow().oldKeys() == [tenant: otherTenant.toString(), BookId: '42']
        deleted.metadata(PostgresChangeEventMetadata).orElseThrow().newKeys() == [:]
        embedded.events.poll(300, TimeUnit.MILLISECONDS) == null
    }

    void "static entity filters preserve keys when rows enter and leave the cached result"() {
        given:
        start(['spec.filtered-listener': 'true'])
        def filtered = application.getBean(FilteredBookChangeListener)
        def keys = [tenant: TENANT, BookId: '41']
        def retainedKeys = [tenant: TENANT, BookId: '42']
        execute("INSERT INTO \"ProviderBook\" VALUES ('$TENANT', 42, 'visible retained')")
        assert filtered.events.poll(10, TimeUnit.SECONDS).entity().orElseThrow().title == 'visible retained'
        assert next().entity().orElseThrow().title == 'visible retained'

        when: 'a trigger reports an inserted row that does not match the reload filter'
        execute("INSERT INTO \"ProviderBook\" VALUES ('$TENANT', 41, 'hidden')")
        def inserted = filtered.events.poll(10, TimeUnit.SECONDS)

        then:
        inserted != null
        inserted.operation() == ChangeOperation.INSERT
        inserted.entity().empty
        inserted.metadata(PostgresChangeEventMetadata).orElseThrow().oldKeys() == [:]
        inserted.metadata(PostgresChangeEventMetadata).orElseThrow().newKeys() == keys
        next().entity().orElseThrow().title == 'hidden'
        filtered.books.keySet() == [retainedKeys] as Set

        when: 'the row enters the filtered result'
        execute('UPDATE "ProviderBook" SET title = \'visible first\' WHERE "BookId" = 41')
        def entered = filtered.events.poll(10, TimeUnit.SECONDS)

        then:
        entered != null
        entered.operation() == ChangeOperation.UPDATE
        entered.entity().orElseThrow().title == 'visible first'
        entered.metadata(PostgresChangeEventMetadata).orElseThrow().oldKeys() == keys
        entered.metadata(PostgresChangeEventMetadata).orElseThrow().newKeys() == keys
        next().entity().orElseThrow().title == 'visible first'
        filtered.books.keySet() == [retainedKeys, keys] as Set
        filtered.books[keys].title == 'visible first'

        when: 'the row leaves the filter without being deleted or changing its primary key'
        execute('UPDATE "ProviderBook" SET title = \'hidden again\' WHERE "BookId" = 41')
        def left = filtered.events.poll(10, TimeUnit.SECONDS)

        then:
        left != null
        left.operation() == ChangeOperation.UPDATE
        left.entity().empty
        left.metadata(PostgresChangeEventMetadata).orElseThrow().oldKeys() == keys
        left.metadata(PostgresChangeEventMetadata).orElseThrow().newKeys() == keys
        next().entity().orElseThrow().title == 'hidden again'
        filtered.books.keySet() == [retainedKeys] as Set
        filtered.books[retainedKeys].title == 'visible retained'

        when: 'the same row re-enters the filter under a new primary key'
        execute('UPDATE "ProviderBook" SET "BookId" = 43, title = \'visible returned\' WHERE "BookId" = 41')
        def reentered = filtered.events.poll(10, TimeUnit.SECONDS)
        def movedKeys = [tenant: TENANT, BookId: '43']

        then:
        reentered != null
        reentered.operation() == ChangeOperation.UPDATE
        reentered.entity().orElseThrow().id == 43L
        reentered.entity().orElseThrow().title == 'visible returned'
        reentered.metadata(PostgresChangeEventMetadata).orElseThrow().oldKeys() == keys
        reentered.metadata(PostgresChangeEventMetadata).orElseThrow().newKeys() == movedKeys
        next().entity().orElseThrow().id == 43L
        filtered.books.keySet() == [retainedKeys, movedKeys] as Set
        filtered.books[movedKeys].title == 'visible returned'

        when: 'a matching row is deleted'
        execute('DELETE FROM "ProviderBook" WHERE "BookId" = 43')
        def deleted = filtered.events.poll(10, TimeUnit.SECONDS)

        then:
        deleted != null
        deleted.operation() == ChangeOperation.DELETE
        deleted.entity().empty
        deleted.metadata(PostgresChangeEventMetadata).orElseThrow().oldKeys() == movedKeys
        deleted.metadata(PostgresChangeEventMetadata).orElseThrow().newKeys() == [:]
        next().operation() == ChangeOperation.DELETE
        filtered.books.keySet() == [retainedKeys] as Set
        filtered.events.poll(300, TimeUnit.MILLISECONDS) == null
    }

    void "bulk changes load every affected entity"() {
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
    void "queue overflow replaces waiting data and row delivery resumes with capacity #capacity"() {
        given:
        start([
                'micronaut.data.jdbc.notifications.postgres.queue-capacity': capacity,
                'spec.overload-probe'                                      : true
        ])
        listener.entered = new CountDownLatch(1)
        listener.release = new CountDownLatch(1)
        def probe = application.getBean(OverloadNotificationProbe)
        execute("INSERT INTO \"ProviderBook\" VALUES ('$TENANT', 1, 'initial')")
        assert listener.entered.await(10, TimeUnit.SECONDS)

        when: 'one callback is running while a committed bulk insert fills the waiting queue'
        execute("""INSERT INTO "ProviderBook"
            SELECT '$TENANT'::uuid, id, 'burst' FROM generate_series(100, 109) AS id""")
        // This other listener confirms the receiver has processed the burst without waiting for our callback.
        notifyProbe()

        then:
        probe.events.poll(10, TimeUnit.SECONDS) != null
        listener.attempts.get() == 1
        listener.events.empty

        when:
        listener.release.countDown()
        def initial = next()
        def invalidation = next()

        then:
        initial.operation() == ChangeOperation.INSERT
        initial.metadata(PostgresChangeEventMetadata).orElseThrow().newKeys().BookId == '1'
        invalidation.operation() == ChangeOperation.INVALIDATE
        invalidation.entity().empty
        invalidation.metadata(PostgresChangeEventMetadata).empty
        listener.attempts.get() == 2
        listener.events.empty

        when: 'a notification arriving during or after reconciliation is queued normally'
        execute('UPDATE "ProviderBook" SET title = \'resumed\' WHERE "BookId" = 1')
        def resumed = next()

        then:
        resumed.operation() == ChangeOperation.UPDATE
        resumed.entity().orElseThrow().title == 'resumed'
        listener.events.poll(300, TimeUnit.MILLISECONDS) == null

        where:
        capacity << [2, 3]
    }

    void "recovery invalidation replaces queued row changes after the running callback finishes"() {
        given:
        start(['spec.overload-probe': true])
        listener.entered = new CountDownLatch(1)
        listener.release = new CountDownLatch(1)
        def probe = application.getBean(OverloadNotificationProbe)
        execute("INSERT INTO \"ProviderBook\" VALUES ('$TENANT', 1, 'older')")
        assert listener.entered.await(10, TimeUnit.SECONDS)
        execute('UPDATE "ProviderBook" SET title = \'newer\' WHERE "BookId" = 1')
        notifyProbe()
        assert probe.events.poll(10, TimeUnit.SECONDS) != null
        assert listener.attempts.get() == 1

        when:
        terminateListeningConnection()
        def recovered = probe.events.poll(10, TimeUnit.SECONDS)

        then:
        recovered != null
        recovered.operation() == ChangeOperation.INVALIDATE
        recovered.metadata(PostgresChangeEventMetadata).empty

        when: 'a probe received after reconnect confirms every listener has been invalidated'
        notifyProbe()

        then:
        probe.events.poll(10, TimeUnit.SECONDS).metadata(PostgresChangeEventMetadata).present
        listener.attempts.get() == 1
        listener.events.empty

        when:
        listener.release.countDown()
        def first = next()
        def invalidation = next()

        then:
        first.operation() == ChangeOperation.INSERT
        first.entity().orElseThrow().title == 'older'
        invalidation.operation() == ChangeOperation.INVALIDATE
        invalidation.metadata(PostgresChangeEventMetadata).empty
        listener.events.poll(300, TimeUnit.MILLISECONDS) == null
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

    void "unhandled listener failures reach the application handler after retries"() {
        given:
        start(['spec.exception-handler': true])
        def handler = application.getBean(RecordingNotificationExceptionHandler)
        listener.failures.set(3)

        when:
        execute("INSERT INTO \"ProviderBook\" VALUES ('$TENANT', 1, 'failed delivery')")
        def failure = handler.failures.poll(10, TimeUnit.SECONDS)

        then:
        failure != null
        failure.cause instanceof IllegalStateException
        failure.cause.message == 'Retry this notification'
        failure.dataSourceName == 'default'
        failure.listenerMethod.methodName == 'changed'
        failure.event.operation() == ChangeOperation.INSERT
        failure.event.entity().orElseThrow().title == 'failed delivery'
        failure.event.metadata(PostgresChangeEventMetadata).orElseThrow().newKeys() == [tenant: TENANT, BookId: '1']
        handler.threadName.startsWith('postgres-notification-listener-')
        next().operation() == ChangeOperation.INVALIDATE
        listener.attempts.get() == 4
        handler.failures.empty

        when:
        execute("INSERT INTO \"ProviderBook\" VALUES ('$TENANT', 2, 'later delivery')")

        then:
        next().entity().orElseThrow().title == 'later delivery'
        handler.failures.empty
    }

    void "entity loading failures reach the application handler with row metadata"() {
        given:
        start(['spec.exception-handler': true])
        def handler = application.getBean(RecordingNotificationExceptionHandler)
        execute('ALTER TABLE "ProviderBook" RENAME COLUMN title TO renamed_title')

        when:
        execute("INSERT INTO \"ProviderBook\" VALUES ('$TENANT', 1, 'unavailable entity')")
        def failure = handler.failures.poll(10, TimeUnit.SECONDS)

        then:
        failure != null
        failure.cause instanceof DataAccessException
        failure.event.operation() == ChangeOperation.INSERT
        failure.event.entity().empty
        failure.event.metadata(PostgresChangeEventMetadata).orElseThrow().newKeys() == [tenant: TENANT, BookId: '1']
        next().operation() == ChangeOperation.INVALIDATE
        listener.attempts.get() == 1
        handler.failures.empty

        when:
        execute('ALTER TABLE "ProviderBook" RENAME COLUMN renamed_title TO title')
        execute('UPDATE "ProviderBook" SET title = \'recovered entity\'')

        then:
        next().entity().orElseThrow().title == 'recovered entity'
        handler.failures.empty
    }

    void "terminated listening session reconnects and reconciles before new changes"() {
        given:
        start()

        when:
        terminateListeningConnection()

        then:
        next().operation() == ChangeOperation.INVALIDATE

        when:
        execute("INSERT INTO \"ProviderBook\" VALUES ('$TENANT', 2, 'recovered')")

        then:
        next().entity().orElseThrow().title == 'recovered'
    }

    @Unroll
    void "short-lived listening sessions terminate recovery after #maxRetries retries"() {
        given:
        start([
                'micronaut.data.jdbc.notifications.postgres.connection-recovery.max-retries': maxRetries
        ])

        when:
        terminateListeningConnection()
        (0..maxRetries).each {
            assert next().operation() == ChangeOperation.INVALIDATE
            terminateListeningConnection()
        }

        then:
        next().operation() == ChangeOperation.TERMINATED
        listener.events.poll(300, TimeUnit.MILLISECONDS) == null

        where:
        maxRetries << [0, 2]
    }

    @Unroll
    void "exhausted recovery delivers final termination to every listener after #maxRetries retries"() {
        given:
        start([
                'spec.unqualified-listener'                                                 : true,
                'micronaut.data.jdbc.notifications.postgres.connection-recovery.max-retries': maxRetries
        ])
        def other = application.getBean(UnqualifiedBookChangeListener)
        def configuration = application.getBean(BasicJdbcConfiguration)
        def originalPassword = configuration.password
        configuration.password = 'invalid_notification_password'

        when:
        terminateListeningConnection()
        def terminated = next()
        def otherTerminated = other.events.poll(10, TimeUnit.SECONDS)

        then:
        [terminated, otherTerminated].every {
            it != null && it.operation() == ChangeOperation.TERMINATED && it.entity().empty &&
                    it.metadata(PostgresChangeEventMetadata).empty
        }

        when: 'restoring credentials cannot restart a permanently stopped subscription'
        configuration.password = originalPassword
        execute("INSERT INTO \"ProviderBook\" VALUES ('$TENANT', 2, 'after termination')")

        then:
        listener.events.poll(500, TimeUnit.MILLISECONDS) == null
        other.events.poll(500, TimeUnit.MILLISECONDS) == null

        cleanup:
        if (configuration != null) {
            configuration.password = originalPassword
        }

        where:
        maxRetries << [0, 2]
    }

    @Unroll
    void "unusable payload invalidates only its channel and preserves reception: #description"() {
        given:
        def applicationName = 'notification-invalid-payload'
        String url = writer.metaData.URL
        start([
                'spec.unqualified-listener'                        : true,
                'spec.overload-probe'                              : true,
                'datasources.default.url'                          : url + (url.contains('?') ? '&' : '?') + 'currentSchema=notification_provider_test',
                'datasources.default.db-properties.ApplicationName': applicationName
        ])
        def other = application.getBean(UnqualifiedBookChangeListener)
        def probe = application.getBean(OverloadNotificationProbe)
        int receiverPid = listeningConnectionPid(applicationName)

        when:
        try (def statement = writer.prepareStatement('SELECT pg_notify(?, ?)')) {
            statement.setString(1, 'provider_changes')
            statement.setString(2, payload)
            statement.execute()
        }

        then:
        next().operation() == ChangeOperation.INVALIDATE
        other.events.poll(10, TimeUnit.SECONDS)?.operation() == ChangeOperation.INVALIDATE
        connectionExists(receiverPid)

        when:
        notifyProbe()
        execute("INSERT INTO \"ProviderBook\" VALUES ('$TENANT', 3, 'healthy')")

        then:
        probe.events.poll(10, TimeUnit.SECONDS)?.metadata(PostgresChangeEventMetadata)?.present
        next().entity().orElseThrow().title == 'healthy'
        other.events.poll(10, TimeUnit.SECONDS)?.entity()?.orElseThrow()?.title == 'healthy'
        connectionExists(receiverPid)

        where:
        description           | payload
        'invalid JSON'        | 'invalid JSON'
        'deeply nested JSON'  | '{"a":' + '[' * 501 + ']' * 501 + '}'
        'unsupported version' | '{"version":2}'
        'wrong key names'     | '{"version":1,"schema":"notification_provider_test","table":"ProviderBook","operation":"INSERT","oldKeys":{},"newKeys":{"unexpected":"1"}}'
        'termination payload' | '{"version":1,"schema":"notification_provider_test","table":"ProviderBook","operation":"TERMINATED","oldKeys":{},"newKeys":{}}'
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

    void "graceful shutdown starts cleanup asynchronously and waits for running callbacks"() {
        given:
        start(['micronaut.lifecycle.graceful-shutdown.enabled': true])
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

    @Unroll
    void "context close waits for notification cleanup before destroying dependencies with graceful shutdown #enabled"() {
        given:
        def applicationName = "notification-context-shutdown-$enabled"
        start([
                'micronaut.lifecycle.graceful-shutdown.enabled'     : enabled,
                'micronaut.lifecycle.graceful-shutdown.grace-period': '5s',
                'datasources.default.db-properties.ApplicationName' : applicationName
        ])
        def dataSource = DelegatingDataSource.unwrapDataSource(application.getBean(DataSource))
        def provider = application.getBeansOfType(ChangeNotificationProvider)
                .find { it.class.simpleName == 'PostgresChangeNotificationProvider' }
        int receiverPid
        try (def statement = writer.prepareStatement('SELECT pid FROM pg_stat_activity WHERE application_name = ? AND query = ?')) {
            statement.setString(1, applicationName)
            statement.setString(2, 'LISTEN "provider_changes"')
            try (def rows = statement.executeQuery()) {
                assert rows.next()
                receiverPid = rows.getInt(1)
                assert !rows.next()
            }
        }
        listener.entered = new CountDownLatch(1)
        listener.release = new CountDownLatch(1)
        execute('TRUNCATE "ProviderBook"')
        assert listener.entered.await(10, TimeUnit.SECONDS)
        def executor = Executors.newSingleThreadExecutor()

        when:
        def closed = executor.submit { application.close() }
        new PollingConditions(timeout: 5).eventually {
            assert provider.reportActiveTasks().present
        }
        closed.get(100, TimeUnit.MILLISECONDS)

        then:
        thrown(TimeoutException)

        when:
        try (def connection = dataSource.connection;
             def statement = connection.createStatement();
             def rows = statement.executeQuery('SELECT 1')) {
            assert rows.next() && rows.getInt(1) == 1
        }
        listener.release.countDown()
        closed.get(10, TimeUnit.SECONDS)

        then:
        provider.shutdownGracefully().toCompletableFuture().done
        provider.reportActiveTasks().orElseThrow() == 0
        next().operation() == ChangeOperation.INVALIDATE
        new PollingConditions(timeout: 5).eventually {
            assert !connectionExists(receiverPid)
        }

        cleanup:
        listener?.release?.countDown()
        executor?.shutdownNow()

        where:
        enabled << [false, true]
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

    private void notifyProbe() {
        execute("""SELECT pg_notify('provider_probe', '{"version":1,"schema":"notification_provider_test",
            "table":"ProviderBook","operation":"INVALIDATE","oldKeys":{},"newKeys":{}}')""")
    }

    private void terminateListeningConnection() {
        try (def statement = writer.prepareStatement("SELECT pg_terminate_backend(pid) FROM pg_stat_activity WHERE datname = current_database() AND query LIKE ? AND pid <> pg_backend_pid()")) {
            statement.setString(1, 'LISTEN %')
            try (def rows = statement.executeQuery()) {
                assert rows.next() && rows.getBoolean(1)
            }
        }
    }

    private boolean connectionExists(int pid) {
        try (def statement = writer.prepareStatement('SELECT 1 FROM pg_stat_activity WHERE pid = ?')) {
            statement.setInt(1, pid)
            try (def rows = statement.executeQuery()) {
                return rows.next()
            }
        }
    }

    private int listeningConnectionPid(String applicationName) {
        try (def statement = writer.prepareStatement("SELECT pid FROM pg_stat_activity WHERE application_name = ? AND query LIKE 'LISTEN %'")) {
            statement.setString(1, applicationName)
            try (def rows = statement.executeQuery()) {
                assert rows.next()
                int pid = rows.getInt(1)
                assert !rows.next()
                return pid
            }
        }
    }

    private int listeningConnectionCount(String applicationName) {
        try (def statement = writer.prepareStatement('SELECT count(*) FROM pg_stat_activity WHERE application_name = ? AND query = ?')) {
            statement.setString(1, applicationName)
            statement.setString(2, 'LISTEN "provider_changes"')
            try (def rows = statement.executeQuery()) {
                assert rows.next()
                return rows.getInt(1)
            }
        }
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

@Singleton
@Requires(property = 'spec.name', value = 'PostgresNotificationProviderSpec')
@Requires(property = 'spec.exception-handler', value = 'true')
class RecordingNotificationExceptionHandler implements ChangeListenerExceptionHandler {
    final LinkedBlockingQueue<ChangeListenerException> failures = new LinkedBlockingQueue<>()
    volatile String threadName

    @Override
    void handle(ChangeListenerException failure) {
        threadName = Thread.currentThread().name
        failures.add(failure)
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

@Embeddable
class NotificationBookId {
    UUID tenant
    @MappedProperty('BookId')
    Long number
}

@MappedEntity(value = 'ProviderBook', schema = 'notification_provider_test')
class EmbeddedNotificationBook {
    @EmbeddedId
    NotificationBookId id
    String title
}

@Singleton
@Requires(property = 'spec.name', value = 'PostgresNotificationProviderSpec')
@Requires(property = 'spec.embedded-listener', value = 'true')
class EmbeddedBookChangeListener {
    final LinkedBlockingQueue<ChangeEvent<EmbeddedNotificationBook>> events = new LinkedBlockingQueue<>()

    @ChangeListener
    @PostgresChangeNotification(channel = 'provider_changes')
    void changed(ChangeEvent<EmbeddedNotificationBook> event) {
        events.add(event)
    }
}

@MappedEntity(value = 'ProviderBook', schema = 'notification_provider_test')
@Where("title LIKE 'visible%'")
class FilteredNotificationBook {
    @Id
    UUID tenant
    @Id
    @MappedProperty('BookId')
    Long id
    String title
}

@Singleton
@Requires(property = 'spec.name', value = 'PostgresNotificationProviderSpec')
@Requires(property = 'spec.filtered-listener', value = 'true')
class FilteredBookChangeListener {
    final Map<Map<String, String>, FilteredNotificationBook> books = new ConcurrentHashMap<>()
    final LinkedBlockingQueue<ChangeEvent<FilteredNotificationBook>> events = new LinkedBlockingQueue<>()

    @ChangeListener
    @PostgresChangeNotification(channel = 'provider_changes')
    void changed(ChangeEvent<FilteredNotificationBook> event) {
        def metadata = event.metadata(PostgresChangeEventMetadata).orElseThrow()
        books.remove(metadata.oldKeys())
        event.entity().ifPresent { books.put(metadata.newKeys(), it) }
        events.add(event)
    }
}
