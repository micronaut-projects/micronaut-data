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
package io.micronaut.data.hibernate

import io.micronaut.context.annotation.Property
import io.micronaut.data.hibernate.spring.SpringCrudRepository
import io.micronaut.data.tck.entities.Person
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Stepwise

import jakarta.inject.Inject

@MicronautTest(rollback = false, packages = "io.micronaut.data.tck.entities")
@Property(name = "datasources.default.name", value = "mydb")
@Property(name = 'jpa.default.properties.hibernate.hbm2ddl.auto', value = 'create-drop')
@Stepwise
class SpringCrudRepositoryJpaSpec extends Specification {
    @Inject
    @Shared
    SpringCrudRepository crudRepository

    def setupSpec() {
        crudRepository.saveAll([
                new Person(name: "Jeff", age: 50),
                new Person(name: "James", age: 35)
        ])
    }

    void "test save one"() {
        when:"one is saved"
        def person = new Person(name: "Fred", age: 40)
        crudRepository.save(person)

        then:"the instance is persisted"
        person.id != null
        crudRepository.findById(person.id).isPresent()
        crudRepository.get(person.id).name == 'Fred'
        crudRepository.existsById(person.id)
        crudRepository.count() == 3
        crudRepository.count("Fred") == 1
        crudRepository.findAll().size() == 3
        crudRepository.listPeople("Fred").size() == 1
    }

    void "test save many"() {
        when:"many are saved"
        def p1 = new Person(name: "Frank", age: 20)
        def p2 = new Person(name: "Bob", age: 45)
        def people = [p1, p2]
        crudRepository.saveAll(people)

        then:"all are saved"
        people.every { it.id != null }
        people.every { crudRepository.findById(it.id).isPresent() }
        crudRepository.findAll().size() == 5
        crudRepository.count() == 5
        crudRepository.count("Fred") == 1
        !crudRepository.queryAll(PageRequest.of(0, 1)).isEmpty()
        crudRepository.list(PageRequest.of(1, 10)).isEmpty()
        crudRepository.list(PageRequest.of(0, 1)).size() == 1
    }

    void "test JPA specification count"() {
        expect:
        crudRepository.count(SpringCrudRepository.Specifications.ageGreaterThanThirty()) == 4
        def results = crudRepository.findAll(SpringCrudRepository.Specifications.ageGreaterThanThirty())
        results.size() == 4
        results.every({ it instanceof Person})

        def sorted = crudRepository.findAll(SpringCrudRepository.Specifications.ageGreaterThanThirty(), Sort.by("age"))

        sorted.first().name == "James"
        sorted.last().name == "Jeff"

        crudRepository.findOne(SpringCrudRepository.Specifications.nameEquals("James")).get().name == "James"

        def page2Req = PageRequest.of(1, 2, Sort.by("age"))
        def page1Req = PageRequest.of(0, 2, Sort.by("age"))
        def page1 = crudRepository.findAll(SpringCrudRepository.Specifications.ageGreaterThanThirty(), page1Req)
        def page2 = crudRepository.findAll(SpringCrudRepository.Specifications.ageGreaterThanThirty(), page2Req)
        page2.size == 2
        page2.content*.name == ["Bob", "Jeff"]
        page1.size == 2
        page1.content*.name == ["James", "Fred"]

    }

    void "test delete by id"() {
        when:"an entity is retrieved"
        def person = crudRepository.findByName("Frank")

        then:"the person is not null"
        person != null
        person.name == 'Frank'
        crudRepository.queryByName("Frank").name == person.name
        crudRepository.findById(person.id).isPresent()

        when:"the person is deleted"
        crudRepository.deleteById(person.id)

        then:"They are really deleted"
        !crudRepository.findById(person.id).isPresent()
        crudRepository.count() == 4
    }

    void "test delete by multiple ids"() {
        when:"A search for some people"
        def people = crudRepository.findByNameLike("J%")

        then:
        people.size() == 2

        when:"the people are deleted"
        crudRepository.deleteAll(people)

        then:"Only the correct people are deleted"
        people.every { !crudRepository.findById(it.id).isPresent() }
        crudRepository.count() == 2
    }

    void "test delete one"() {
        when:"A specific person is found and deleted"
        def bob = crudRepository.findByName("Bob")

        then:"The person is present"
        bob != null

        when:"The person is deleted"
        crudRepository.delete(bob)

        then:"They are deleted"
        !crudRepository.findById(bob.id).isPresent()
        crudRepository.count() == 1
    }

    void "test update one"() {
        when:"A person is retrieved"
        def fred = crudRepository.findByName("Fred")

        then:"The person is present"
        fred != null

        when:"The person is updated"
        crudRepository.updatePerson(fred.id, "Jack")

        then:"the person is updated"
        crudRepository.findByName("Fred") == null
        crudRepository.findByName("Jack") != null
    }

    void "test delete spec"() {
        when:"A person is saved"
        def p1 = new Person(name: "NewPerson", age: 25)
        crudRepository.save(p1)
        def found = crudRepository.findByName(p1.name)

        then:"Person is found"
        found
        found.name == p1.name

        when:"A new person deleted"
        def deletedCount = crudRepository.delete(SpringCrudRepository.Specifications.nameEquals(found.name))

        then:"Person is deleted"
        deletedCount == 1
        !crudRepository.findById(found.id).isPresent()
    }

    void "test delete all"() {
        when:"everything is deleted"
        crudRepository.deleteAll()

        then:"data is gone"
        crudRepository.count() == 0
    }

}
