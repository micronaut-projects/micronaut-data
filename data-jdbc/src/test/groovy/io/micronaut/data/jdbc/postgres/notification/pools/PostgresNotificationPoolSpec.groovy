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
package io.micronaut.data.jdbc.postgres.notification.pools

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Factory
import io.micronaut.context.annotation.Primary
import io.micronaut.context.annotation.Requires
import io.micronaut.context.env.Environment
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.data.connection.jdbc.advice.DelegatingDataSource
import io.micronaut.data.jdbc.annotation.ChangeListener
import io.micronaut.data.jdbc.annotation.PostgresChangeNotification
import io.micronaut.data.jdbc.notification.ChangeEvent
import io.micronaut.data.jdbc.notification.ChangeNotificationProvider
import io.micronaut.data.jdbc.notification.ChangeOperation
import io.micronaut.data.jdbc.postgres.PostgresTestPropertyProvider
import io.micronaut.data.runtime.config.SchemaGenerate
import io.micronaut.inject.qualifiers.Qualifiers
import jakarta.annotation.PreDestroy
import jakarta.inject.Named
import jakarta.inject.Singleton
import org.apache.tomcat.jdbc.pool.PoolProperties
import spock.lang.Specification
import spock.lang.Unroll
import spock.util.concurrent.PollingConditions

import javax.sql.DataSource
import java.sql.Connection
import java.sql.DriverManager
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

class PostgresNotificationPoolSpec extends Specification implements PostgresTestPropertyProvider {
    private ApplicationContext bootstrap
    private ApplicationContext application
    private Connection writer
    private Connection secondaryWriter
    private String database
    private Map settings

    @Override
    SchemaGenerate schemaGenerate() { SchemaGenerate.NONE }

    void setup() {
        settings = properties
        bootstrap = ApplicationContext.run(settings)
        writer = DelegatingDataSource.unwrapDataSource(bootstrap.getBean(DataSource)).connection
        database = 'notification_' + UUID.randomUUID().toString().replace('-', '')
        execute(writer, 'CREATE DATABASE ' + database)
        String url = writer.metaData.URL.replace('/' + writer.catalog, '/' + database)
        String username = bootstrap.environment.getRequiredProperty('datasources.default.username', String)
        String password = bootstrap.environment.getRequiredProperty('datasources.default.password', String)
        secondaryWriter = DriverManager.getConnection(url, username, password)
        settings += [
            'spec.pool.url': writer.metaData.URL,
            'spec.pool.secondary-url': url,
            'spec.pool.username': username,
            'spec.pool.password': password
        ]
        for (Connection connection : [writer, secondaryWriter]) {
            execute(connection, 'CREATE SCHEMA notification_pool_test')
            execute(connection, 'SET search_path TO notification_pool_test')
            execute(connection, getClass().getResource('/io/micronaut/data/jdbc/notification/postgres/create-notification-function.sql').text)
            execute(connection, 'CREATE TABLE notification_book (id bigint PRIMARY KEY, title text NOT NULL)')
            execute(connection, """CREATE TRIGGER changes AFTER INSERT ON notification_book FOR EACH ROW
                EXECUTE FUNCTION notification_pool_test.micronaut_data_notify_change('shared"changes', 'id')""")
        }
    }

    void cleanup() {
        if (application?.isRunning()) {
            provider().shutdownGracefully().toCompletableFuture().get(10, TimeUnit.SECONDS)
        }
        application?.close()
        secondaryWriter?.close()
        if (writer != null) {
            execute(writer, 'DROP SCHEMA IF EXISTS notification_pool_test CASCADE')
            if (database != null) {
                execute(writer, 'DROP DATABASE ' + database + ' WITH (FORCE)')
            }
            writer.close()
        }
        bootstrap?.close()
    }

    @Unroll
    void "#pool shares one connection per datasource and releases subscriptions before pool reuse"() {
        given:
        Map configuration = settings + getDataSourceProperties('secondary') + [
            'spec.name': 'PostgresNotificationPoolSpec',
            'spec.pool.type': pool,
            'datasources.default.enabled': false,
            'datasources.secondary.enabled': false,
            'micronaut.data.jdbc.notifications.postgres.poll-timeout': '100ms',
            'micronaut.data.jdbc.notifications.postgres.retry-delay': '100ms'
        ]
        application = ApplicationContext.run(configuration)
        def listener = application.getBean(PoolBookListeners)
        DataSource primary = poolDataSource('default')
        DataSource secondary = poolDataSource('secondary')

        expect:
        new PollingConditions(timeout: 10).eventually {
            assert active(primary) == 1
            assert active(secondary) == 1
        }
        listener.first.poll(200, TimeUnit.MILLISECONDS) == null
        listener.second.isEmpty()
        listener.secondary.isEmpty()

        when: 'both methods sharing a channel receive changes from the default datasource'
        execute(writer, "INSERT INTO notification_book VALUES (1, 'primary')")

        then:
        next(listener.first).entity().orElseThrow().title == 'primary'
        next(listener.second).entity().orElseThrow().title == 'primary'
        listener.secondary.poll(200, TimeUnit.MILLISECONDS) == null

        when: 'the same channel, table, and ID in the other database use the other datasource'
        execute(secondaryWriter, "INSERT INTO notification_book VALUES (1, 'secondary')")

        then:
        next(listener.secondary).entity().orElseThrow().title == 'secondary'
        listener.first.poll(200, TimeUnit.MILLISECONDS) == null
        listener.second.isEmpty()

        when: 'one datasource loses its listening session'
        try (def statement = writer.prepareStatement("SELECT pg_terminate_backend(pid) FROM pg_stat_activity WHERE application_name = ? AND query = ?")) {
            statement.setString(1, 'notification-pool-default')
            statement.setString(2, 'LISTEN "shared""changes"')
            try (def rows = statement.executeQuery()) {
                assert rows.next() && rows.getBoolean(1)
                assert !rows.next()
            }
        }

        then: 'both affected listeners reconcile while the other datasource stays subscribed'
        next(listener.first).operation() == ChangeOperation.INVALIDATE
        next(listener.second).operation() == ChangeOperation.INVALIDATE
        listener.secondary.poll(200, TimeUnit.MILLISECONDS) == null

        when:
        execute(writer, "INSERT INTO notification_book VALUES (2, 'reconnected')")
        execute(secondaryWriter, "INSERT INTO notification_book VALUES (2, 'still connected')")

        then:
        next(listener.first).entity().orElseThrow().title == 'reconnected'
        next(listener.second).entity().orElseThrow().title == 'reconnected'
        next(listener.secondary).entity().orElseThrow().title == 'still connected'

        when:
        def notifications = provider()
        notifications.shutdownGracefully().toCompletableFuture().get(10, TimeUnit.SECONDS)

        then:
        notifications.reportActiveTasks().orElseThrow() == 0
        active(primary) == 0
        active(secondary) == 0
        assertReusable(primary)
        assertReusable(secondary)

        where:
        pool << ['tomcat', 'hikari']
    }

    private ChangeNotificationProvider provider() {
        application.getBeansOfType(ChangeNotificationProvider).find { it.class.simpleName == 'PostgresChangeNotificationProvider' }
    }

    private DataSource poolDataSource(String name) {
        DelegatingDataSource.unwrapDataSource(application.getBean(DataSource, Qualifiers.byName(name)))
    }

    private static int active(DataSource source) {
        source instanceof HikariDataSource ? source.hikariPoolMXBean.activeConnections : source.active
    }

    private static ChangeEvent<PoolBook> next(LinkedBlockingQueue<ChangeEvent<PoolBook>> events) {
        def event = events.poll(10, TimeUnit.SECONDS)
        assert event != null
        event
    }

    private static void assertReusable(DataSource source) {
        // Borrow every pool slot together so the former LISTEN connection must also be inspected.
        try (Connection first = source.connection; Connection second = source.connection) {
            for (Connection connection : [first, second]) {
                try (def statement = connection.createStatement(); def rows = statement.executeQuery('SELECT pg_listening_channels()')) {
                    assert !rows.next()
                }
                try (def statement = connection.createStatement(); def rows = statement.executeQuery('SELECT 1')) {
                    assert rows.next() && rows.getInt(1) == 1
                }
            }
        }
    }

    private static void execute(Connection connection, String sql) {
        try (def statement = connection.createStatement()) {
            statement.execute(sql)
        }
    }
}

@MappedEntity(value = 'notification_book', schema = 'notification_pool_test')
class PoolBook {
    @Id Long id
    String title
}

@Singleton
@Requires(property = 'spec.name', value = 'PostgresNotificationPoolSpec')
class PoolBookListeners {
    final LinkedBlockingQueue<ChangeEvent<PoolBook>> first = new LinkedBlockingQueue<>()
    final LinkedBlockingQueue<ChangeEvent<PoolBook>> second = new LinkedBlockingQueue<>()
    final LinkedBlockingQueue<ChangeEvent<PoolBook>> secondary = new LinkedBlockingQueue<>()

    @ChangeListener
    @PostgresChangeNotification(channel = 'shared"changes')
    void first(ChangeEvent<PoolBook> event) { first.add(event) }

    @ChangeListener
    @PostgresChangeNotification(channel = 'shared"changes')
    void second(ChangeEvent<PoolBook> event) { second.add(event) }

    @ChangeListener(dataSource = 'secondary')
    @PostgresChangeNotification(channel = 'shared"changes')
    void secondary(ChangeEvent<PoolBook> event) { secondary.add(event) }
}

@Factory
@Requires(property = 'spec.name', value = 'PostgresNotificationPoolSpec')
class NotificationPoolFactory {
    private final List<DataSource> pools = []

    @PreDestroy
    void close() {
        pools.each { it.close() }
    }

    private DataSource keep(DataSource source) {
        pools.add(source)
        source
    }

    @Singleton
    @Named('default')
    @Primary
    DataSource primary(Environment environment) { create(environment, 'default', 'url') }

    @Singleton
    @Named('secondary')
    DataSource secondary(Environment environment) { create(environment, 'secondary', 'secondary-url') }

    private DataSource create(Environment environment, String name, String urlProperty) {
        String url = environment.getRequiredProperty('spec.pool.' + urlProperty, String)
        url += (url.contains('?') ? '&' : '?') + 'ApplicationName=notification-pool-' + name
        String username = environment.getRequiredProperty('spec.pool.username', String)
        String password = environment.getRequiredProperty('spec.pool.password', String)
        if (environment.getRequiredProperty('spec.pool.type', String) == 'hikari') {
            HikariConfig configuration = new HikariConfig()
            configuration.jdbcUrl = url
            configuration.username = username
            configuration.password = password
            configuration.maximumPoolSize = 2
            configuration.minimumIdle = 0
            configuration.connectionTimeout = 5000
            return keep(new HikariDataSource(configuration))
        }
        PoolProperties configuration = new PoolProperties()
        configuration.url = url
        configuration.username = username
        configuration.password = password
        configuration.driverClassName = 'org.postgresql.Driver'
        configuration.maxActive = 2
        configuration.maxIdle = 2
        configuration.initialSize = 0
        configuration.minIdle = 0
        configuration.maxWait = 5000
        return keep(new org.apache.tomcat.jdbc.pool.DataSource(configuration))
    }
}
