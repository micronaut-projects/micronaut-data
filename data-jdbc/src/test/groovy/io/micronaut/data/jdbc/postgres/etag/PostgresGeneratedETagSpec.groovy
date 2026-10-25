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
package io.micronaut.data.jdbc.postgres.etag

import io.micronaut.context.ApplicationContext
import io.micronaut.data.annotation.GeneratedValue
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.data.annotation.MappedProperty
import io.micronaut.data.annotation.sql.ETaggable
import io.micronaut.data.annotation.sql.GeneratedETag
import io.micronaut.data.connection.jdbc.advice.DelegatingDataSource
import io.micronaut.data.exceptions.OptimisticLockException
import io.micronaut.data.jdbc.annotation.JdbcRepository
import io.micronaut.data.jdbc.postgres.PostgresTestPropertyProvider
import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.repository.CrudRepository
import io.micronaut.inject.qualifiers.Qualifiers
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

import javax.sql.DataSource

class PostgresGeneratedETagSpec extends Specification implements PostgresTestPropertyProvider {

    @Shared
    @AutoCleanup
    ApplicationContext applicationContext = ApplicationContext.run(properties)

    @Shared
    PostgresETagBookRepository repository = applicationContext.getBean(PostgresETagBookRepository)

    void setupSpec() {
        def dataSource = DelegatingDataSource.unwrapDataSource(applicationContext.getBean(DataSource, Qualifiers.byName("default")))
        dataSource.connection.withCloseable { connection ->
            connection.createStatement().withCloseable { statement ->
                statement.execute('''
                    CREATE OR REPLACE FUNCTION book_etag(BIGINT, VARCHAR) RETURNS VARCHAR
                    LANGUAGE SQL IMMUTABLE AS $$ SELECT md5($1::text || ':' || $2) $$
                ''')
            }
        }
    }

    void "generated ETag reads and checks upper case mapped columns"() {
        when:
        def saved = repository.save(new PostgresETagBook(title: "Initial"))
        def loaded = repository.findById(saved.id).get()

        then:
        loaded.etag

        when:
        def etag = loaded.etag
        loaded.title = "Updated"
        repository.update(loaded)
        def updated = repository.findById(saved.id).get()

        then:
        updated.title == "Updated"
        updated.etag != etag

        when:
        repository.update(new PostgresETagBook(id: saved.id, title: "Stale", etag: etag))

        then:
        thrown(OptimisticLockException)

        when:
        repository.delete(new PostgresETagBook(id: saved.id, title: "Updated", etag: etag))

        then:
        thrown(OptimisticLockException)

        when:
        repository.delete(updated)

        then:
        !repository.findById(saved.id).present
    }

    @Override
    List<String> packages() {
        ["io.micronaut.data.jdbc.postgres.etag"]
    }
}

@MappedEntity("ETAG_BOOK")
@ETaggable
class PostgresETagBook {
    @Id
    @GeneratedValue
    @MappedProperty("BOOK_ID")
    Long id

    @MappedProperty("BOOK_TITLE")
    String title

    @GeneratedETag(function = "book_etag")
    String etag
}

@JdbcRepository(dialect = Dialect.POSTGRES)
interface PostgresETagBookRepository extends CrudRepository<PostgresETagBook, Long> {
}
