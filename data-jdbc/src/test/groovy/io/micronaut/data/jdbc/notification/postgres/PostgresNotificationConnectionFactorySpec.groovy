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
import io.micronaut.context.env.Environment
import io.micronaut.context.exceptions.ConfigurationException
import io.micronaut.jdbc.BasicJdbcConfiguration
import org.postgresql.Driver
import org.postgresql.PGProperty
import spock.lang.Specification

import javax.net.SocketFactory
import java.sql.SQLException
import java.util.concurrent.atomic.AtomicReference

class PostgresNotificationConnectionFactorySpec extends Specification {

    void "dedicated connections have finite timeout defaults"() {
        when:
        def source = driverProperties()

        then:
        source.tcpKeepAlive == 'true'
        source.connectTimeout == '10'
        source.loginTimeout == '30'
        source.socketTimeout == '30'
    }

    void "unlimited timeout settings use notification defaults even in the URL"() {
        when:
        def source = driverProperties('connectTimeout=0&loginTimeout=0&socketTimeout=0',
            [connectTimeout: 5, loginTimeout: 6, socketTimeout: 7])

        then:
        source.tcpKeepAlive == 'true'
        source.connectTimeout == '10'
        source.loginTimeout == '30'
        source.socketTimeout == '30'
    }

    void "preserves positive datasource timeouts"() {
        when:
        def source = driverProperties('', [connectTimeout: 5, loginTimeout: 6, socketTimeout: 7])

        then:
        source.connectTimeout == '5'
        source.loginTimeout == '6'
        source.socketTimeout == '7'
    }

    void "URL timeouts override datasource properties"() {
        when:
        def source = driverProperties('connectTimeout=8&loginTimeout=9&socketTimeout=11',
            [connectTimeout: 5, loginTimeout: 6, socketTimeout: 7])

        then:
        source.connectTimeout == '8'
        source.loginTimeout == '9'
        source.socketTimeout == '11'
    }

    void "preserves explicit keepalive settings and URL precedence"() {
        expect:
        driverProperties('', [tcpKeepAlive: false]).tcpKeepAlive == 'false'
        driverProperties('tcpKeepAlive=false', [tcpKeepAlive: true]).tcpKeepAlive == 'false'
        driverProperties('tcpKeepAlive=true', [tcpKeepAlive: false]).tcpKeepAlive == 'true'
    }

    void "preserves fractional login timeout supported by the driver"() {
        expect:
        PGProperty.LOGIN_TIMEOUT.get(driverProperties('loginTimeout=0.5')) == '0.5'
    }

    void "passes custom socket-factory properties from the URL and configuration to the driver"() {
        when:
        def properties = driverProperties('cloudSqlInstance=p%3Ar%3Ai&enableIamAuth=true',
            [cloudSqlInstance: 'overridden', customSocketArgument: 'endpoint=value'])

        then:
        properties.cloudSqlInstance == 'p:r:i'
        properties.enableIamAuth == 'true'
        properties.customSocketArgument == 'endpoint=value'
        properties.socketFactory == CapturingNotificationSocketFactory.name
    }

    void "preserves URL parameter overrides for host port and database"() {
        when:
        def properties = driverProperties('PGHOST=custom-host&PGPORT=6543&PGDBNAME=custom-db')

        then:
        properties.getProperty('PGHOST') == 'custom-host'
        properties.getProperty('PGPORT') == '6543'
        properties.getProperty('PGDBNAME') == 'custom-db'
    }

    void "custom factory replaces the default and reads current programmatic properties"() {
        given:
        def properties = new Properties()
        properties.setProperty('sslmode', 'verify-full')
        properties.setProperty('customSocketArgument', 'configured')
        properties.setProperty('socketFactory', CapturingNotificationSocketFactory.name)
        PostgresNotificationConnectionFactory custom = { String name ->
            assert name == 'test'
            new Driver().connect('jdbc:postgresql://localhost/test', properties)
        }
        def context = ApplicationContext.builder()
            .enableDefaultPropertySources(false)
            .singletons(custom)
            .start()
        CapturingNotificationSocketFactory.properties.set(null)

        when:
        context.getBean(PostgresNotificationConnectionFactory).openConnection('test')

        then:
        thrown(SQLException)
        CapturingNotificationSocketFactory.properties.get().sslmode == 'verify-full'
        CapturingNotificationSocketFactory.properties.get().customSocketArgument == 'configured'
        context.getBeansOfType(PostgresNotificationConnectionFactory).size() == 1
        !context.containsBean(DefaultPostgresNotificationConnectionFactory)
        !properties.containsKey('socketTimeout')

        when:
        properties.setProperty('customSocketArgument', 'refreshed')
        CapturingNotificationSocketFactory.properties.set(null)
        context.getBean(PostgresNotificationConnectionFactory).openConnection('test')

        then:
        thrown(SQLException)
        CapturingNotificationSocketFactory.properties.get().sslmode == 'verify-full'
        CapturingNotificationSocketFactory.properties.get().customSocketArgument == 'refreshed'

        cleanup:
        context?.close()
    }

    void "rejects invalid timeout before opening a connection: #property=#value"() {
        when:
        driverProperties("$property=$value")

        then:
        def failure = thrown(ConfigurationException)
        failure.message.contains(property)
        failure.message.contains('test')

        where:
        property         | value
        'connectTimeout' | '-1'
        'connectTimeout' | 'invalid'
        'connectTimeout' | '2147484'
        'socketTimeout'  | '-1'
        'socketTimeout'  | 'NaN'
        'socketTimeout'  | '2147484'
        'loginTimeout'   | '-1'
        'loginTimeout'   | 'NaN'
        'loginTimeout'   | 'Infinity'
        'loginTimeout'   | '0.0001'
        'loginTimeout'   | '1e30'
    }

    void "rejects unsupported JDBC scheme #scheme without exposing URL credentials"() {
        given:
        def context = Stub(BeanContext) {
            findBean(_, _) >> Optional.empty()
        }
        def environment = Stub(Environment) {
            getProperty('datasources.test.url', String) >> Optional.of(url)
            getProperty(_, _) >> Optional.empty()
        }
        def factory = new DefaultPostgresNotificationConnectionFactory(context, environment)

        when:
        factory.openConnection('test')

        then:
        def failure = thrown(ConfigurationException)
        failure.message.contains("Unsupported JDBC URL scheme [$scheme]")
        failure.message.contains('datasource [test]')
        failure.message.contains('expected [jdbc:postgresql:]')
        !failure.message.contains('secret')
        !failure.message.contains('private-host')

        where:
        scheme              | url
        'jdbc:otel:'        | 'jdbc:otel:postgresql://private-host/db?password=secret'
        'jdbc:aws-wrapper:' | 'jdbc:aws-wrapper:postgresql://private-host/db?password=secret'
        'jdbc:p6spy:'       | 'jdbc:p6spy:postgresql://private-host/db?password=secret'
        'jdbc:tc:'          | 'jdbc:tc:postgresql:18.6:///db?password=secret'
        'jdbc:mysql:'       | 'jdbc:mysql://private-host/db?password=secret'
        'unrecognized'     | 'secret@private-host'
    }

    void "reports a missing notification JDBC URL"() {
        given:
        def context = Stub(BeanContext) {
            findBean(_, _) >> Optional.empty()
        }
        def environment = Stub(Environment) {
            getProperty(_, _) >> Optional.empty()
        }

        when:
        new DefaultPostgresNotificationConnectionFactory(context, environment).openConnection('test')

        then:
        def failure = thrown(ConfigurationException)
        failure.message.contains('Missing JDBC URL')
        failure.message.contains('datasource [test]')
        failure.message.contains('jdbc:postgresql:')
    }

    private Properties driverProperties(String query = '', Map<String, Object> properties = [:],
                                        BasicJdbcConfiguration configuration = null) {
        CapturingNotificationSocketFactory.properties.set(null)
        def context = Stub(BeanContext) {
            findBean(BasicJdbcConfiguration, _) >> Optional.ofNullable(configuration)
            findBean(_, _) >> Optional.empty()
        }
        def environment = Stub(Environment) {
            getProperty('datasources.test.url', String) >> Optional.of('jdbc:postgresql://localhost/test?' + query)
            getProperty(_, _) >> Optional.empty()
            getProperties('datasources.test.data-source-properties', _) >>
                ([socketFactory: CapturingNotificationSocketFactory.name] + properties)
            getProperties(_, _) >> [:]
        }
        def factory = new DefaultPostgresNotificationConnectionFactory(context, environment)
        try {
            factory.openConnection('test')
            assert false: 'The capturing socket factory must stop connection setup'
        } catch (SQLException ignored) {
            // The socket factory captures the final driver properties before any network access.
        }
        def captured = CapturingNotificationSocketFactory.properties.get()
        assert captured != null
        captured
    }
}

class CapturingNotificationSocketFactory extends SocketFactory {
    static final AtomicReference<Properties> properties = new AtomicReference<>()

    @Delegate
    private final SocketFactory delegate = SocketFactory.default

    CapturingNotificationSocketFactory(Properties values) {
        properties.set(values)
        throw new IllegalStateException('Connection properties captured')
    }
}
