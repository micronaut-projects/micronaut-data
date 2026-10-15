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

    void "DTO queries of different entities return their own values on every call"() {
        expect:
            (1..3).every {
                personRepository.findByAgeGreaterThan(35)*.name == ["Bob"] &&
                    petRepository.findByAgeGreaterThan(5)*.name == ["Tom"] &&
                    personRepository.findByAgeGreaterThan(0).collect { "$it.name:$it.age".toString() }.sort() == ["Ann:30", "Bob:40"] &&
                    petRepository.findByAgeGreaterThan(0).collect { "$it.name:$it.age".toString() }.sort() == ["Rex:3", "Tom:7"]
            }
    }

    void "different DTO queries of the same entity return their own values on every call"() {
        expect:
            (1..3).every {
                personRepository.findByAgeGreaterThan(35)*.name == ["Bob"] &&
                    personRepository.findOneByName("Ann").age == 30 &&
                    personRepository.findByAgeGreaterThan(35)*.age == [40]
            }
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
