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
package io.micronaut.data.connection.jdbc.oracle

import io.micronaut.data.connection.support.AbstractConnectionOperations
import spock.lang.Specification

import javax.sql.DataSource
import java.sql.Connection
import java.sql.DatabaseMetaData
import java.sql.SQLException

class OracleClientInfoConnectionCustomizerSpec extends Specification {

    void "the connection borrowed to detect the database is closed (#productName)"() {
        given:
            def metaData = Mock(DatabaseMetaData) {
                getDatabaseProductName() >> productName
            }
            def connection = Mock(Connection) {
                getMetaData() >> metaData
            }
            def dataSource = Mock(DataSource) {
                getConnection() >> connection
            }
            def connectionOperations = Mock(AbstractConnectionOperations)

        when:
            new OracleClientInfoConnectionCustomizer(dataSource, connectionOperations, null)

        then:
            1 * connection.close()
            registrations * connectionOperations.addConnectionCustomizer(_)

        where:
            productName | registrations
            "Oracle"    | 1
            "H2"        | 0
    }

    void "the connection borrowed to detect the database is closed when reading the metadata fails"() {
        given:
            def connection = Mock(Connection) {
                getMetaData() >> { throw new SQLException("no metadata") }
            }
            def dataSource = Mock(DataSource) {
                getConnection() >> connection
            }
            def connectionOperations = Mock(AbstractConnectionOperations)

        when:
            new OracleClientInfoConnectionCustomizer(dataSource, connectionOperations, null)

        then:
            1 * connection.close()
            0 * connectionOperations.addConnectionCustomizer(_)
    }
}
