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

import io.micronaut.context.ApplicationContext
import io.micronaut.data.exceptions.EmptyResultException
import io.micronaut.data.model.Page
import io.micronaut.data.model.Pageable
import io.micronaut.data.model.Sort
import io.micronaut.data.repository.jpa.criteria.DeleteSpecification
import io.micronaut.data.repository.jpa.criteria.PredicateSpecification
import io.micronaut.data.repository.jpa.criteria.QuerySpecification
import io.micronaut.data.repository.jpa.criteria.UpdateSpecification
import io.micronaut.data.tck.entities.Person
import io.micronaut.data.tck.entities.PersonDto
import io.micronaut.data.tck.repositories.PersonAsyncRepository
import jakarta.persistence.criteria.CriteriaBuilder
import jakarta.persistence.criteria.CriteriaUpdate
import jakarta.persistence.criteria.Predicate
import jakarta.persistence.criteria.Root
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

import java.util.concurrent.ExecutionException

import static io.micronaut.data.repository.jpa.criteria.QuerySpecification.where
import static io.micronaut.data.tck.repositories.PersonRepository.Specifications.nameEquals

abstract class AbstractAsyncRepositorySpec extends Specification {

    @AutoCleanup
    @Shared
    ApplicationContext context = ApplicationContext.run(properties)

    abstract PersonAsyncRepository getPersonRepository()

    void init() {
    }

    def setup() {
        cleanup()
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

        then: "all are saved"
        people.every { it.id != null }
        people.every { personRepository.findById(it.id).get() != null }
        personRepository.findAll().get().size() == 4
        personRepository.count().get() == 4
        personRepository.count("Jeff").get() == 1
        personRepository.list(Pageable.from(1)).get().isEmpty()
        personRepository.list(Pageable.from(0, 1)).get().size() == 1
    }

    def "test save all with empty collection"() {
        given:
        personRepository.deleteAll().get()

        when:
        personRepository.saveAll([]).get()

        then:
        personRepository.count().get() == 0
    }

    void "test update many"() {
        when:
        def people = personRepository.findAll().get()
        people.forEach() { it.name = it.name + " updated" }
        def recordsUpdated = personRepository.updateAll(people).get().size()
        people = personRepository.findAll().get()

        then:
        people.size() == 2
        recordsUpdated == 2
        people.get(0).name.endsWith(" updated")
        people.get(1).name.endsWith(" updated")

        when:
        people = personRepository.findAll().get()
        people.forEach() { it.name = it.name + " X" }
        def peopleUpdated = personRepository.updatePeople(people).get()
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
        def saved = personRepository.saveCustom([new Person(name: "Abc", age: 12), new Person(name: "Xyz", age: 22)]).get()

        when:
        def people = personRepository.findAll().get()

        then:
        saved == 2
        people.size() == 2
        people.get(0).name == "Abc"
        people.get(1).name == "Xyz"
        people.get(0).age == 12
        people.get(1).age == 22
    }

    void "test custom update"() {
        given:
        personRepository.deleteAll().get()
        def saved = personRepository.saveCustom([
                new Person(name: "Dennis", age: 12),
                new Person(name: "Jeff", age: 22),
                new Person(name: "James", age: 12),
                new Person(name: "Dennis", age: 22)]
        ).get()

        when:
        def updated = personRepository.updateNamesCustom("Denis", "Dennis").get()
        def people = personRepository.findAll().get()

        then:
        saved == 4
        updated == 2
        people.count { it.name == "Dennis"} == 0
        people.count { it.name == "Denis"} == 2
    }

    void "test custom single insert"() {
        given:
        personRepository.deleteAll().get()
        personRepository.saveCustomSingle(new Person(name: "Abc", age: 12)).get()

        when:
        def people = personRepository.findAll().get()

        then:
        people.size() == 1
        people.get(0).name == "Abc"
    }

    void "test delete by id"() {
        when:"an entity is retrieved"
        createFrankAndBob()
        def person = personRepository.findByName("Frank").get()
        def bob = personRepository.findByName("Bob").get()

        then:"the person is not null"
        person != null
        person.name == 'Frank'
        personRepository.findById(person.id).get()

        when:"the person is deleted"
        personRepository.deleteById(person.id).get()

        then:"They are really deleted"
        isEmptyResult { personRepository.findById(person.id).get() }
        personRepository.count().get() == 3

        when:"the person is deleted"
        def rowsDeleted = personRepository.remove(bob.id).get()

        then:"They are really deleted"
        rowsDeleted == 1
        isEmptyResult { personRepository.findById(bob.id).get() }
        personRepository.count().get() == 2
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
        def result = personRepository.deleteAll(people).thenAccept(r -> {
            if (r instanceof Number) {
                throw new IllegalStateException()
            }
        }).get()

        then:"Only the correct people are deleted"
        result == null
        people.every {person -> isEmptyResult { personRepository.findById(person.id).get() } }
        personRepository.count().get() == 2

        when:
        def rowsDeleted = personRepository.deleteManyReturnRowsDeleted(personRepository.findAll().get()).get()

        then:
        rowsDeleted == 2
        personRepository.findAll().get().isEmpty()
    }

    void "test delete one"() {
        when:"A specific person is found and deleted"
        createFrankAndBob()
        def allPeople = personRepository.findAll().get()
        def bob = personRepository.findByName("Bob").get()
        def jeff = personRepository.findByName("Jeff").get()
        def frank = personRepository.findByName("Frank").get()

        then:"The person is present"
        bob != null
        allPeople.size() == 4

        when:"The person is deleted"
        Void result = personRepository.deleteById(bob.id).thenAccept(r -> {
            if (r instanceof Number) {
                throw new IllegalStateException()
            }
        }).get()

        then:"They are deleted"
        result == null
        isEmptyResult { personRepository.findById(bob.id).get() }
        personRepository.count().get() == 3

        when:
        result = personRepository.delete(jeff).thenAccept(r -> {
            if (r instanceof Number) {
                throw new IllegalStateException()
            }
        }).get()
        then:
        result == null

        isEmptyResult { personRepository.findById(jeff.id).get() }
        personRepository.count().get() == 2

        when:
        def rowsDeleted = personRepository.deleteOneReturnRowsDeleted(frank).get()
        then:
        rowsDeleted == 1

        isEmptyResult { personRepository.findById(frank.id).get() }
        personRepository.count().get() == 1
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
        def result = personRepository.deleteAll().thenAccept(r -> {
            if (r instanceof Number) {
                throw new IllegalStateException()
            }
        }).join()

        then:"data is gone"
        result == null
        personRepository.count().get() == 0
    }

    void "test custom delete"() {
        given:
        personRepository.deleteAll().get()
        savePersons(["Dennis", "Jeff", "James", "Dennis"])

        when:
        def people = personRepository.findAll().get()
        people.findAll {it.name == "Dennis"}.forEach{ it.name = "DoNotDelete"}
        def deleted = personRepository.deleteCustom(people).get()
        people = personRepository.findAll().get()

        then:
        deleted == 2
        people.size() == 2
        people.count {it.name == "Dennis"}
    }

    void "test custom delete single"() {
        given:
        personRepository.deleteAll().get()
        savePersons(["Dennis", "Jeff", "James", "Dennis"])

        when:
        def people = personRepository.findAll().get()
        def jeff = people.find {it.name == "Jeff"}
        def deleted = personRepository.deleteCustomSingle(jeff).get()
        people = personRepository.findAll().get()

        then:
        deleted == 1
        people.size() == 3

        when:
        def james = people.find {it.name == "James"}
        james.name = "DoNotDelete"
        deleted = personRepository.deleteCustomSingle(james).get()
        people = personRepository.findAll().get()

        then:
        deleted == 0
        people.size() == 3
    }

    void "test custom delete single no entity"() {
        given:
        personRepository.deleteAll().get()
        savePersons(["Dennis", "Jeff", "James", "Dennis"])

        when:
        def people = personRepository.findAll().get()
        def jeff = people.find {it.name == "Jeff"}
        def deleted = personRepository.deleteCustomSingleNoEntity(jeff.getName()).get()
        people = personRepository.findAll().get()

        then:
        deleted == 1
        people.size() == 3
    }

    void "test criteria" () {
        when:
            personRepository.deleteAll().get()
            savePersons(["Jeff", "James"])
        then:
            personRepository.findOne(nameEquals("Jeff")).get()
            isEmptyResult { personRepository.findOne(nameEquals("Denis")).get() }
            personRepository.findOne(QuerySpecification.where(nameEquals("Jeff"))).get()
            isEmptyResult { personRepository.findOne(QuerySpecification.where(nameEquals("Denis"))).get() }
        then:
            personRepository.findAll(nameEquals("Jeff")).get().size() == 1
            personRepository.findAll(QuerySpecification.where(nameEquals("Jeff"))).get().size() == 1
            personRepository.findAll(nameEquals("Denis")).get().size() == 0
            personRepository.findAll(null as QuerySpecification).get().size() == 2
            personRepository.findAll(null as PredicateSpecification).get().size() == 2
            personRepository.findAll(nameEquals("Jeff").or(nameEquals("Denis"))).get().size() == 1
            personRepository.findAll(nameEquals("Jeff").and(nameEquals("Denis"))).get().size() == 0
            personRepository.findAll(nameEquals("Jeff").and(nameEquals("Jeff"))).get().size() == 1
            personRepository.findAll(nameEquals("Jeff").or(nameEquals("James"))).get().size() == 2
            personRepository.findAll(QuerySpecification.where(nameEquals("Jeff")).or(nameEquals("Denis"))).get().size() == 1
            personRepository.findAll(QuerySpecification.where(nameEquals("Jeff")).and(nameEquals("Denis"))).get().size() == 0
            personRepository.findAll(QuerySpecification.where(nameEquals("Jeff")).and(nameEquals("Jeff"))).get().size() == 1
            personRepository.findAll(QuerySpecification.where(nameEquals("Jeff")).or(nameEquals("James"))).get().size() == 2
            personRepository.findAll(QuerySpecification.where(nameEquals("Jeff")).or(nameEquals("James")), Sort.of(Sort.Order.desc("name"))).get()[1].name == "James"
            personRepository.findAll(QuerySpecification.where(nameEquals("Jeff")).or(nameEquals("James")), Sort.of(Sort.Order.asc("name"))).get()[1].name == "Jeff"
        when:
            def unpaged = personRepository.findAll(nameEquals("Jeff").or(nameEquals("James")), Pageable.UNPAGED).get()
        then:
            unpaged.content.size() == 2
            unpaged.totalSize == 2
        when:
            def unpagedSortedDesc = personRepository.findAll(nameEquals("Jeff").or(nameEquals("James")), Pageable.UNPAGED.order(Sort.Order.desc("name"))).get()
            def unpagedSortedAsc = personRepository.findAll(nameEquals("Jeff").or(nameEquals("James")), Pageable.UNPAGED.order(Sort.Order.asc("name"))).get()
        then:
            unpagedSortedDesc.content.size() == 2
            unpagedSortedDesc.content[1].name == "James"
            unpagedSortedAsc.content.size() == 2
            unpagedSortedAsc.content[1].name == "Jeff"
        when:
            def paged = personRepository.findAll(nameEquals("Jeff").or(nameEquals("James")), Pageable.from(0, 1)).get()
        then:
            paged.content.size() == 1
            paged.pageNumber == 0
            paged.totalPages == 2
            paged.totalSize == 2
        when:
            def pagedSortedDesc = personRepository.findAll(nameEquals("Jeff").or(nameEquals("James")), Pageable.from(0, 1).order(Sort.Order.desc("name"))).get()
        then:
            pagedSortedDesc.content.size() == 1
            pagedSortedDesc.content[0].name == "Jeff"
            pagedSortedDesc.pageNumber == 0
            pagedSortedDesc.totalPages == 2
            pagedSortedDesc.totalSize == 2
        when:
            def pagedSortedAsc = personRepository.findAll(QuerySpecification.where(nameEquals("Jeff")).or(nameEquals("James")), Pageable.from(0, 1).order(Sort.Order.asc("name"))).get()
        then:
            pagedSortedAsc.content.size() == 1
            pagedSortedAsc.content[0].name == "James"
            pagedSortedAsc.pageNumber == 0
            pagedSortedAsc.totalPages == 2
            pagedSortedAsc.totalSize == 2
        when:
            def countAllByPredicateSpec = personRepository.count(nameEquals("Jeff").or(nameEquals("James"))).get()
        then:
            countAllByPredicateSpec == 2
        when:
            def countOneByPredicateSpec = personRepository.count(nameEquals("Jeff")).get()
        then:
            countOneByPredicateSpec == 1
        when:
            def countAllByQuerySpec = personRepository.count(QuerySpecification.where(nameEquals("Jeff").or(nameEquals("James")))).get()
        then:
            countAllByQuerySpec == 2
        when:
            def countOneByQuerySpec = personRepository.count(QuerySpecification.where(nameEquals("Jeff"))).get()
        then:
            countOneByQuerySpec == 1
        when:
            def countAppByNullByPredicateSpec = personRepository.count(null as PredicateSpecification).get()
            def countAppByNullByQuerySpec = personRepository.count(null as QuerySpecification).get()
        then:
            countAppByNullByPredicateSpec == 2
            countAppByNullByQuerySpec == 2
        when:
            def deleted = personRepository.deleteAll(nameEquals("Jeff")).get()
            def all = personRepository.findAll().get()
        then:
            deleted == 1
            all.size() == 1
            all[0].name == "James"
        when:
            deleted = personRepository.deleteAll(null as DeleteSpecification).get()
            all = personRepository.findAll().get()
        then:
            deleted == 1
            all.size() == 0
        when:
            savePersons(["Jeff", "James"])
            def updated = personRepository.updateAll(new UpdateSpecification<Person>() {
                @Override
                Predicate toPredicate(Root<Person> root, CriteriaUpdate<?> query, CriteriaBuilder criteriaBuilder) {
                    query.set("name", "Xyz")
                    return criteriaBuilder.equal(root.get("name"), "Jeff")
                }
            }).get()
        then:
            updated == 1
            personRepository.count(nameEquals("Xyz")).get() == 1
            personRepository.count(nameEquals("Jeff")).get() == 0
        when:
            deleted = personRepository.deleteAll(DeleteSpecification.where(nameEquals("Xyz"))).get()
        then:
            deleted == 1
            personRepository.count(nameEquals("Xyz")).get() == 0
        when:
            savePersons(["Jeff"])
            def existsPredicateSpec = personRepository.exists(nameEquals("Jeff")).get()
            def existsNotPredicateSpec = personRepository.exists(nameEquals("NotJeff")).get()
            def existsQuerySpec = personRepository.exists(QuerySpecification.where(nameEquals("Jeff"))).get()
            def existsNotQuerySpec = personRepository.exists(QuerySpecification.where(nameEquals("NotJeff"))).get()
        then:
            existsPredicateSpec
            !existsNotPredicateSpec
            existsQuerySpec
            !existsNotQuerySpec
    }

    def setupPersonsForPageableTest() {
        personRepository.deleteAll().get()
        List<Person> people = []
        50.times { num ->
            ('A'..'Z').each {
                people << new Person(name: it * 5 + num)
            }
        }
        personRepository.saveAll(people).get()
    }

    void "test pageable list"() {
        given:
            setupPersonsForPageableTest()
        when: "All the people are count"
            def count = personRepository.count().get()

        then: "the count is correct"
            count == 1300

        when: "10 people are paged"
            def pageable = Pageable.from(0, 10, Sort.of(Sort.Order.asc("id")))
            Page<Person> page = personRepository.findAll(pageable).get()

        then: "The data is correct"
            page.content.size() == 10
            page.content.every() { it instanceof Person }
            page.content[0].name.startsWith("A")
            page.content[1].name.startsWith("B")
            page.totalSize == 1300
            page.totalPages == 130
            page.nextPageable().offset == 10
            page.nextPageable().size == 10

        when: "The next page is selected"
            pageable = page.nextPageable()
            page = personRepository.findAll(pageable).get()

        then: "it is correct"
            page.offset == 10
            page.pageNumber == 1
            page.content[0].name.startsWith("K")
            page.content.size() == 10

        when: "The previous page is selected"
            pageable = page.previousPageable()
            page = personRepository.findAll(pageable).get()

        then: "it is correct"
            page.offset == 0
            page.pageNumber == 0
            page.content[0].name.startsWith("A")
            page.content.size() == 10
    }

    void "test pageable sort"() {
        given:
            setupPersonsForPageableTest()
        when: "All the people are count"
            def count = personRepository.count().get()

        then: "the count is correct"
            count == 1300

        when: "10 people are paged"
            Page<Person> page = personRepository.findAll(
                    Pageable.from(0, 10)
                            .order("name", Sort.Order.Direction.DESC)
            ).get()

        then: "The data is correct"
            page.content.size() == 10
            page.content.every() { it instanceof Person }
            page.content[0].name.startsWith("Z")
            page.content[1].name.startsWith("Z")
            page.totalSize == 1300
            page.totalPages == 130
            page.nextPageable().offset == 10
            page.nextPageable().size == 10

        when: "The next page is selected"
            page = personRepository.findAll(page.nextPageable()).get()

        then: "it is correct"
            page.offset == 10
            page.pageNumber == 1
            page.content[0].name.startsWith("Z")
    }

    void "test pageable findBy"() {
        given:
            setupPersonsForPageableTest()
        when: "People are searched for"
            def pageable = Pageable.from(0, 10)
            Page<Person> page = personRepository.findByNameLike("A%", pageable).get()
            Page<Person> page2 = personRepository.findPeople("A%", pageable).get()

        then: "The page is correct"
            page.offset == 0
            page.pageNumber == 0
            page.totalSize == 50
            page2.totalSize == page.totalSize
            page.content

        when: "The next page is retrieved"
            page = personRepository.findByNameLike("A%", page.nextPageable()).get()

        then: "it is correct"
            page.offset == 10
            page.pageNumber == 1
            page.totalSize == 50
            page.nextPageable().offset == 20
            page.nextPageable().number == 2
    }

    protected void savePersons(List<String> names) {
        personRepository.saveAll(names.collect { new Person(name: it) }).get()
    }

    protected static boolean isEmptyResult(Closure<?> closure) {
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
