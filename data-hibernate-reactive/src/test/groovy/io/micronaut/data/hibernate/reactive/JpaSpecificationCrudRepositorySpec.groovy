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
package io.micronaut.data.hibernate.reactive

import io.micronaut.data.model.Pageable
import io.micronaut.data.model.Sort
import io.micronaut.data.repository.jpa.criteria.CriteriaDeleteBuilder
import io.micronaut.data.repository.jpa.criteria.CriteriaQueryBuilder
import io.micronaut.data.repository.jpa.criteria.CriteriaUpdateBuilder
import io.micronaut.data.repository.jpa.criteria.DeleteSpecification
import io.micronaut.data.repository.jpa.criteria.PredicateSpecification
import io.micronaut.data.repository.jpa.criteria.QuerySpecification
import io.micronaut.data.repository.jpa.criteria.UpdateSpecification
import io.micronaut.data.runtime.criteria.RuntimeCriteriaBuilder
import io.micronaut.data.tck.entities.Person
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import jakarta.persistence.criteria.CriteriaBuilder
import org.hibernate.query.criteria.HibernateCriteriaBuilder
import spock.lang.Shared
import spock.lang.Specification

import static io.micronaut.data.hibernate.reactive.JpaSpecificationCrudRepository.Specifications.*

@MicronautTest(transactional = false, packages = "io.micronaut.data.tck.entities")
class JpaSpecificationCrudRepositorySpec extends Specification implements PostgresHibernateReactiveProperties {
    @Inject
    @Shared
    JpaSpecificationCrudRepository crudRepository

    def setupSpec() {
        crudRepository.saveAll([
                new Person(name: "Jeff", age: 50),
                new Person(name: "James", age: 35)
        ]).then().block()
        def person = new Person(name: "Fred", age: 40)
        crudRepository.save(person).block()
        def p1 = new Person(name: "Frank", age: 20)
        def p2 = new Person(name: "Bob", age: 45)
        def people = [p1, p2]
        crudRepository.saveAll(people).then().block()
    }

    void "test JPA specification count"() {
        expect:
        crudRepository.count(ageGreaterThanThirty()).block() == 4
        // test with null specification as param is nullable
        crudRepository.count((QuerySpecification<Person>) null).block() >= 4
        def results = crudRepository.findAll(ageGreaterThanThirty()).collectList().block()
        results.size() == 4
        results.every({ it instanceof Person})

        def sorted = crudRepository.findAll(ageGreaterThanThirty(), Sort.of(Sort.Order.asc("age"))).collectList().block()

        sorted.first().name == "James"
        sorted.last().name == "Jeff"

        crudRepository.findOne(nameEquals("James")).block().name == "James"
        def page2Req = Pageable.from(1, 2, Sort.of(Sort.Order.asc("age")))
        def page1Req = Pageable.from(0, 2, Sort.of(Sort.Order.asc("age")))
        def page1 = crudRepository.findAll(ageGreaterThanThirty(), page1Req).block()
        def page2 = crudRepository.findAll(ageGreaterThanThirty(), page2Req).block()
        page2.size == 2
        page2.content*.name == ["Bob", "Jeff"]
        page1.size == 2
        page1.content*.name == ["James", "Fred"]

        // test with null specification
        crudRepository.findAll((QuerySpecification<Person>) null).collectList().block().size() >= 4
    }

    void "test criteria callbacks receive Hibernate criteria builder"() {
        given:
        def callbacks = []
        def prefix = "Criteria Builder ${UUID.randomUUID()}"
        def findName = "${prefix} Find"
        def deleteName = "${prefix} Delete"
        def deletePredicateName = "${prefix} Delete Predicate"
        def updateName = "${prefix} Update"
        def updateBuilderName = "${prefix} Update Builder"
        def updatedName = "${prefix} Updated"
        def updatedBuilderName = "${prefix} Updated Builder"
        crudRepository.saveAll([
                new Person(name: findName, age: 10),
                new Person(name: deleteName, age: 11),
                new Person(name: deletePredicateName, age: 12),
                new Person(name: updateName, age: 13),
                new Person(name: updateBuilderName, age: 14)
        ]).then().block()

        expect:
        crudRepository.findOne(querySpec(findName, "findOne query", callbacks)).block().name == findName
        crudRepository.findOne(predicateSpec(findName, "findOne predicate", callbacks)).block().name == findName
        crudRepository.findAll(querySpec(findName, "findAll query", callbacks)).collectList().block()*.name == [findName]
        crudRepository.findAll(predicateSpec(findName, "findAll predicate", callbacks)).collectList().block()*.name == [findName]
        crudRepository.findAll(querySpec(findName, "findAll sort query", callbacks), Sort.of(Sort.Order.asc("name"))).collectList().block()*.name == [findName]
        crudRepository.findAll(predicateSpec(findName, "findAll sort predicate", callbacks), Sort.of(Sort.Order.asc("name"))).collectList().block()*.name == [findName]
        crudRepository.findAll(querySpec(findName, "findAll page query", callbacks), Pageable.from(0, 1)).block().content*.name == [findName]
        crudRepository.findAll(predicateSpec(findName, "findAll page predicate", callbacks), Pageable.from(0, 1)).block().content*.name == [findName]
        crudRepository.count(querySpec(findName, "count query", callbacks)).block() == 1
        crudRepository.count(predicateSpec(findName, "count predicate", callbacks)).block() == 1
        crudRepository.exists(querySpec(findName, "exists query", callbacks)).block()
        crudRepository.exists(predicateSpec(findName, "exists predicate", callbacks)).block()
        crudRepository.findOne(criteriaQueryBuilder(findName, "findOne builder", callbacks)).block().name == findName
        crudRepository.findAll(criteriaQueryBuilder(findName, "findAll builder", callbacks)).collectList().block()*.name == [findName]

        when:
        def deleted = crudRepository.deleteAll(deleteSpec(deleteName, "deleteAll delete", callbacks)).block()
        def deletedByPredicate = crudRepository.deleteAll(predicateSpec(deletePredicateName, "deleteAll predicate", callbacks)).block()
        def updated = crudRepository.updateAll(updateSpec(updateName, updatedName, "updateAll update", callbacks)).block()
        def deletedByBuilder = crudRepository.deleteAll(criteriaDeleteBuilder(findName, "deleteAll builder", callbacks)).block()
        def updatedByBuilder = crudRepository.updateAll(criteriaUpdateBuilder(updateBuilderName, updatedBuilderName, "updateAll builder", callbacks)).block()

        then:
        deleted == 1
        deletedByPredicate == 1
        updated == 1
        deletedByBuilder == 1
        updatedByBuilder == 1
        crudRepository.exists(querySpec(updatedName, "exists updated", callbacks)).block()
        crudRepository.exists(querySpec(updatedBuilderName, "exists updated builder", callbacks)).block()
        callbacks.toSet().containsAll([
                "findOne query",
                "findOne predicate",
                "findAll query",
                "findAll predicate",
                "findAll sort query",
                "findAll sort predicate",
                "findAll page query",
                "findAll page predicate",
                "count query",
                "count predicate",
                "exists query",
                "exists predicate",
                "findOne builder",
                "findAll builder",
                "deleteAll delete",
                "deleteAll predicate",
                "updateAll update",
                "deleteAll builder",
                "updateAll builder"
        ])
    }

    private static PredicateSpecification<Person> predicateSpec(String name, String callback, List<String> callbacks) {
        return { root, criteriaBuilder ->
            assertHibernateCriteriaBuilder(criteriaBuilder, callback, callbacks)
            criteriaBuilder.equal(root.get("name"), name)
        } as PredicateSpecification<Person>
    }

    private static QuerySpecification<Person> querySpec(String name, String callback, List<String> callbacks) {
        return { root, query, criteriaBuilder ->
            assertHibernateCriteriaBuilder(criteriaBuilder, callback, callbacks)
            criteriaBuilder.equal(root.get("name"), name)
        } as QuerySpecification<Person>
    }

    private static DeleteSpecification<Person> deleteSpec(String name, String callback, List<String> callbacks) {
        return { root, query, criteriaBuilder ->
            assertHibernateCriteriaBuilder(criteriaBuilder, callback, callbacks)
            criteriaBuilder.equal(root.get("name"), name)
        } as DeleteSpecification<Person>
    }

    private static UpdateSpecification<Person> updateSpec(String name, String newName, String callback, List<String> callbacks) {
        return { root, query, criteriaBuilder ->
            assertHibernateCriteriaBuilder(criteriaBuilder, callback, callbacks)
            query.set("name", newName)
            criteriaBuilder.equal(root.get("name"), name)
        } as UpdateSpecification<Person>
    }

    private static CriteriaQueryBuilder<Person> criteriaQueryBuilder(String name, String callback, List<String> callbacks) {
        return { criteriaBuilder ->
            assertHibernateCriteriaBuilder(criteriaBuilder, callback, callbacks)
            def query = criteriaBuilder.createQuery(Person)
            def root = query.from(Person)
            query.select(root)
            query.where(criteriaBuilder.equal(root.get("name"), name))
            query
        } as CriteriaQueryBuilder<Person>
    }

    private static CriteriaDeleteBuilder<Person> criteriaDeleteBuilder(String name, String callback, List<String> callbacks) {
        return { criteriaBuilder ->
            assertHibernateCriteriaBuilder(criteriaBuilder, callback, callbacks)
            def query = criteriaBuilder.createCriteriaDelete(Person)
            def root = query.from(Person)
            query.where(criteriaBuilder.equal(root.get("name"), name))
            query
        } as CriteriaDeleteBuilder<Person>
    }

    private static CriteriaUpdateBuilder<Person> criteriaUpdateBuilder(String name, String newName, String callback, List<String> callbacks) {
        return { criteriaBuilder ->
            assertHibernateCriteriaBuilder(criteriaBuilder, callback, callbacks)
            def query = criteriaBuilder.createCriteriaUpdate(Person)
            def root = query.from(Person)
            query.set("name", newName)
            query.where(criteriaBuilder.equal(root.get("name"), name))
            query
        } as CriteriaUpdateBuilder<Person>
    }

    private static void assertHibernateCriteriaBuilder(CriteriaBuilder criteriaBuilder, String callback, List<String> callbacks) {
        assert criteriaBuilder instanceof HibernateCriteriaBuilder : "${callback} received ${criteriaBuilder.getClass().name}"
        assert !(criteriaBuilder instanceof RuntimeCriteriaBuilder) : "${callback} received Micronaut RuntimeCriteriaBuilder"
        callbacks.add(callback)
    }

}
