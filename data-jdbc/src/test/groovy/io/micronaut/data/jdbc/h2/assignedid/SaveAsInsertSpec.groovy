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
package io.micronaut.data.jdbc.h2.assignedid

import io.micronaut.context.ApplicationContext
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.data.exceptions.DataAccessException
import io.micronaut.data.jdbc.annotation.JdbcRepository
import io.micronaut.data.jdbc.h2.H2TestPropertyProvider
import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.repository.CrudRepository
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

class SaveAsInsertSpec extends Specification implements H2TestPropertyProvider {

    @Shared @AutoCleanup ApplicationContext ctx = ApplicationContext.run(getProperties())

    @Shared SaveAsInsertBookRepository repository = ctx.getBean(SaveAsInsertBookRepository)

    @Override
    List<String> packages() {
        return Arrays.asList(getClass().package.name)
    }

    @Override
    Map<String, String> getProperties() {
        return H2TestPropertyProvider.super.getProperties() + [
                'datasources.default.url'      : 'jdbc:h2:mem:saveAsInsert;LOCK_TIMEOUT=10000;DB_CLOSE_ON_EXIT=FALSE',
                'micronaut.data.save-as-insert': 'true'
        ]
    }

    void "save always inserts entities with preset IDs when save-as-insert is enabled"() {
        given:
        UUID id = UUID.randomUUID()

        when:
        SaveAsInsertBook saved = repository.save(new SaveAsInsertBook(id: id, title: 'First'))

        then:
        saved.id == id
        repository.findById(id).get().title == 'First'

        when:
        repository.save(new SaveAsInsertBook(id: id, title: 'Second'))

        then:
        thrown(DataAccessException)
        repository.findById(id).get().title == 'First'
    }

    void "saveAll always inserts entities with preset IDs when save-as-insert is enabled"() {
        given:
        UUID firstId = UUID.randomUUID()
        UUID secondId = UUID.randomUUID()

        when:
        List<SaveAsInsertBook> saved = repository.saveAll([
                new SaveAsInsertBook(id: firstId, title: 'First batch'),
                new SaveAsInsertBook(id: secondId, title: 'Second batch')
        ])

        then:
        saved*.id as Set == [firstId, secondId] as Set
        repository.findById(firstId).get().title == 'First batch'
        repository.findById(secondId).get().title == 'Second batch'

        when:
        repository.saveAll([
                new SaveAsInsertBook(id: firstId, title: 'First batch updated'),
                new SaveAsInsertBook(id: UUID.randomUUID(), title: 'Third batch')
        ])

        then:
        thrown(DataAccessException)
        repository.findById(firstId).get().title == 'First batch'
    }
}

@MappedEntity("save_as_insert_book")
class SaveAsInsertBook {
    @Id
    UUID id
    String title
}

@JdbcRepository(dialect = Dialect.H2)
interface SaveAsInsertBookRepository extends CrudRepository<SaveAsInsertBook, UUID> {
}
