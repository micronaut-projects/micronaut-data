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
package io.micronaut.data.jdbc.mariadb

import io.micronaut.context.ApplicationContext
import io.micronaut.data.annotation.GeneratedValue
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.Insert
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.data.jdbc.annotation.JdbcRepository
import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.repository.CrudRepository
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

class MariaBatchInsertSpec extends Specification implements MariaTestPropertyProvider {

    @AutoCleanup
    @Shared
    ApplicationContext context = ApplicationContext.run(properties)

    @Shared
    MariaBatchBookRepository repository = context.getBean(MariaBatchBookRepository)

    void setup() {
        repository.deleteAll()
    }

    void "custom void insertAll batches generated-id inserts without mutating input ids"() {
        given:
        def books = [
            new MariaBatchBook(title: "The Left Hand"),
            new MariaBatchBook(title: "The Dispossessed")
        ]

        when:
        repository.customInsertAll(books)

        then:
        repository.count() == 2
        books*.id == [null, null]
        repository.findAll()*.id.every { it != null }
        repository.findAll()*.title as Set == ["The Left Hand", "The Dispossessed"] as Set
    }

    void "saveAll stays on the generated-key path for generated identities"() {
        given:
        def books = [
            new MariaBatchBook(title: "A Wizard of Earthsea"),
            new MariaBatchBook(title: "The Tombs of Atuan")
        ]

        when:
        def saved = repository.saveAll(books)

        then:
        saved*.id.every { it != null }
        repository.count() == 2
    }
}

@MappedEntity("maria_batch_book")
class MariaBatchBook {

    @Id
    @GeneratedValue
    Long id

    String title
}

@JdbcRepository(dialect = Dialect.MYSQL)
interface MariaBatchBookRepository extends CrudRepository<MariaBatchBook, Long> {

    @Insert
    void customInsertAll(List<MariaBatchBook> entities)
}
