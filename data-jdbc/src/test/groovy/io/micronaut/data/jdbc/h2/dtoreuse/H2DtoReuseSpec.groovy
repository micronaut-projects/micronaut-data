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
package io.micronaut.data.jdbc.h2.dtoreuse

import io.micronaut.context.ApplicationContext
import io.micronaut.core.annotation.Introspected
import io.micronaut.data.annotation.GeneratedValue
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.data.annotation.MappedProperty
import io.micronaut.data.jdbc.annotation.JdbcRepository
import io.micronaut.data.jdbc.h2.H2TestPropertyProvider
import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.repository.CrudRepository
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

/**
 * DTO projections of the same DTO type from different entities, called repeatedly, so that the DTO entity reused for
 * a query is never the one built for another query.
 */
class H2DtoReuseSpec extends Specification implements H2TestPropertyProvider {

    @Shared
    @AutoCleanup
    ApplicationContext ctx = ApplicationContext.run(getProperties())

    @Shared
    ReusePersonRepository personRepository = ctx.getBean(ReusePersonRepository)

    @Shared
    ReusePetRepository petRepository = ctx.getBean(ReusePetRepository)

    @Override
    List<String> packages() {
        return [getClass().package.name]
    }

    @Override
    Map<String, String> getProperties() {
        return H2TestPropertyProvider.super.getProperties() + [
            'datasources.default.url': 'jdbc:h2:mem:dtoReuse;LOCK_TIMEOUT=10000;DB_CLOSE_ON_EXIT=FALSE'
        ]
    }

    def setupSpec() {
        personRepository.saveAll([new ReusePerson(name: "Ann", age: 30), new ReusePerson(name: "Bob", age: 40)])
        petRepository.saveAll([new ReusePet(name: "Rex", age: 3), new ReusePet(name: "Tom", age: 7)])
    }

    /**
     * Every round calls each query once, in this order, so that from the second round on each query maps its rows
     * with the DTO entity it built in the first round, right after the other queries used theirs.
     */
    void "round #round: #query returns its own rows"() {
        expect:
            run(query) == expected

        where:
            [round, query, expected] << (1..3).collectMany { int round ->
                [
                    [round, "people older than 35", ["Bob:40"]],
                    [round, "pets older than 5", ["Tom:7"]],
                    [round, "all people", ["Ann:30", "Bob:40"]],
                    [round, "all pets", ["Rex:3", "Tom:7"]],
                    [round, "the person named Ann", ["Ann:30"]]
                ]
            }
    }

    /**
     * Runs a DTO query. The people and pets queries of the same name map the same DTO type from entities whose
     * properties are stored in differently named columns.
     *
     * @param query The query
     * @return The rows as name:age, sorted
     */
    private List<String> run(String query) {
        List<NameAndAge> rows = switch (query) {
            case "people older than 35" -> personRepository.findByAgeGreaterThan(35)
            case "pets older than 5" -> petRepository.findByAgeGreaterThan(5)
            case "all people" -> personRepository.findByAgeGreaterThan(0)
            case "all pets" -> petRepository.findByAgeGreaterThan(0)
            case "the person named Ann" -> [personRepository.findOneByName("Ann")]
            default -> throw new IllegalArgumentException(query)
        }
        return rows.collect { "$it.name:$it.age".toString() }.sort()
    }
}

@JdbcRepository(dialect = Dialect.H2)
interface ReusePersonRepository extends CrudRepository<ReusePerson, Long> {

    List<NameAndAge> findByAgeGreaterThan(int age)

    NameAndAge findOneByName(String name)
}

@JdbcRepository(dialect = Dialect.H2)
interface ReusePetRepository extends CrudRepository<ReusePet, Long> {

    List<NameAndAge> findByAgeGreaterThan(int age)
}

@MappedEntity
class ReusePerson {
    @Id
    @GeneratedValue
    Long id

    String name

    int age
}

@MappedEntity
class ReusePet {
    @Id
    @GeneratedValue
    Long id

    @MappedProperty("pet_name")
    String name

    @MappedProperty("pet_age")
    int age
}

@Introspected
class NameAndAge {
    String name
    int age
}
