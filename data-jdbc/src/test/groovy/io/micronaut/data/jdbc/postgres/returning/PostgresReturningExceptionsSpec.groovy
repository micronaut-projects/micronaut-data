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
package io.micronaut.data.jdbc.postgres.returning

import io.micronaut.context.ApplicationContext
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.Index
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.data.annotation.Query
import io.micronaut.data.connection.jdbc.advice.DelegatingDataSource
import io.micronaut.data.exceptions.DataAccessException
import io.micronaut.data.exceptions.DataIntegrityViolationException
import io.micronaut.data.exceptions.EntityExistsException
import io.micronaut.data.jdbc.annotation.JdbcRepository
import io.micronaut.data.jdbc.postgres.PostgresTestPropertyProvider
import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.repository.CrudRepository
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

import javax.sql.DataSource
import java.sql.SQLException

/**
 * Constraint violations of entity and query operations executed with a RETURNING clause
 * must be classified the same way as those of plain entity operations.
 */
class PostgresReturningExceptionsSpec extends Specification implements PostgresTestPropertyProvider {

    @Override
    List<String> packages() {
        return [getClass().package.name]
    }

    @Shared
    @AutoCleanup
    ApplicationContext ctx = ApplicationContext.run(getProperties())

    @Shared
    ReturningItemRepository repository = ctx.getBean(ReturningItemRepository)

    @Shared
    DataSource dataSource = DelegatingDataSource.unwrapDataSource(ctx.getBean(DataSource))

    void setupSpec() {
        executeSql('CREATE TABLE IF NOT EXISTS returning_item_ref (item_id BIGINT NOT NULL REFERENCES returning_item (id))')
    }

    void cleanupSpec() {
        executeSql('DROP TABLE IF EXISTS returning_item_ref')
    }

    void cleanup() {
        executeSql('DELETE FROM returning_item_ref')
        repository.deleteAll()
    }

    private void executeSql(String sql) {
        dataSource.connection.withCloseable { connection ->
            connection.createStatement().withCloseable { statement ->
                statement.execute(sql)
            }
        }
    }

    void "plain insert of an existing id throws EntityExistsException"() {
        given:
            repository.insert(new ReturningItem(id: 1L, code: "A", name: "A"))

        when:
            repository.insert(new ReturningItem(id: 1L, code: "B", name: "B"))

        then:
            def e = thrown(EntityExistsException)
            e.cause instanceof SQLException
    }

    void "insert returning of an existing id throws EntityExistsException"() {
        given:
            repository.insertReturning(new ReturningItem(id: 1L, code: "A", name: "A"))

        when:
            repository.insertReturning(new ReturningItem(id: 1L, code: "B", name: "B"))

        then:
            def e = thrown(EntityExistsException)
            e.cause instanceof SQLException
    }

    void "update returning violating a unique index throws EntityExistsException"() {
        given:
            repository.insertReturning(new ReturningItem(id: 1L, code: "A", name: "A"))
            def other = repository.insertReturning(new ReturningItem(id: 2L, code: "B", name: "B"))

        when:
            other.code = "A"
            repository.updateReturning(other)

        then:
            def e = thrown(EntityExistsException)
            e.cause instanceof SQLException
    }

    void "insert returning violating a NOT NULL column throws DataIntegrityViolationException"() {
        when:
            repository.insertReturning(new ReturningItem(id: 3L, code: "C", name: null))

        then:
            def e = thrown(DataIntegrityViolationException)
            !(e instanceof EntityExistsException)
            e.cause instanceof SQLException
    }

    void "custom insert returning query of an existing id throws EntityExistsException"() {
        given:
            repository.customInsertReturning(1L, "A", "A")

        when:
            repository.customInsertReturning(1L, "B", "B")

        then:
            def e = thrown(EntityExistsException)
            e.cause instanceof SQLException
    }

    void "update returning a property violating a unique index throws EntityExistsException"() {
        given:
            repository.insert(new ReturningItem(id: 1L, code: "A", name: "A"))
            repository.insert(new ReturningItem(id: 2L, code: "B", name: "B"))

        when:
            repository.updateReturningCode(2L, "A")

        then:
            def e = thrown(EntityExistsException)
            e.cause instanceof SQLException
    }

    void "delete returning a referenced row throws DataIntegrityViolationException"() {
        given:
            repository.insert(new ReturningItem(id: 1L, code: "A", name: "A"))
            executeSql('INSERT INTO returning_item_ref (item_id) VALUES (1)')

        when:
            repository.deleteReturning(1L)

        then:
            def e = thrown(DataIntegrityViolationException)
            !(e instanceof EntityExistsException)
            e.cause instanceof SQLException
    }

    void "a constraint violation of a select query is not classified"() {
        given:
            repository.insert(new ReturningItem(id: 1L, code: "A", name: "A"))

        when: "a SELECT query fails with a duplicate key"
            repository.findAllInsertedWithCte(1L, "B", "B")

        then: "it is wrapped as before, only the RETURNING queries are classified"
            def e = thrown(DataAccessException)
            e.class == DataAccessException
            e.message.startsWith("Error executing SQL Query: ")
            e.cause instanceof SQLException
    }
}

@MappedEntity("returning_item")
@Index(columns = "code", unique = true)
class ReturningItem {
    @Id
    Long id
    String code
    String name
}

@JdbcRepository(dialect = Dialect.POSTGRES)
interface ReturningItemRepository extends CrudRepository<ReturningItem, Long> {

    ReturningItem insertReturning(ReturningItem entity)

    ReturningItem updateReturning(ReturningItem entity)

    @Query("INSERT INTO returning_item (id, code, name) VALUES (:id, :code, :name) RETURNING *")
    ReturningItem customInsertReturning(Long id, String code, String name)

    String updateReturningCode(@Id Long id, String code)

    List<ReturningItem> deleteReturning(Long id)

    @Query("WITH inserted AS (INSERT INTO returning_item (id, code, name) VALUES (:id, :code, :name) RETURNING *) SELECT * FROM inserted")
    List<ReturningItem> findAllInsertedWithCte(Long id, String code, String name)
}
