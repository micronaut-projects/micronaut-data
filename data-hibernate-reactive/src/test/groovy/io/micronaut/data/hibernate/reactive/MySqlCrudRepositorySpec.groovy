/*
 * Copyright 2017-2023 original authors
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
package io.micronaut.data.hibernate.reactive

import io.micronaut.context.ApplicationContext
import io.micronaut.data.model.Pageable
import io.micronaut.data.tck.entities.Person
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import spock.lang.PendingFeature
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Stepwise

import java.time.Duration
import java.time.temporal.ChronoUnit

/**
 * All tests in data-hibernate-reactive use Postgres but we need at least one for MySql.
 */
@MicronautTest(transactional = true, packages = "io.micronaut.data.tck.entities")
@Stepwise
class MySqlCrudRepositorySpec extends Specification implements MySqlHibernateReactiveProperties {

    private static final Duration TEST_TIMEOUT = Duration.of(3, ChronoUnit.SECONDS)

    @Inject
    @Shared
    PersonCrudRepository crudRepository

    @Inject
    @Shared
    ApplicationContext context

    /**
     * This will be called from each method, to avoid test failure on setup.
     * Currently MySql is not supported and will throw IllegalException because of timeout on block(timeout)
     */
    void setupData() {
        if (crudRepository.count().block(TEST_TIMEOUT) == 0) {
            crudRepository.saveAll([
                    new Person(name: "Jeff"),
                    new Person(name: "James")
            ]).collectList().block(TEST_TIMEOUT)
        }
    }

    @PendingFeature
    void "test save one"() {
        given:
        setupData()

        when:"one is saved"
        def person = new Person(name: "Fred")
        crudRepository.save(person).block()

        then:"the instance is persisted"
        person.id != null
        crudRepository.findById(person.id).blockOptional().isPresent()
        !crudRepository.findById(1000L).blockOptional().isPresent()
        crudRepository.getById(person.id).block().name == 'Fred'
        crudRepository.existsById(person.id).block()
        crudRepository.count().block() == 3
        crudRepository.count("Fred").block() == 1
        crudRepository.findAll().collectList().block().size() == 3
        crudRepository.listPeople("Fred").collectList().block().size() == 1
    }

    @PendingFeature
    void "test save many"() {
        given:
        setupData()

        when:"many are saved"
        def p1 = crudRepository.save("Frank", 0).block()
        def p2 = crudRepository.save("Bob", 0).block()
        def people = [p1,p2]

        then:"all are saved"
        people.every { it.id != null }
        people.every { crudRepository.findById(it.id).blockOptional().isPresent() }
        crudRepository.findAll().collectList().block().size() == 5
        crudRepository.count().block() == 5
        crudRepository.count("Fred").block() == 1
        crudRepository.list(Pageable.from(1)).collectList().block().isEmpty()
        crudRepository.list(Pageable.from(0, 1)).collectList().block().size() == 1

        when:"The person is updated with reactor"
        long result = crudRepository.updatePersonRx(crudRepository.findByName("Jeff").block().id).block()

        then:
        result == 1

    }

    @PendingFeature
    void "test delete by id"() {
        given:
        setupData()

        when:"an entity is retrieved"
        def person = crudRepository.findByName("Frank").block()

        then:"the person is not null"
        person != null
        person.name == 'Frank'
        crudRepository.findByName("Frank").block().name == person.name
        crudRepository.findById(person.id).blockOptional().isPresent()

        when:"the person is deleted"
        crudRepository.deleteById(person.id).block()

        then:"They are really deleted"
        !crudRepository.findById(person.id).blockOptional().isPresent()
        crudRepository.count().block() == 4
    }

    @PendingFeature
    void "test delete by multiple ids"() {
        given:
        setupData()

        when:"A search for some people"
        def people = crudRepository.findAllAndDelete("J%").block()

        then:
        people.size() == 2

        and:"Only the correct people are deleted"
        people.every { !crudRepository.findById(it.id).blockOptional().isPresent() }
        crudRepository.count().block() == 2
    }

    @PendingFeature
    void "test delete one"() {
        given:
        setupData()

        when:"A specific person is found and deleted"
        def bob = crudRepository.findOneAndDelete("Bob").block()

        then:"The person is present"
        bob != null

        and:"They are deleted"
        !crudRepository.findById(bob.id).blockOptional().isPresent()
        crudRepository.count().block() == 1
    }

    @PendingFeature
    void "test update one"() {
        given:
        setupData()

        when:"A person is retrieved"
        def fred = crudRepository.findByName("Fred").block()
        def id = fred.id
        def jack = crudRepository.findByName("Jack").block()

        then:"The person is present"
        fred != null
        jack == null

        when:"The person name is updated"
        crudRepository.updatePerson(id, "Jack").block()
        fred = crudRepository.findByName("Fred").block()
        jack = crudRepository.findByName("Jack").block()

        then:"the person name is updated"
        fred == null
        jack != null
        jack.name == "Jack"
    }

    @PendingFeature
    void "test delete all"() {
        given:
        setupData()

        when:"everything is deleted"
        crudRepository.deleteAll().block()

        then:"data is gone"
        crudRepository.count().block() == 0
    }

}
