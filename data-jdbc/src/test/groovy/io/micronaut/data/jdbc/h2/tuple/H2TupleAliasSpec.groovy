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
package io.micronaut.data.jdbc.h2.tuple

import io.micronaut.context.ApplicationContext
import io.micronaut.data.annotation.GeneratedValue
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.data.annotation.Query
import io.micronaut.data.jdbc.annotation.JdbcRepository
import io.micronaut.data.jdbc.h2.H2TestPropertyProvider
import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.repository.CrudRepository
import jakarta.persistence.Tuple
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

/**
 * Tuple elements are looked up by the column alias of the query.
 */
class H2TupleAliasSpec extends Specification implements H2TestPropertyProvider {

    @Shared
    @AutoCleanup
    ApplicationContext ctx = ApplicationContext.run(getProperties())

    @Shared
    TupleItemRepository repository = ctx.getBean(TupleItemRepository)

    @Override
    List<String> packages() {
        return [getClass().package.name]
    }

    @Override
    Map<String, String> getProperties() {
        return H2TestPropertyProvider.super.getProperties() + [
            'datasources.default.url': 'jdbc:h2:mem:tupleAlias;LOCK_TIMEOUT=10000;DB_CLOSE_ON_EXIT=FALSE'
        ]
    }

    void "a tuple element is found by its column alias"() {
        given:
            repository.save(new TupleItem(name: "a"))
        when:
            List<Tuple> tuples = repository.findAliased()
        then:
            tuples.size() == 1
            tuples[0].get("ITEM_NAME") == "a"
        cleanup:
            repository.deleteAll()
    }
}

@JdbcRepository(dialect = Dialect.H2)
interface TupleItemRepository extends CrudRepository<TupleItem, Long> {

    @Query("SELECT name AS ITEM_NAME FROM tuple_item")
    List<Tuple> findAliased()
}

@MappedEntity
class TupleItem {
    @Id
    @GeneratedValue
    Long id

    String name
}
