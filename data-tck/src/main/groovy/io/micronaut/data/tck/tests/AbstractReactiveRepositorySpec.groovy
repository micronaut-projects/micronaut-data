/*
 * Copyright 2017-2020 original authors
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
import io.micronaut.data.tck.entities.PersonDto
import io.micronaut.data.tck.repositories.PersonReactiveRepository
import spock.lang.Specification
import spock.lang.Stepwise

@Stepwise
abstract class AbstractReactiveRepositorySpec extends Specification {

    abstract PersonReactiveRepository getPersonRepository()

    abstract void init()

    def setup() {
        init()
        personRepository.saveAll([
                new Person(name: "Jeff"),
                new Person(name: "James")
        ]).toList().blockingGet()
    }

    def createFrankAndBob(){
        personRepository.save("Frank", 0).blockingGet()
        personRepository.save("Bob", 0).blockingGet()
    }

    def cleanup() {
        init()
        personRepository.deleteAll().blockingGet()
    }

    void "test no value"() {
        expect:
        personRepository.getMaxId().isEmpty().blockingGet()
    }

    void "test save one"() {
        when:"one is saved"
        def person = new Person(name: "Fred", age: 30)
        personRepository.save(person).blockingGet()

        then:"the instance is persisted"
        person.id != null
        def personDto = personRepository.getByName("Fred").blockingGet()
        personDto instanceof PersonDto
        personDto.age == 30
        personRepository.queryByName("Fred").toList().blockingGet().size() == 1
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
        personRepository.findAll().toList().blockingGet().size() == 4
        personRepository.count().blockingGet() == 4
        personRepository.count("Jeff").blockingGet() == 1
        personRepository.list(Pageable.from(1)).toList().blockingGet().isEmpty()
        personRepository.list(Pageable.from(0, 1)).toList().blockingGet().size() == 1
    }

    void "test update many"() {
        when:
        def people = personRepository.findAll().blockingIterable().toList()
        people.forEach() { it.name = it.name + " updated" }
        def recordsUpdated = personRepository.updateAll(people).blockingGet()
        people = personRepository.findAll().blockingIterable().toList()

        then:
        people.size() == 2
        recordsUpdated == 2
        people.get(0).name.endsWith(" updated")
        people.get(1).name.endsWith(" updated")

        when:
        people = personRepository.findAll().blockingIterable().toList()
        people.forEach() { it.name = it.name + " X" }
        def peopleUpdated = personRepository.updatePeople(people).blockingIterable().toList()
        people = personRepository.findAll().blockingIterable().toList()

        then:
        peopleUpdated.size() == 2
        people.get(0).name.endsWith(" X")
        people.get(1).name.endsWith(" X")
        peopleUpdated.get(0).name.endsWith(" X")
        peopleUpdated.get(1).name.endsWith(" X")
    }

    void "test custom insert"() {
        given:
        personRepository.deleteAll().blockingGet()
        personRepository.saveCustom([new Person(name: "Abc", age: 12), new Person(name: "Xyz", age: 22)]).blockingGet()

        when:
        def people = personRepository.findAll().blockingIterable().toList()

        then:
        people.size() == 2
        people.get(0).name == "Abc"
        people.get(1).name == "Xyz"
        people.get(0).age == 12
        people.get(1).age == 22
    }

    void "test custom single insert"() {
        given:
        personRepository.deleteAll().blockingGet()
        personRepository.saveCustomSingle(new Person(name: "Abc", age: 12)).blockingGet()

        when:
        def people = personRepository.findAll().toList().blockingGet()

        then:
        people.size() == 1
        people.get(0).name == "Abc"
    }

    void "test custom update"() {
        given:
        personRepository.deleteAll().blockingGet()
        personRepository.saveCustom([
                new Person(name: "Dennis", age: 12),
                new Person(name: "Jeff", age: 22),
                new Person(name: "James", age: 12),
                new Person(name: "Dennis", age: 22)]
        ).blockingGet()

        when:
        def updated = personRepository.updateNamesCustom("Denis", "Dennis").blockingGet()
        def people = personRepository.findAll().blockingIterable().toList()

        then:
        updated == 2
        people.count { it.name == "Dennis"} == 0
        people.count { it.name == "Denis"} == 2
    }

    void "test delete by id"() {
        when:"an entity is retrieved"
        createFrankAndBob()
        def person = personRepository.findByName("Frank").blockingGet()

        then:"the person is not null"
        person != null
        person.name == 'Frank'
        personRepository.findById(person.id).blockingGet() != null

        when:"the person is deleted"
        personRepository.deleteById(person.id).blockingGet()

        then:"They are really deleted"
        !personRepository.findById(person.id).blockingGet()
        personRepository.count().blockingGet() == 3
    }

    void "test delete by multiple ids"() {
        when:"A search for some people"
        createFrankAndBob()
        def allPeople = personRepository.findAll().toList().blockingGet()
        def people = personRepository.findByNameLike("J%").toList().blockingGet()

        then:
        allPeople.size() == 4
        people.size() == 2

        when:"the people are deleted"
        personRepository.deleteAll(people).blockingGet()

        then:"Only the correct people are deleted"
        people.every { !personRepository.findById(it.id).blockingGet() }
        personRepository.count().blockingGet() == 2
    }

    void "test delete one"() {
        when:"A specific person is found and deleted"
        createFrankAndBob()
        def allPeople = personRepository.findAll().toList().blockingGet()
        def bob = personRepository.findByName("Bob").blockingGet()

        then:"The person is present"
        bob != null
        allPeople.size() == 4

        when:"The person is deleted"
        personRepository.deleteById(bob.id).blockingGet()

        then:"They are deleted"
        !personRepository.findById(bob.id).blockingGet()
        personRepository.count().blockingGet() == 3
    }

    void "test update one"() {
        when:"A person is retrieved"
        createFrankAndBob()
        def frank = personRepository.findByName("Frank").blockingGet()

        then:"The person is present"
        frank != null

        when:"The person is updated"
        personRepository.updatePerson(frank.id, "Jack").blockingGet()

        then:"the person is updated"
        personRepository.findByName("Frank").blockingGet() == null
        personRepository.findByName("Jack").blockingGet() != null

        when:
        def jack = personRepository.findByName("Jack").blockingGet()
        jack.setName("Joe")
        jack = personRepository.update(jack).blockingGet()

        then:
        jack.name == 'Joe'
        personRepository.findByName("Jack").blockingGet() == null
        personRepository.findByName("Joe").blockingGet() != null
    }

    void "test delete all"() {
        when:"A new person is saved"
        personRepository.save("Greg", 30).blockingGet()
        personRepository.save("Groot", 300).blockingGet()

        then:"The count is 4"
        personRepository.count().blockingGet() == 4

        when:"batch delete occurs"
        def deleted = personRepository.deleteByNameLike("G%").blockingGet()

        then:"The count is back to 2 and it entries were deleted"
        deleted == 2
        personRepository.count().blockingGet() == 2

        when:"everything is deleted"
        personRepository.deleteAll().blockingGet()

        then:"data is gone"
        personRepository.count().blockingGet() == 0
    }
}
