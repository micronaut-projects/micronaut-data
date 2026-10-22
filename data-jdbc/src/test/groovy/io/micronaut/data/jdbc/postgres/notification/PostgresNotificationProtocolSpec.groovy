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
package io.micronaut.data.jdbc.postgres.notification

import io.micronaut.context.ApplicationContext
import io.micronaut.data.connection.jdbc.advice.DelegatingDataSource
import io.micronaut.data.jdbc.postgres.PostgresTestPropertyProvider
import io.micronaut.data.runtime.config.SchemaGenerate
import io.micronaut.json.JsonMapper
import org.postgresql.PGConnection
import spock.lang.Specification
import spock.lang.Unroll

import javax.sql.DataSource
import java.sql.Connection
import java.sql.SQLException

class PostgresNotificationProtocolSpec extends Specification implements PostgresTestPropertyProvider {
    private static final String FUNCTION = '/io/micronaut/data/jdbc/notification/postgres/sql/create-notification-function.sql'
    private static final String TENANT = 'b3d93d9a-1b6c-40ae-a761-f15d6010ad67'
    private static final String OTHER_TENANT = '54ef057d-a6d5-4b03-8865-5f347d892d19'
    private final String schema = 'notification_' + UUID.randomUUID().toString().replace('-', '')
    private final String channel = 'Book Changes ' + UUID.randomUUID().toString()
    private ApplicationContext context
    private Connection writer
    private Connection receiver
    private PGConnection notifications

    @Override
    SchemaGenerate schemaGenerate() {
        SchemaGenerate.NONE
    }

    void setup() {
        context = ApplicationContext.run(properties)
        def datasource = DelegatingDataSource.unwrapDataSource(context.getBean(DataSource))
        writer = datasource.connection
        receiver = datasource.connection
        writer.autoCommit = true
        receiver.autoCommit = true
        notifications = receiver.unwrap(PGConnection)
        execute("CREATE SCHEMA \"$schema\"")
        execute("SET search_path TO \"$schema\"")
        execute(getClass().getResource(FUNCTION).getText('UTF-8'))
        execute('CREATE TABLE "Book" ("Tenant" uuid, "Id" numeric(30), body text, PRIMARY KEY ("Tenant", "Id"))')
        execute("""CREATE TRIGGER book_changes AFTER INSERT OR UPDATE OR DELETE ON "Book"
            FOR EACH ROW EXECUTE FUNCTION micronaut_data_notify_change('$channel', 'Tenant', 'Id')""")
        execute("""CREATE TRIGGER book_truncate AFTER TRUNCATE ON "Book"
            FOR EACH STATEMENT EXECUTE FUNCTION micronaut_data_notify_change('$channel', 'Tenant', 'Id')""")
        try (def statement = receiver.createStatement()) {
            statement.execute("LISTEN \"$channel\"")
        }
    }

    void cleanup() {
        receiver?.close()
        if (writer != null) {
            if (!writer.autoCommit) {
                writer.rollback()
                writer.autoCommit = true
            }
            execute("DROP SCHEMA IF EXISTS \"$schema\" CASCADE")
            writer.close()
        }
        context?.close()
    }

    void "row events preserve composite keys precision and changes to primary keys"() {
        when:
        execute("INSERT INTO \"Book\" VALUES ('$TENANT', 9007199254740993, 'first')")

        then:
        event() == payload('INSERT', [:], [Tenant: TENANT, Id: '9007199254740993'])

        when:
        execute("UPDATE \"Book\" SET \"Tenant\" = '$OTHER_TENANT', \"Id\" = 9007199254740994, body = 'second'")

        then:
        event() == payload('UPDATE', [Tenant: TENANT, Id: '9007199254740993'], [Tenant: OTHER_TENANT, Id: '9007199254740994'])

        when:
        execute('DELETE FROM "Book"')

        then:
        event() == payload('DELETE', [Tenant: OTHER_TENANT, Id: '9007199254740994'], [:])
    }

    @Unroll
    void "non-key JSON that cannot be represented as jsonb does not prevent row changes: #description"() {
        given:
        execute('ALTER TABLE "Book" ADD COLUMN metadata json')
        try (def statement = writer.prepareStatement('INSERT INTO "Book" VALUES (?, ?, ?, ?::json)')) {
            statement.setObject(1, UUID.fromString(TENANT))
            statement.setBigDecimal(2, BigDecimal.ONE)
            statement.setString(3, 'before')
            statement.setString(4, json)
            statement.executeUpdate()
        }

        expect:
        event() == payload('INSERT', [:], [Tenant: TENANT, Id: '1'])

        when:
        execute('UPDATE "Book" SET body = \'after\'')

        then:
        event() == payload('UPDATE', [Tenant: TENANT, Id: '1'], [Tenant: TENANT, Id: '1'])

        when:
        execute('DELETE FROM "Book"')

        then:
        event() == payload('DELETE', [Tenant: TENANT, Id: '1'], [:])

        where:
        description             | json
        'escaped NUL'           | '{"value":"\\u0000"}'
        'out-of-range number'   | '{"value":1e1000000}'
    }

    void "key column identifiers containing quotes are extracted safely"() {
        given:
        execute('CREATE TABLE quoted_key ("Book""Id" bigint PRIMARY KEY)')
        execute("""CREATE TRIGGER changes AFTER INSERT OR UPDATE OR DELETE ON quoted_key FOR EACH ROW
            EXECUTE FUNCTION micronaut_data_notify_change('$channel', 'Book"Id')""")

        when:
        execute('INSERT INTO quoted_key VALUES (1)')

        then:
        event() == payload('INSERT', [:], ['Book"Id': '1']) + [table: 'quoted_key']

        when:
        execute('UPDATE quoted_key SET "Book""Id" = 2')

        then:
        event() == payload('UPDATE', ['Book"Id': '1'], ['Book"Id': '2']) + [table: 'quoted_key']

        when:
        execute('DELETE FROM quoted_key')

        then:
        event() == payload('DELETE', ['Book"Id': '2'], [:]) + [table: 'quoted_key']
    }

    void "notifications are delivered after commit and discarded on rollback"() {
        given:
        writer.autoCommit = false

        when:
        execute("INSERT INTO \"Book\" VALUES ('$TENANT', 1, 'committed')")

        then:
        !notifications.getNotifications(200)

        when:
        writer.commit()

        then:
        event().newKeys == [Tenant: TENANT, Id: '1']

        when:
        execute('DELETE FROM "Book"')
        writer.rollback()

        then:
        !notifications.getNotifications(200)
    }

    void "truncate reports one table invalidation"() {
        when:
        execute('TRUNCATE "Book"')

        then:
        event() == payload('INVALIDATE', [:], [:])
    }

    void "UTF-8 payload overflow falls back to invalidation"() {
        given:
        execute('CREATE TABLE "LargeKey" (id text PRIMARY KEY, body text)')
        execute("""CREATE TRIGGER large_changes AFTER INSERT OR UPDATE ON "LargeKey"
            FOR EACH ROW EXECUTE FUNCTION micronaut_data_notify_change('$channel', 'id')""")
        def key = 'é' * 2500
        execute("INSERT INTO \"LargeKey\" VALUES ('$key', 'before')")
        assert event().newKeys == [id: key]

        when:
        execute('UPDATE "LargeKey" SET body = \'after\'')

        then:
        event() == payload('INVALIDATE', [:], [:]) + [table: 'LargeKey']
    }

    void "identical row payloads in one transaction may be coalesced"() {
        given:
        execute("INSERT INTO \"Book\" VALUES ('$TENANT', 1, 'initial')")
        event()
        writer.autoCommit = false

        when:
        execute('UPDATE "Book" SET body = \'first\'')
        execute('UPDATE "Book" SET body = \'second\'')
        writer.commit()

        then:
        event() == payload('UPDATE', [Tenant: TENANT, Id: '1'], [Tenant: TENANT, Id: '1'])
        !notifications.getNotifications(200)
    }

    @Unroll
    void "invalid trigger configuration fails explicitly: #description"() {
        given:
        execute('DROP TRIGGER book_changes ON "Book"')
        execute("""CREATE TRIGGER book_changes $timing INSERT ON "Book"
            FOR EACH ROW EXECUTE FUNCTION micronaut_data_notify_change($arguments)""")

        when:
        execute("INSERT INTO \"Book\" VALUES ('$TENANT', 1, 'value')")

        then:
        def e = thrown(SQLException)
        e.message.contains(message)
        !notifications.getNotifications(200)

        where:
        description      | timing   | arguments                  | message
        'missing keys'   | 'AFTER'  | "'books'"                  | 'requires a channel and primary-key'
        'missing column' | 'AFTER'  | "'books', 'missing'"       | 'must exist and contain a non-null JSON scalar'
        'duplicate keys' | 'AFTER'  | "'books', 'Id', 'Id'"      | 'must be non-empty and distinct'
        'long channel'   | 'AFTER'  | "'${'é' * 32}', 'Id'"     | 'at most 63 UTF-8 bytes'
        'before trigger' | 'BEFORE' | "'books', 'Id'"           | 'Use an AFTER ROW'
    }

    void "statement resource emits one invalidation for bulk writes and respects transaction boundaries"() {
        given:
        installInvalidationFunction()
        execute("""CREATE TRIGGER book_changes AFTER INSERT OR UPDATE OR DELETE OR TRUNCATE ON "Book"
            FOR EACH STATEMENT EXECUTE FUNCTION micronaut_data_notify_invalidation('$channel')""")
        writer.autoCommit = false

        when:
        execute("""INSERT INTO "Book" SELECT '$TENANT'::uuid, id, 'bulk' FROM generate_series(1, 1000) AS id""")
        execute('UPDATE "Book" SET body = \'changed\'')

        then:
        !notifications.getNotifications(200)

        when:
        writer.commit()

        then: 'identical invalidations within the transaction are coalesced'
        event() == payload('INVALIDATE', [:], [:])
        !notifications.getNotifications(200)

        when:
        execute('DELETE FROM "Book"')
        writer.rollback()

        then:
        !notifications.getNotifications(200)
    }

    @Unroll
    void "statement resource rejects invalid configuration: #description"() {
        given:
        installInvalidationFunction()
        execute("""CREATE TRIGGER book_changes $timing INSERT ON "Book"
            FOR EACH $level EXECUTE FUNCTION micronaut_data_notify_invalidation($arguments)""")

        when:
        execute("INSERT INTO \"Book\" VALUES ('$TENANT', 1, 'value')")

        then:
        def failure = thrown(SQLException)
        failure.message.contains(message)
        !notifications.getNotifications(200)

        where:
        description      | timing   | level       | arguments             | message
        'missing channel'| 'AFTER'  | 'STATEMENT' | ''                    | 'exactly one channel'
        'extra argument' | 'AFTER'  | 'STATEMENT' | "'books', 'id'"       | 'exactly one channel'
        'blank channel'  | 'AFTER'  | 'STATEMENT' | "' '"                 | 'non-blank'
        'long channel'   | 'AFTER'  | 'STATEMENT' | "'${'é' * 32}'"      | 'at most 63 UTF-8 bytes'
        'before trigger' | 'BEFORE' | 'STATEMENT' | "'books'"             | 'Use an AFTER STATEMENT'
        'row trigger'    | 'AFTER'  | 'ROW'       | "'books'"             | 'Use an AFTER STATEMENT'
    }

    private void installInvalidationFunction() {
        execute('DROP TRIGGER book_changes ON "Book"')
        execute('DROP TRIGGER book_truncate ON "Book"')
        execute(getClass().getResource('/io/micronaut/data/jdbc/notification/postgres/sql/create-invalidation-function.sql').getText('UTF-8'))
    }

    private void execute(String sql) {
        try (def statement = writer.createStatement()) {
            statement.execute(sql)
        }
    }

    private Map event() {
        def received = notifications.getNotifications(5000)
        assert received != null && received.length == 1
        assert received[0].name == channel
        context.getBean(JsonMapper).readValue(received[0].parameter, Map)
    }

    private Map payload(String operation, Map oldKeys, Map newKeys) {
        [version: 1, schema: schema, table: 'Book', operation: operation, oldKeys: oldKeys, newKeys: newKeys]
    }
}
