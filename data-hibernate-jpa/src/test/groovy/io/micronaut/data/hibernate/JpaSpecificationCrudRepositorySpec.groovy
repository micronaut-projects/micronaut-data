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
import io.micronaut.data.model.Page
import io.micronaut.data.model.Pageable
import io.micronaut.data.model.Sort
import io.micronaut.data.tck.entities.Person
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Stepwise

import jakarta.persistence.criteria.CriteriaBuilder
import jakarta.persistence.criteria.CriteriaQuery
import jakarta.persistence.criteria.Predicate
import jakarta.persistence.criteria.Root
import java.util.stream.Collectors

@MicronautTest(transactional = false, packages = "io.micronaut.data.tck.entities")
@Property(name = "datasources.default.name", value = "mydb")
@Property(name = 'jpa.default.properties.hibernate.hbm2ddl.auto', value = 'create-drop')
@Stepwise
class JpaSpecificationCrudRepositorySpec extends Specification {
    @Inject
    @Shared
    JpaSpecificationCrudRepository crudRepository

    def setupSpec() {
        crudRepository.saveAll([
                new Person(name: "Jeff", age: 50),
                new Person(name: "James", age: 35)
        ])
    }

    void "test save one"() {
        when: "one is saved"
        def person = new Person(name: "Fred", age: 40)
        crudRepository.save(person)

        then: "the instance is persisted"
        person.id != null
        crudRepository.findById(person.id).isPresent()
        crudRepository.get(person.id).name == 'Fred'
        crudRepository.existsById(person.id)
        crudRepository.count() == 3
        crudRepository.count("Fred") == 1
        crudRepository.findAll().size() == 3
        crudRepository.listPeople("Fred").size() == 1
        when:
        def page = crudRepository.findAll(new io.micronaut.data.jpa.repository.criteria.Specification<Person>() {
            @Override
            Predicate toPredicate(Root<Person> root, CriteriaQuery<?> query, CriteriaBuilder criteriaBuilder) {
                return null
            }
        }, Pageable.from(0, 10))
        then:
        page.size() == 3
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
        !crudRepository.queryAll(Pageable.from(0, 1)).isEmpty()
        crudRepository.list(Pageable.from(1, 10)).isEmpty()
        crudRepository.list(Pageable.from(0, 1)).size() == 1
    }

    void "test JPA specification count"() {
        expect:
        crudRepository.count(JpaSpecificationCrudRepository.Specifications.ageGreaterThanThirty()) == 4
        def results = crudRepository.findAll(JpaSpecificationCrudRepository.Specifications.ageGreaterThanThirty())
        results.size() == 4
        results.every({ it instanceof Person})

        def sorted = crudRepository.findAll(JpaSpecificationCrudRepository.Specifications.ageGreaterThanThirty(), Sort.of(Sort.Order.asc("age")))

        sorted.first().name == "James"
        sorted.last().name == "Jeff"

        !crudRepository.findOne(JpaSpecificationCrudRepository.Specifications.nameEquals("NotFound")).isPresent()
        crudRepository.findOne(JpaSpecificationCrudRepository.Specifications.nameEquals("James")).get().name == "James"
        def page2Req = Pageable.from(1, 2, Sort.of(Sort.Order.asc("age")))
        def page1Req = Pageable.from(0, 2, Sort.of(Sort.Order.asc("age")))
        def page1 = crudRepository.findAll(JpaSpecificationCrudRepository.Specifications.ageGreaterThanThirty(), page1Req)
        def page2 = crudRepository.findAll(JpaSpecificationCrudRepository.Specifications.ageGreaterThanThirty(), page2Req)
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

    void "test delete all"() {
        when:"everything is deleted"
        crudRepository.deleteAll()

        then:"data is gone"
        crudRepository.count() == 0
    }

    void "test order case insensitive"() {
        when:"items are saved"
        def p1 = new Person(name: "A", age: 20)
        def p2 = new Person(name: "c", age: 45)
        def p3 = new Person(name: "B", age: 42)
        def p4 = new Person(name: "b", age: 40)
        def p5 = new Person(name: "a", age: 50)
        def people = [p1, p2, p3, p4, p5]
        crudRepository.saveAll(people)
        then:"can be ordered case insensitively"
        def peopleIds = people.stream().map(p -> p.getId()).collect(Collectors.toList())
        Page<Person> personsPaged = crudRepository.findAll(new io.micronaut.data.jpa.repository.criteria.Specification<Person>() {
            @Override
            Predicate toPredicate(Root<Person> root, CriteriaQuery<?> query, CriteriaBuilder criteriaBuilder) {
                return root.get("id").in(peopleIds)
            }
        }, Pageable.from(0, 10, Sort.of(new Sort.Order("name", Sort.Order.Direction.ASC, true))))
        personsPaged.totalSize == 5
        def personNames = personsPaged.content.stream().map(p -> p.name).collect(Collectors.toList())
        personNames.size() == 5
        personNames[0].toLowerCase() == "a"
        personNames[1].toLowerCase() == "a"
        personNames[2].toLowerCase() == "b"
        personNames[3].toLowerCase() == "b"
        personNames[4] == "c"
    }

}
