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
package io.micronaut.data.jdbc.oraclexe

import io.micronaut.context.ApplicationContext
import io.micronaut.data.annotation.GeneratedValue
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.data.connection.ConnectionOperations
import io.micronaut.data.jdbc.annotation.JdbcRepository
import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.repository.OracleCrudRepository
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

import javax.sql.DataSource

class OracleRowIdSpec extends Specification implements OracleTestPropertyProvider {

    @AutoCleanup
    @Shared
    ApplicationContext context = ApplicationContext.run(properties)

    @Shared
    OracleRowIdRepository repository = context.getBean(OracleRowIdRepository)

    @Shared
    ConnectionOperations<DataSource> connectionOperations = context.getBean(ConnectionOperations)

    void "finds an entity by its Oracle ROWID pseudocolumn"() {
        given:
        def saved = repository.save(new OracleRowIdEntity(name: "Oracle ROWID"))

        when:
        String rowId = connectionOperations.executeRead { status ->
            status.connection.prepareStatement('SELECT ROWID FROM oracle_row_id_entity WHERE id = ?').withCloseable { statement ->
                statement.setLong(1, saved.id)
                statement.executeQuery().withCloseable { resultSet ->
                    assert resultSet.next()
                    resultSet.getString(1)
                }
            }
        }
        def found = repository.findByRowId(rowId)

        then:
        found.present
        found.get().id == saved.id
        found.get().name == saved.name

        cleanup:
        repository.deleteById(saved.id)
    }
}

@MappedEntity
class OracleRowIdEntity {
    @Id
    @GeneratedValue
    Long id

    String name
}

@JdbcRepository(dialect = Dialect.ORACLE)
interface OracleRowIdRepository extends OracleCrudRepository<OracleRowIdEntity, Long> {
}
