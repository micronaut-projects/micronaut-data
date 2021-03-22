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

import io.micronaut.data.exceptions.EmptyResultException
import io.micronaut.data.model.Pageable
import io.micronaut.data.tck.entities.Person
import io.micronaut.data.tck.entities.PersonDto
import io.micronaut.data.tck.repositories.PersonAsyncRepository
import spock.lang.Specification

import java.util.concurrent.ExecutionException

abstract class AbstractAsyncRepositorySpec extends Specification {

    abstract PersonAsyncRepository getPersonRepository()

    abstract void init()

    def setup() {
        init()
        personRepository.saveAll([
                new Person(name: "Jeff"),
                new Person(name: "James")
        ]).get()
    }

    def createFrankAndBob(){
        personRepository.save("Frank", 0).toCompletableFuture().get()
        personRepository.save("Bob", 0).toCompletableFuture().get()
    }

    def cleanup() {
        init()
        personRepository.deleteAll().get()
    }

    void "test no value"() {
        expect:
        isEmptyResult { personRepository.getMaxId().get() }
    }

    void "test save one"() {
        when:"one is saved"
        def person = new Person(name: "Fred", age: 30)
        personRepository.save(person).get()

        then:"the instance is persisted"
        person.id != null
        def personDto = personRepository.getByName("Fred").get()
        personDto instanceof PersonDto
        personDto.age == 30
        personRepository.queryByName("Fred").get().size() == 1
        personRepository.findById(person.id).get()
        personRepository.getById(person.id).get().name == 'Fred'
        personRepository.existsById(person.id).get()
        personRepository.count().get() == 3
        personRepository.count("Fred").get() == 1
        personRepository.findAll().get().size() == 3
    }

    void "test save many"() {
        when:"many are saved"
        def p1 = personRepository.save("Frank", 0).get()
        def p2 = personRepository.save("Bob", 0).get()
        def people = [p1,p2]

        then:"all are saved"
        people.every { it.id != null }
        people.every { personRepository.findById(it.id).get() != null }
        personRepository.findAll().get().size() == 4
        personRepository.count().get() == 4
        personRepository.count("Jeff").get() == 1
        personRepository.list(Pageable.from(1)).get().isEmpty()
        personRepository.list(Pageable.from(0, 1)).get().size() == 1
    }

    void "test update many"() {
        when:
        def people = personRepository.findAll().get().toList()
        people.forEach() { it.name = it.name + " updated" }
        def recordsUpdated = personRepository.updateAll(people).get()
        people = personRepository.findAll().get().toList()

        then:
        people.size() == 2
        recordsUpdated == 2
        people.get(0).name.endsWith(" updated")
        people.get(1).name.endsWith(" updated")

        when:
        people = personRepository.findAll().get().toList()
        people.forEach() { it.name = it.name + " X" }
        def peopleUpdated = personRepository.updatePeople(people).get().toList()
        people = personRepository.findAll().get()

        then:
        peopleUpdated.size() == 2
        people.get(0).name.endsWith(" X")
        people.get(1).name.endsWith(" X")
        peopleUpdated.get(0).name.endsWith(" X")
        peopleUpdated.get(1).name.endsWith(" X")
    }

    void "test custom insert"() {
        given:
        personRepository.deleteAll().get()
        personRepository.saveCustom([new Person(name: "Abc", age: 12), new Person(name: "Xyz", age: 22)]).get()

        when:
        def people = personRepository.findAll().get()

        then:
        people.size() == 2
        people.get(0).name == "Abc"
        people.get(1).name == "Xyz"
        people.get(0).age == 12
        people.get(1).age == 22
    }

    void "test custom update"() {
        given:
        personRepository.deleteAll().get()
        personRepository.saveCustom([
                new Person(name: "Dennis", age: 12),
                new Person(name: "Jeff", age: 22),
                new Person(name: "James", age: 12),
                new Person(name: "Dennis", age: 22)]
        ).get()

        when:
        def updated = personRepository.updateNamesCustom("Denis", "Dennis").get()
        def people = personRepository.findAll().get()

        then:
        updated == 2
        people.count { it.name == "Dennis"} == 0
        people.count { it.name == "Denis"} == 2
    }

    void "test custom single insert"() {
        given:
        personRepository.deleteAll().get()
        personRepository.saveCustomSingle(new Person(name: "Abc", age: 12)).get()

        when:
        def people = personRepository.findAll().get().toList()

        then:
        people.size() == 1
        people.get(0).name == "Abc"
    }

    void "test delete by id"() {
        when:"an entity is retrieved"
        createFrankAndBob()
        def person = personRepository.findByName("Frank").get()

        then:"the person is not null"
        person != null
        person.name == 'Frank'
        personRepository.findById(person.id).get()

        when:"the person is deleted"
        personRepository.deleteById(person.id).get()

        then:"They are really deleted"
        isEmptyResult { personRepository.findById(person.id).get() }
        personRepository.count().get() == 3
    }

    void "test delete by multiple ids"() {
        when:"A search for some people"
        createFrankAndBob()
        def allPeople = personRepository.findAll().get()
        def people = personRepository.findByNameLike("J%").get()

        then:
        allPeople.size() == 4
        people.size() == 2

        when:"the people are deleted"
        personRepository.deleteAll(people).get()

        then:"Only the correct people are deleted"
        people.every {person -> isEmptyResult { personRepository.findById(person.id).get() } }
        personRepository.count().get() == 2
    }

    void "test delete one"() {
        when:"A specific person is found and deleted"
        createFrankAndBob()
        def allPeople = personRepository.findAll().get()
        def bob = personRepository.findByName("Bob").get()

        then:"The person is present"
        bob != null
        allPeople.size() == 4

        when:"The person is deleted"
        personRepository.deleteById(bob.id).get()

        then:"They are deleted"
        isEmptyResult { personRepository.findById(bob.id).get() }
        personRepository.count().get() == 3
    }

    void "test update one"() {
        when:"A person is retrieved"
        createFrankAndBob()
        def frank = personRepository.findByName("Frank").get()

        then:"The person is present"
        frank != null

        when:"The person is updated"
        personRepository.updatePerson(frank.id, "Jack").get()

        then:"the person is updated"
        isEmptyResult { personRepository.findByName("Frank").get() }
        personRepository.findByName("Jack").get() != null

        when:
        def jack = personRepository.findByName("Jack").get()
        jack.setName("Joe")
        jack = personRepository.update(jack).get()

        then:
        jack.name == 'Joe'
        isEmptyResult { personRepository.findByName("Jack").get() }
        personRepository.findByName("Joe").get() != null
    }

    void "test delete all"() {
        when:"A new person is saved"
        personRepository.save("Greg", 30).toCompletableFuture().get()
        personRepository.save("Groot", 300).toCompletableFuture().get()

        then:"The count is 4"
        personRepository.count().get() == 4

        when:"batch delete occurs"
        def deleted = personRepository.deleteByNameLike("G%").get()

        then:"The count is back to 2 and it entries were deleted"
        deleted == 2
        personRepository.count().get() == 2

        when:"everything is deleted"
        personRepository.deleteAll().get()

        then:"data is gone"
        personRepository.count().get() == 0
    }

    private static boolean isEmptyResult(Closure<?> closure) {
        try {
            closure.call()
            return false
        } catch (ExecutionException e) {
            if (e.cause instanceof EmptyResultException) {
                return true
            }
            throw e
        }
    }
}
