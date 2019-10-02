/*
 * Copyright 2017-2019 original authors
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
package io.micronaut.data.tck.tests

import io.micronaut.data.model.Pageable
import io.micronaut.data.tck.entities.Person
import io.micronaut.data.tck.repositories.PersonReactiveRepository
import spock.lang.Specification
import spock.lang.Stepwise

@Stepwise
abstract class AbstractReactiveRepositorySpec extends Specification {

    abstract PersonReactiveRepository getPersonRepository()

    abstract void init()

    def setupSpec() {
        init()
        personRepository.saveAll([
                new Person(name: "Jeff"),
                new Person(name: "James")
        ]).toList().blockingGet()
    }

    void "test save one"() {
        when:"one is saved"
        def person = new Person(name: "Fred")
        personRepository.save(person).blockingGet()

        then:"the instance is persisted"
        person.id != null
        personRepository.findById(person.id).blockingGet()
        personRepository.getById(person.id).blockingGet().name == 'Fred'
        personRepository.existsById(person.id).blockingGet()
        personRepository.count().blockingGet() == 3
        personRepository.count("Fred").blockingGet() == 1
        personRepository.findAll().toList().blockingGet().size() == 3
    }

    void "test save many"() {
        when:"many are saved"
        def p1 = personRepository.save("Frank", 0).blockingGet()
        def p2 = personRepository.save("Bob", 0).blockingGet()
        def people = [p1,p2]

        then:"all are saved"
        people.every { it.id != null }
        people.every { personRepository.findById(it.id).blockingGet() != null }
        personRepository.findAll().toList().blockingGet().size() == 5
        personRepository.count().blockingGet() == 5
        personRepository.count("Fred").blockingGet() == 1
        personRepository.list(Pageable.from(1)).toList().blockingGet().isEmpty()
        personRepository.list(Pageable.from(0, 1)).toList().blockingGet().size() == 1
    }

    void "test delete by id"() {
        when:"an entity is retrieved"
        def person = personRepository.findByName("Frank").blockingGet()

        then:"the person is not null"
        person != null
        person.name == 'Frank'
        personRepository.findById(person.id).blockingGet() != null

        when:"the person is deleted"
        personRepository.deleteById(person.id).blockingGet()

        then:"They are really deleted"
        !personRepository.findById(person.id).blockingGet()
        personRepository.count().blockingGet() == 4
    }

    void "test delete by multiple ids"() {
        when:"A search for some people"
        def people = personRepository.findByNameLike("J%").toList().blockingGet()

        then:
        people.size() == 2

        when:"the people are deleted"
        personRepository.deleteAll(people).blockingGet()

        then:"Only the correct people are deleted"
        people.every { !personRepository.findById(it.id).blockingGet() }
        personRepository.count().blockingGet() == 2
    }

    void "test delete one"() {
        when:"A specific person is found and deleted"
        def bob = personRepository.findByName("Bob").blockingGet()

        then:"The person is present"
        bob != null

        when:"The person is deleted"
        personRepository.deleteById(bob.id).blockingGet()

        then:"They are deleted"
        !personRepository.findById(bob.id).blockingGet()
        personRepository.count().blockingGet() == 1
    }

    void "test update one"() {
        when:"A person is retrieved"
        def fred = personRepository.findByName("Fred").blockingGet()

        then:"The person is present"
        fred != null

        when:"The person is updated"
        personRepository.updatePerson(fred.id, "Jack").blockingGet()

        then:"the person is updated"
        personRepository.findByName("Fred").blockingGet() == null
        personRepository.findByName("Jack").blockingGet() != null
    }

    void "test delete all"() {
        when:"A new person is saved"
        personRepository.save("Greg", 30).blockingGet()
        personRepository.save("Groot", 300).blockingGet()

        then:"The count is 3"
        personRepository.count().blockingGet() == 3

        when:"batch delete occurs"
        def deleted = personRepository.deleteByNameLike("G%").blockingGet()

        then:"The count is back to 1 and it entries were deleted"
        deleted == 2
        personRepository.count().blockingGet() == 1

        when:"everything is deleted"
        personRepository.deleteAll().blockingGet()

        then:"data is gone"
        personRepository.count().blockingGet() == 0
    }
}
