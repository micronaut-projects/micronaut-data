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
import io.micronaut.data.annotation.Version
import io.micronaut.data.exceptions.DataAccessException
import io.micronaut.data.jdbc.annotation.JdbcRepository
import io.micronaut.data.jdbc.h2.H2TestPropertyProvider
import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.repository.CrudRepository
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

class SaveAssignedIdFallbackToUpdateSpec extends Specification implements H2TestPropertyProvider {

    @Shared @AutoCleanup ApplicationContext ctx = ApplicationContext.run(getProperties())

    @Shared SaveAssignedIdFallbackBookRepository repository = ctx.getBean(SaveAssignedIdFallbackBookRepository)
    @Shared SaveAssignedIdFallbackVersionedBookRepository versionedRepository = ctx.getBean(SaveAssignedIdFallbackVersionedBookRepository)

    @Override
    List<String> packages() {
        return Arrays.asList(getClass().package.name)
    }

    @Override
    Map<String, String> getProperties() {
        return H2TestPropertyProvider.super.getProperties() + [
                'datasources.default.url'                                : 'jdbc:h2:mem:saveAssignedIdFallbackToUpdate;LOCK_TIMEOUT=10000;DB_CLOSE_ON_EXIT=FALSE',
                'micronaut.data.save-assigned-id-fallback-to-update': 'true'
        ]
    }

    void "save falls back to update for non-versioned entities with assigned IDs"() {
        when:
        SaveAssignedIdFallbackBook saved = repository.save(new SaveAssignedIdFallbackBook(id: 1, title: 'First'))

        then:
        saved.id == 1
        repository.findById(1L).get().title == 'First'

        when:
        SaveAssignedIdFallbackBook updated = repository.save(new SaveAssignedIdFallbackBook(id: 1, title: 'Second'))

        then:
        updated.id == 1
        repository.findById(1L).get().title == 'Second'
    }

    void "save does not fall back to update for negative assigned IDs"() {
        given:
        repository.save(new SaveAssignedIdFallbackBook(id: -1, title: 'Negative first'))

        when:
        repository.save(new SaveAssignedIdFallbackBook(id: -1, title: 'Negative second'))

        then:
        thrown(DataAccessException)
        repository.findById(-1L).get().title == 'Negative first'
    }

    void "save does not fall back to update for versioned assigned IDs"() {
        given:
        SaveAssignedIdFallbackVersionedBook saved = versionedRepository.save(new SaveAssignedIdFallbackVersionedBook(id: 1, title: 'Versioned first'))

        when:
        versionedRepository.save(new SaveAssignedIdFallbackVersionedBook(id: 1, title: 'Versioned second', version: saved.version))

        then:
        thrown(DataAccessException)
        versionedRepository.findById(1L).get().title == 'Versioned first'
    }
}

@MappedEntity("save_assigned_id_fallback_book")
class SaveAssignedIdFallbackBook {
    @Id
    Long id
    String title
}

@MappedEntity("save_assigned_id_fallback_versioned_book")
class SaveAssignedIdFallbackVersionedBook {
    @Id
    Long id
    String title
    @Version
    long version
}

@JdbcRepository(dialect = Dialect.H2)
interface SaveAssignedIdFallbackBookRepository extends CrudRepository<SaveAssignedIdFallbackBook, Long> {
}

@JdbcRepository(dialect = Dialect.H2)
interface SaveAssignedIdFallbackVersionedBookRepository extends CrudRepository<SaveAssignedIdFallbackVersionedBook, Long> {
}
