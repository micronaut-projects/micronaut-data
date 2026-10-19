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

import io.micronaut.context.BeanContext
import io.micronaut.context.env.Environment
import io.micronaut.context.exceptions.ConfigurationException
import org.postgresql.PGProperty
import org.postgresql.ds.PGSimpleDataSource
import spock.lang.Specification

class PostgresNotificationConnectionFactorySpec extends Specification {

    void "dedicated connections have finite timeout defaults"() {
        when:
        def source = createSource()

        then:
        source.connectTimeout == 10
        source.loginTimeout == 30
        source.socketTimeout == 30
    }

    void "unlimited timeout settings use notification defaults even in the URL"() {
        when:
        def source = createSource('connectTimeout=0&loginTimeout=0&socketTimeout=0',
            [connectTimeout: 5, loginTimeout: 6, socketTimeout: 7])

        then:
        source.connectTimeout == 10
        source.loginTimeout == 30
        source.socketTimeout == 30
    }

    void "preserves positive datasource timeouts"() {
        when:
        def source = createSource('', [connectTimeout: 5, loginTimeout: 6, socketTimeout: 7])

        then:
        source.connectTimeout == 5
        source.loginTimeout == 6
        source.socketTimeout == 7
    }

    void "URL timeouts override datasource properties"() {
        when:
        def source = createSource('connectTimeout=8&loginTimeout=9&socketTimeout=11',
            [connectTimeout: 5, loginTimeout: 6, socketTimeout: 7])

        then:
        source.connectTimeout == 8
        source.loginTimeout == 9
        source.socketTimeout == 11
    }

    void "preserves fractional login timeout supported by the driver"() {
        expect:
        createSource('loginTimeout=0.5').getProperty(PGProperty.LOGIN_TIMEOUT) == '0.5'
    }

    void "rejects invalid timeout before opening a connection: #property=#value"() {
        when:
        createSource("$property=$value")

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

    private PGSimpleDataSource createSource(String query = '', Map<String, Object> properties = [:]) {
        def context = Stub(BeanContext) {
            findBean(_, _) >> Optional.empty()
        }
        def environment = Stub(Environment) {
            getProperty('datasources.test.url', String) >> Optional.of('jdbc:postgresql://localhost/test?' + query)
            getProperty(_, _) >> Optional.empty()
            getProperties('datasources.test.data-source-properties', _) >> properties
            getProperties(_, _) >> [:]
        }
        def factory = new PostgresNotificationConnectionFactory(context, environment)
        factory.createDataSource('test')
    }
}
