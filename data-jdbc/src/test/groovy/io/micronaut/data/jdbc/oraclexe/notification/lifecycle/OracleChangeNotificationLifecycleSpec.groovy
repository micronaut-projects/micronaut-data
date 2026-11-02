package io.micronaut.data.jdbc.oraclexe.notification.lifecycle

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.context.event.BeanCreatedEvent
import io.micronaut.context.event.BeanCreatedEventListener
import io.micronaut.data.jdbc.notification.oracle.OracleChangeEventMetadata
import io.micronaut.data.notification.ChangeEvent
import io.micronaut.data.notification.ChangeOperation
import jakarta.inject.Singleton
import oracle.jdbc.OracleConnection
import oracle.jdbc.dcn.DatabaseChangeEvent
import oracle.jdbc.dcn.DatabaseChangeListener
import oracle.jdbc.dcn.DatabaseChangeRegistration
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.utility.DockerImageName
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification
import spock.util.concurrent.PollingConditions

import javax.sql.DataSource
import java.sql.Connection
import java.sql.DriverManager
import java.sql.SQLException
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/** Uses its own database so a real shutdown cannot affect other Oracle specs. */
class OracleChangeNotificationLifecycleSpec extends Specification {
    private static final AtomicLong BOOK_IDS = new AtomicLong()

    @Shared
    @AutoCleanup
    GenericContainer database

    @Shared
    String jdbcUrl

    ApplicationContext context
    boolean databaseStopped

    void setupSpec() {
        database = new GenericContainer(DockerImageName.parse('gvenzl/oracle-free:23.26.3-slim-faststart'))
            .withEnv('ORACLE_PASSWORD', 'test')
            .withEnv('APP_USER', 'test')
            .withEnv('APP_USER_PASSWORD', 'test')
            .withExposedPorts(1521)
            .waitingFor(Wait.forLogMessage('.*DATABASE IS READY TO USE!.*\\s', 1))
            .withStartupTimeout(Duration.ofMinutes(5))
        database.start()
        jdbcUrl = "jdbc:oracle:thin:@${database.host}:${database.getMappedPort(1521)}/freepdb1"
        administratorStatement('GRANT CHANGE NOTIFICATION TO test')
        administratorStatement('CREATE USER lifecycle_reader IDENTIFIED BY test')
        administratorStatement('GRANT CREATE SESSION, CHANGE NOTIFICATION TO lifecycle_reader')
        DriverManager.getConnection(jdbcUrl, 'test', 'test').withCloseable { connection ->
            connection.createStatement().withCloseable { statement ->
                statement.execute('CREATE TABLE notification_lifecycle_book (id NUMBER(19) PRIMARY KEY, title VARCHAR2(255) NOT NULL)')
            }
        }
    }

    void setup() {
        assert registrationIds().empty
        assert registrationIds('lifecycle_reader').empty
    }

    void cleanup() {
        if (databaseStopped) {
            startDatabase()
        }
        context?.close()
        assert registrationIds().empty
        assert registrationIds('lifecycle_reader').empty
    }

    void 'context close leaves no Oracle registrations with graceful shutdown #enabled'() {
        given:
        context = startContext([
            'oracle-lifecycle.query': 'true',
            'micronaut.lifecycle.graceful-shutdown.enabled': enabled
        ])
        assert registrationIds().size() == 2
        assert context.getBean(RegistrationProbe).options.values().count {
            it.getProperty(OracleConnection.DCN_QUERY_CHANGE_NOTIFICATION) == 'true'
        } == 1

        when:
        context.close()

        then:
        registrationIds().empty

        where:
        enabled << [false, true]
    }

    void 'a real Oracle shutdown and restart replaces the registration and resumes row delivery'() {
        given:
        context = startContext()
        def listener = context.getBean(LifecycleBookListener)
        def probe = context.getBean(RegistrationProbe)
        def repository = context.getBean(LifecycleBookRepository)
        def original = registrationIds().first()
        assertInsert(repository, listener, 'before shutdown')

        when:
        sysdba('SHUTDOWN IMMEDIATE;')
        databaseStopped = true
        new PollingConditions(timeout: 10).eventually {
            assert probe.events.contains(DatabaseChangeEvent.EventType.SHUTDOWN)
        }
        startDatabase()
        def recovered = listener.poll(30, TimeUnit.SECONDS)

        then:
        recovered.operation() == ChangeOperation.INVALIDATE
        recovered.entity().empty
        recovered.metadata(OracleChangeEventMetadata).empty
        def current = registrationIds()
        current.size() == 1
        !current.contains(original)

        when:
        assertInsert(repository, listener, 'after restart')
        context.close()

        then:
        registrationIds().empty
    }

    void 'loss of SELECT privilege delivers DEREG and terminates without replacement'() {
        given:
        administratorStatement('GRANT SELECT ON test.notification_lifecycle_book TO lifecycle_reader')
        context = startContext(['datasources.default.username': 'lifecycle_reader'])
        def listener = context.getBean(LifecycleBookListener)
        def probe = context.getBean(RegistrationProbe)
        assert registrationIds('lifecycle_reader').size() == 1

        when:
        // Explicit deregistration emits no callback; privilege loss exercises a real Oracle DEREG.
        administratorStatement('REVOKE SELECT ON test.notification_lifecycle_book FROM lifecycle_reader')
        def terminated = listener.poll(30, TimeUnit.SECONDS)

        then:
        terminated != null
        terminated.operation() == ChangeOperation.TERMINATED
        terminated.entity().empty
        terminated.metadata(OracleChangeEventMetadata).empty
        probe.events.contains(DatabaseChangeEvent.EventType.DEREG)
        probe.callbacks.size() == 1
        registrationIds('lifecycle_reader').empty

        when:
        DriverManager.getConnection(jdbcUrl, 'test', 'test').withCloseable { connection ->
            connection.createStatement().withCloseable { statement ->
                statement.executeUpdate("INSERT INTO test.notification_lifecycle_book (id, title) VALUES (1000000, 'after deregistration')")
            }
        }

        then:
        listener.poll(1, TimeUnit.SECONDS) == null
        registrationIds('lifecycle_reader').empty
    }

    void 'failed recovery unregister is retried during #retryAt'() {
        given:
        context = startContext()
        def listener = context.getBean(LifecycleBookListener)
        def repository = context.getBean(LifecycleBookRepository)
        def probe = context.getBean(RegistrationProbe)
        long original = registrationIds().first()
        probe.failRegistration.set(original)
        probe.failOnce.set(true)

        when:
        // Keep Oracle running so the failed unregister leaves a real database registration behind.
        shutdownCallback(probe, original)
        def recovered = listener.poll(10, TimeUnit.SECONDS)

        then:
        recovered.operation() == ChangeOperation.INVALIDATE
        registrationIds().size() == 2
        registrationIds().contains(original)
        probe.unregisterAttempts[original].get() == 1
        long replacement = (registrationIds() - [original]).first()

        when:
        // Both physical registrations can report this insert; only the replacement is accepted.
        assertInsert(repository, listener, 'after failed unregister')

        then:
        new PollingConditions(timeout: 10).eventually {
            assert probe.dataCallbacks[original]?.get() > 0
            assert probe.dataCallbacks[replacement]?.get() > 0
        }
        listener.poll(1, TimeUnit.SECONDS) == null

        when:
        if (retryAt == 'next recovery') {
            shutdownCallback(probe, replacement)
            assert listener.poll(10, TimeUnit.SECONDS).operation() == ChangeOperation.INVALIDATE
            assert registrationIds().size() == 1
            assert !registrationIds().contains(original)
            assert probe.unregisterAttempts[original].get() == 2
            assertInsert(repository, listener, 'after cleanup retry')
        }
        context.close()

        then:
        probe.unregisterAttempts[original].get() == 2
        registrationIds().empty

        where:
        retryAt << ['next recovery', 'context shutdown']
    }

    private ApplicationContext startContext(Map<String, Object> overrides = [:]) {
        ApplicationContext.run([
            'datasources.default.url': jdbcUrl,
            'datasources.default.username': 'test',
            'datasources.default.password': 'test',
            'datasources.default.driver-class-name': 'oracle.jdbc.OracleDriver',
            'datasources.default.dialect': 'ORACLE',
            // Keep the table after close so dropping it cannot mask a leaked registration.
            'datasources.default.schema-generate': 'NONE',
            'datasources.default.packages': ['io.micronaut.data.jdbc.oraclexe.notification.lifecycle'],
            'datasources.default.test-on-borrow': true,
            'datasources.default.validation-query': 'SELECT 1 FROM DUAL',
            'datasources.default.validation-interval': 0,
            'datasources.default.initial-size': 0,
            'datasources.default.min-idle': 0,
            'datasources.default.max-idle': 4,
            'datasources.default.max-active': 4,
            'oracle-lifecycle.enabled': 'true',
            'micronaut.data.jdbc.notifications.oracle.registration-recovery.retry-delay': '100ms',
            'micronaut.data.jdbc.notifications.oracle.registration-recovery.max-retry-delay': '1s'
        ] + overrides)
    }

    private Set<Long> registrationIds(String username = 'test') {
        DriverManager.getConnection(jdbcUrl, username, 'test').withCloseable { connection ->
            connection.createStatement().withCloseable { statement ->
                statement.executeQuery('SELECT DISTINCT REGID FROM USER_CHANGE_NOTIFICATION_REGS').withCloseable { rows ->
                    Set<Long> ids = []
                    while (rows.next()) {
                        ids.add(rows.getLong(1))
                    }
                    ids
                }
            }
        }
    }

    private void administratorStatement(String sql) {
        DriverManager.getConnection(jdbcUrl, 'system', 'test').withCloseable { connection ->
            connection.createStatement().withCloseable { statement ->
                statement.execute(sql)
            }
        }
    }

    private static void assertInsert(LifecycleBookRepository repository, def listener, String title) {
        def saved = repository.save(new LifecycleBook(id: BOOK_IDS.incrementAndGet(), title: title))
        ChangeEvent<LifecycleBook> event = listener.poll(10, TimeUnit.SECONDS)
        assert event.operation() == ChangeOperation.INSERT
        assert event.entity().orElseThrow().id == saved.id
        assert event.entity().orElseThrow().title == title
        assert event.metadata(OracleChangeEventMetadata).orElseThrow().rowId()
    }

    private void shutdownCallback(RegistrationProbe probe, long registrationId) {
        def event = Stub(DatabaseChangeEvent) {
            getEventType() >> DatabaseChangeEvent.EventType.SHUTDOWN
            getRegId() >> registrationId
        }
        probe.callbacks[registrationId].onDatabaseChangeNotification(event)
    }

    private void startDatabase() {
        sysdba('STARTUP;\nALTER PLUGGABLE DATABASE ALL OPEN;')
        databaseStopped = false
    }

    private void sysdba(String sql) {
        def result = database.execInContainer('bash', '-c', """sqlplus -s / as sysdba <<'SQL'
WHENEVER SQLERROR EXIT FAILURE
${sql}
EXIT
SQL
""")
        assert result.exitCode == 0: result.stdout + result.stderr
    }

    @Singleton
    @Requires(property = 'oracle-lifecycle.enabled', value = 'true')
    static class RegistrationProbe {
        final Map<Long, DatabaseChangeListener> callbacks = new ConcurrentHashMap<>()
        final Map<Long, Properties> options = new ConcurrentHashMap<>()
        final Map<Long, AtomicInteger> dataCallbacks = new ConcurrentHashMap<>()
        final List<DatabaseChangeEvent.EventType> events = new CopyOnWriteArrayList<>()
        final Map<Long, AtomicInteger> unregisterAttempts = new ConcurrentHashMap<>()
        final AtomicLong failRegistration = new AtomicLong(-1)
        final AtomicBoolean failOnce = new AtomicBoolean()
    }

    @Singleton
    @Requires(property = 'oracle-lifecycle.enabled', value = 'true')
    static class ProbeDataSources implements BeanCreatedEventListener<DataSource> {
        private final RegistrationProbe probe

        ProbeDataSources(RegistrationProbe probe) {
            this.probe = probe
        }

        @Override
        DataSource onCreated(BeanCreatedEvent<DataSource> event) {
            new ProbeDataSource(event.bean, probe)
        }
    }

    private static class ProbeDataSource implements DataSource, AutoCloseable {
        @Delegate
        private final DataSource target
        private final RegistrationProbe probe

        ProbeDataSource(DataSource target, RegistrationProbe probe) {
            this.target = target
            this.probe = probe
        }

        @Override
        Connection getConnection() {
            new ProbeConnection(target.connection, probe)
        }

        @Override
        Connection getConnection(String username, String password) {
            new ProbeConnection(target.getConnection(username, password), probe)
        }

        @Override
        void close() {
            target.close()
        }
    }

    private static class ProbeConnection implements Connection {
        @Delegate
        private final Connection target
        private final RegistrationProbe probe

        ProbeConnection(Connection target, RegistrationProbe probe) {
            this.target = target
            this.probe = probe
        }

        @Override
        <T> T unwrap(Class<T> type) {
            if (type == OracleConnection) {
                return new ProbeOracleConnection(target.unwrap(OracleConnection), probe) as T
            }
            target.unwrap(type)
        }
    }

    private static class ProbeOracleConnection implements OracleConnection {
        @Delegate
        private final OracleConnection target
        private final RegistrationProbe probe

        ProbeOracleConnection(OracleConnection target, RegistrationProbe probe) {
            this.target = target
            this.probe = probe
        }

        @Override
        DatabaseChangeRegistration registerDatabaseChangeNotification(Properties properties, DatabaseChangeListener listener) {
            DatabaseChangeListener tracked = { DatabaseChangeEvent event ->
                probe.events.add(event.eventType)
                if (event.eventType in [DatabaseChangeEvent.EventType.OBJCHANGE, DatabaseChangeEvent.EventType.QUERYCHANGE]) {
                    probe.dataCallbacks.computeIfAbsent(event.regId, { new AtomicInteger() }).incrementAndGet()
                }
                listener.onDatabaseChangeNotification(event)
            } as DatabaseChangeListener
            def registration = target.registerDatabaseChangeNotification(properties, tracked)
            probe.callbacks[registration.regId] = tracked
            probe.options[registration.regId] = registration.registrationOptions
            registration
        }

        @Override
        void unregisterDatabaseChangeNotification(DatabaseChangeRegistration registration) {
            probe.unregisterAttempts.computeIfAbsent(registration.regId, { new AtomicInteger() }).incrementAndGet()
            if (registration.regId == probe.failRegistration.get() && probe.failOnce.compareAndSet(true, false)) {
                throw new SQLException('Injected recovery unregister failure', '08006')
            }
            target.unregisterDatabaseChangeNotification(registration)
        }
    }
}
