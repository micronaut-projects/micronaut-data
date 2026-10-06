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
import io.micronaut.data.exceptions.DataIntegrityViolationException
import io.micronaut.data.exceptions.EntityExistsException
import io.micronaut.data.jdbc.annotation.JdbcRepository
import io.micronaut.data.jdbc.postgres.PostgresTestPropertyProvider
import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.repository.CrudRepository
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

import java.sql.SQLException

/**
 * Constraint violations of entity operations executed with a RETURNING clause
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

    void cleanup() {
        repository.deleteAll()
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
}
