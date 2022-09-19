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
import io.micronaut.data.model.Page
import io.micronaut.data.model.Pageable
import io.micronaut.data.model.Sort
import io.micronaut.data.repository.jpa.criteria.DeleteSpecification
import io.micronaut.data.repository.jpa.criteria.PredicateSpecification
import io.micronaut.data.repository.jpa.criteria.QuerySpecification
import io.micronaut.data.repository.jpa.criteria.UpdateSpecification
import io.micronaut.data.tck.entities.Person
import io.micronaut.data.tck.entities.PersonDto
import io.micronaut.data.tck.entities.Student
import io.micronaut.data.tck.repositories.PersonReactiveRepository
import io.micronaut.data.tck.repositories.StudentReactiveRepository
import jakarta.persistence.criteria.CriteriaBuilder
import jakarta.persistence.criteria.CriteriaUpdate
import jakarta.persistence.criteria.Predicate
import jakarta.persistence.criteria.Root
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Stepwise

import static io.micronaut.data.repository.jpa.criteria.QuerySpecification.where
import static io.micronaut.data.tck.repositories.PersonRepository.Specifications.nameEquals

@Stepwise
abstract class AbstractReactiveRepositorySpec extends Specification {

    @AutoCleanup
    @Shared
    ApplicationContext context = ApplicationContext.run(properties)

    abstract PersonReactiveRepository getPersonRepository()
    abstract StudentReactiveRepository getStudentRepository()

    void init() {
    }

    def setup() {
        init()
        personRepository.deleteAll().block()
        studentRepository.deleteAll().blockingGet()
        personRepository.saveAll([
                new Person(name: "Jeff"),
                new Person(name: "James")
        ]).collectList().block()
    }

    def createFrankAndBob(){
        personRepository.save("Frank", 0).block()
        personRepository.save("Bob", 0).block()
    }

    def cleanup() {
        personRepository.deleteAll().block()
        studentRepository.deleteAll().blockingGet()
    }

    void "test no value"() {
        expect:
        !personRepository.getMaxId().blockOptional().isPresent()
    }

    void "test save one"() {
        when:"one is saved"
        def person = new Person(name: "Fred", age: 30)
        personRepository.save(person).block()

        then:"the instance is persisted"
        person.id != null
        def personDto = personRepository.getByName("Fred").block()
        personDto instanceof PersonDto
        personDto.age == 30
        personRepository.queryByName("Fred").collectList().block().size() == 1
        personRepository.findById(person.id).block()
        personRepository.getById(person.id).block().name == 'Fred'
        personRepository.existsById(person.id).block()
        personRepository.count().block() == 3
        personRepository.count("Fred").block() == 1
        personRepository.findAll().collectList().block().size() == 3
    }

    void "test save many"() {
        when:"many are saved"
        def p1 = personRepository.save("Frank", 0).block()
        def p2 = personRepository.save("Bob", 0).block()
        def people = [p1,p2]

        then:"all are saved"
        people.every { it.id != null }
        people.every { personRepository.findById(it.id).block() != null }
        personRepository.findAll().collectList().block().size() == 4
        personRepository.count().block() == 4
        personRepository.count("Jeff").block() == 1
        personRepository.list(Pageable.from(1)).collectList().block().isEmpty()
        personRepository.list(Pageable.from(0, 1)).collectList().block().size() == 1
    }

    void "test update many"() {
        when:
        def people = personRepository.findAll().collectList().block()
        people.forEach() { it.name = it.name + " updated" }
        def recordsUpdated = personRepository.updateAll(people).collectList().block().size()
        people = personRepository.findAll().collectList().block()

        then:
        people.size() == 2
        recordsUpdated == 2
        people.get(0).name.endsWith(" updated")
        people.get(1).name.endsWith(" updated")

        when:
        people = personRepository.findAll().collectList().block()
        people.forEach() { it.name = it.name + " X" }
        def peopleUpdated = personRepository.updatePeople(people).collectList().block()
        people = personRepository.findAll().collectList().block()

        then:
        peopleUpdated.size() == 2
        people.get(0).name.endsWith(" X")
        people.get(1).name.endsWith(" X")
        peopleUpdated.get(0).name.endsWith(" X")
        peopleUpdated.get(1).name.endsWith(" X")
    }

    void "test custom insert"() {
        given:
        personRepository.deleteAll().block()
        def saved = personRepository.saveCustom([new Person(name: "Abc", age: 12), new Person(name: "Xyz", age: 22)]).block()

        when:
        def people = personRepository.findAll().collectList().block()

        then:
        saved == 2
        people.size() == 2
        people.get(0).name == "Abc"
        people.get(1).name == "Xyz"
        people.get(0).age == 12
        people.get(1).age == 22
    }

    void "test custom single insert"() {
        given:
        personRepository.deleteAll().block()
        def saved = personRepository.saveCustomSingle(new Person(name: "Abc", age: 12)).block()

        when:
        def people = personRepository.findAll().collectList().block()

        then:
        saved == 1
        people.size() == 1
        people.get(0).name == "Abc"
    }

    void "test custom update"() {
        given:
        personRepository.deleteAll().block()
        def saved = personRepository.saveCustom([
                new Person(name: "Dennis", age: 12),
                new Person(name: "Jeff", age: 22),
                new Person(name: "James", age: 12),
                new Person(name: "Dennis", age: 22)]
        ).block()

        when:
        def updated = personRepository.updateNamesCustom("Denis", "Dennis").block()
        def people = personRepository.findAll().collectList().block()

        then:
        saved == 4
        updated == 2
        people.count { it.name == "Dennis"} == 0
        people.count { it.name == "Denis"} == 2
    }

    void "test delete by id"() {
        when:"an entity is retrieved"
        createFrankAndBob()
        def person = personRepository.findByName("Frank").block()

        then:"the person is not null"
        person != null
        person.name == 'Frank'
        personRepository.findById(person.id).block() != null

        when:"the person is deleted"
        personRepository.deleteById(person.id).block()

        then:"They are really deleted"
        !personRepository.findById(person.id).block()
        personRepository.count().block() == 3
    }

    void "test delete by multiple ids"() {
        when:"A search for some people"
        createFrankAndBob()
        def allPeople = personRepository.findAll().collectList().block()
        def people = personRepository.findByNameLike("J%").collectList().block()

        then:
        allPeople.size() == 4
        people.size() == 2

        when:"the people are deleted"
        personRepository.deleteAll(people).block()

        then:"Only the correct people are deleted"
        people.every { !personRepository.findById(it.id).block() }
        personRepository.count().block() == 2
    }

    void "test delete one"() {
        when:"A specific person is found and deleted"
        createFrankAndBob()
        def allPeople = personRepository.findAll().collectList().block()
        def bob = personRepository.findByName("Bob").block()

        then:"The person is present"
        bob != null
        allPeople.size() == 4

        when:"The person is deleted"
        personRepository.deleteById(bob.id).block()

        then:"They are deleted"
        !personRepository.findById(bob.id).block()
        personRepository.count().block() == 3
    }

    void "test update one"() {
        when:"A person is retrieved"
        createFrankAndBob()
        def frank = personRepository.findByName("Frank").block()

        then:"The person is present"
        frank != null

        when:"The person is updated"
        def updated =personRepository.updatePerson(frank.id, "Jack").block()

        then:"the person is updated"
        updated == 1
        personRepository.findByName("Frank").block() == null
        personRepository.findByName("Jack").block() != null

        when:
        def jack = personRepository.findByName("Jack").block()
        jack.setName("Joe")
        jack = personRepository.update(jack).block()

        then:
        jack.name == 'Joe'
        personRepository.findByName("Jack").block() == null
        personRepository.findByName("Joe").block() != null
    }

    void "test delete all"() {
        when:"A new person is saved"
        personRepository.save("Greg", 30).block()
        personRepository.save("Groot", 300).block()

        then:"The count is 4"
        personRepository.count().block() == 4

        when:"batch delete occurs"
        def deleted = personRepository.deleteByNameLike("G%").block()

        then:"The count is back to 2 and it entries were deleted"
        deleted == 2
        personRepository.count().block() == 2

        when:"everything is deleted"
        personRepository.deleteAll().block()

        then:"data is gone"
        personRepository.count().block() == 0
    }

    boolean skipOptimisticLockingTest() {
        return false
    }

    def "test optimistic locking"() {
        given:
            def student = new Student("Denis")
        when:
            studentRepository.save(student).blockingGet()
        then:
            student.version == 0
        when:
            student.setVersion(5)
            student.setName("Xyz")
            studentRepository.update(student).blockingGet()
        then:
            def e = thrown(Exception)
            e.message.contains "Execute update returned unexpected row count. Expected: 1 got: 0"
        when:
            e = studentRepository.updateByIdAndVersion(student.getId(), student.getVersion(), student.getName()).blockingGet()
        then:
            e.message.contains "Execute update returned unexpected row count. Expected: 1 got: 0"
        when:
            e = studentRepository.delete(student).blockingGet()
        then:
            e.message.contains "Execute update returned unexpected row count. Expected: 1 got: 0"
        when:
            e = studentRepository.deleteByIdAndVersionAndName(student.getId(), student.getVersion(), student.getName()).blockingGet()
        then:
            e.message.contains "Execute update returned unexpected row count. Expected: 1 got: 0"
        when:
            e = studentRepository.deleteByIdAndVersion(student.getId(), student.getVersion()).blockingGet()
        then:
            e.message.contains "Execute update returned unexpected row count. Expected: 1 got: 0"
        when:
            e = studentRepository.deleteAll([student]).blockingGet()
        then:
            e.message.contains "Execute update returned unexpected row count. Expected: 1 got: 0"
        when:
            student = studentRepository.findById(student.getId()).blockingGet()
        then:
            student.name == "Denis"
            student.version == 0
        when:
            student.setName("Abc")
            studentRepository.update(student).blockingGet()
            def student2 = studentRepository.findById(student.getId()).blockingGet()
        then:
            student.version == 1
            student2.name == "Abc"
            student2.version == 1
        when:
            studentRepository.updateByIdAndVersion(student2.getId(), student2.getVersion(), "Joe").blockingGet()
            def student3 = studentRepository.findById(student2.getId()).blockingGet()
        then:
            student3.name == "Joe"
            student3.version == 2
        when:
            studentRepository.updateById(student2.getId(), "Joe2").blockingGet()
            def student4 = studentRepository.findById(student2.getId()).blockingGet()
        then:
            student4.name == "Joe2"
            student4.version == 2
        when:
            studentRepository.deleteByIdAndVersionAndName(student4.getId(), student4.getVersion(), student4.getName()).blockingGet()
            def student5 = studentRepository.findById(student2.getId())
        then:
            student5.isEmpty().blockingGet()
    }

    def "test batch optimistic locking"() {
        given:
            def student1 = new Student("Denis")
            def student2 = new Student("Frank")
        when:
            studentRepository.saveAll([student1, student2]).toList().blockingGet()
        then:
            student1.version == 0
            student2.version == 0
        when:
            student1 = studentRepository.findById(student1.getId()).blockingGet()
            student2 = studentRepository.findById(student2.getId()).blockingGet()
        then:
            student1.version == 0
            student2.version == 0
        when:
            studentRepository.updateAll([student1, student2]).toList().blockingGet()
            student1 = studentRepository.findById(student1.getId()).blockingGet()
            student2 = studentRepository.findById(student2.getId()).blockingGet()
        then:
            student1.version == 1
            student2.version == 1
        when:
            studentRepository.updateAll([student1, student2]).toList().blockingGet()
            student1 = studentRepository.findById(student1.getId()).blockingGet()
            student2 = studentRepository.findById(student2.getId()).blockingGet()
        then:
            student1.version == 2
            student2.version == 2
        when:
            student1.setVersion(5)
            student1.setName("Xyz")
            studentRepository.updateAll([student1, student2]).toList().blockingGet()
        then:
            def e = thrown(Exception)
            e.message.contains "Execute update returned unexpected row count. Expected: 2 got: 1"
        when:
            student1 = studentRepository.findById(student1.getId()).blockingGet()
            student2 = studentRepository.findById(student2.getId()).blockingGet()
            student1.setVersion(5)
            e = studentRepository.deleteAll([student1, student2]).blockingGet()
        then:
            e.message.contains "Execute update returned unexpected row count. Expected: 2 got: 1"
        cleanup:
            studentRepository.deleteAll().blockingGet()
    }

    void "test custom delete"() {
        given:
        personRepository.deleteAll().block()
        savePersons(["Dennis", "Jeff", "James", "Dennis"])

        when:
        def people = personRepository.findAll().collectList().block()
        people.findAll {it.name == "Dennis"}.forEach{ it.name = "DoNotDelete"}
        def deleted = personRepository.deleteCustom(people).block()
        people = personRepository.findAll().collectList().block()

        then:
        deleted == 2
        people.size() == 2
        people.count {it.name == "Dennis"}
    }

    void "test custom delete single"() {
        given:
        personRepository.deleteAll().block()
        savePersons(["Dennis", "Jeff", "James", "Dennis"])

        when:
        def people = personRepository.findAll().collectList().block()
        def jeff = people.find {it.name == "Jeff"}
        def deleted = personRepository.deleteCustomSingle(jeff).block()
        people = personRepository.findAll().collectList().block()

        then:
        deleted == 1
        people.size() == 3

        when:
        def james = people.find {it.name == "James"}
        james.name = "DoNotDelete"
        deleted = personRepository.deleteCustomSingle(james).block()
        people = personRepository.findAll().collectList().block()

        then:
        deleted == 0
        people.size() == 3
    }

    void "test custom delete single no entity"() {
        given:
        personRepository.deleteAll().block()
        savePersons(["Dennis", "Jeff", "James", "Dennis"])

        when:
        def people = personRepository.findAll().collectList().block()
        def jeff = people.find {it.name == "Jeff"}
        def deleted = personRepository.deleteCustomSingleNoEntity(jeff.getName()).block()
        people = personRepository.findAll().collectList().block()

        then:
        deleted == 1
        people.size() == 3
    }

    void "test criteria" () {
        when:
            personRepository.deleteAll().block()
            savePersons(["Jeff", "James"])
        then:
            personRepository.findOne(nameEquals("Jeff")).blockOptional().isPresent()
            !personRepository.findOne(nameEquals("Denis")).blockOptional().isPresent()
            personRepository.findOne(where(nameEquals("Jeff"))).blockOptional().isPresent()
            !personRepository.findOne(where(nameEquals("Denis"))).blockOptional().isPresent()
        then:
            personRepository.findAll(nameEquals("Jeff")).collectList().block().size() == 1
            personRepository.findAll(where(nameEquals("Jeff"))).collectList().block().size() == 1
            personRepository.findAll(nameEquals("Denis")).collectList().block().size() == 0
            personRepository.findAll(null as QuerySpecification).collectList().block().size() == 2
            personRepository.findAll(null as PredicateSpecification).collectList().block().size() == 2
            personRepository.findAll(null as QuerySpecification, Sort.of(Sort.Order.desc("name"))).collectList().block().size() == 2
            personRepository.findAll(null as PredicateSpecification, Sort.of(Sort.Order.desc("name"))).collectList().block().size() == 2
            personRepository.findAll(null as QuerySpecification, Pageable.from(Sort.of(Sort.Order.desc("name")))).block().getContent().size() == 2
            personRepository.findAll(null as PredicateSpecification, Pageable.from(Sort.of(Sort.Order.desc("name")))).block().getContent().size() == 2
            personRepository.findAll(nameEquals("Jeff").or(nameEquals("Denis"))).collectList().block().size() == 1
            personRepository.findAll(nameEquals("Jeff").and(nameEquals("Denis"))).collectList().block().size() == 0
            personRepository.findAll(nameEquals("Jeff").and(nameEquals("Jeff"))).collectList().block().size() == 1
            personRepository.findAll(nameEquals("Jeff").or(nameEquals("James"))).collectList().block().size() == 2
            personRepository.findAll(where(nameEquals("Jeff")).or(nameEquals("Denis"))).collectList().block().size() == 1
            personRepository.findAll(where(nameEquals("Jeff")).and(nameEquals("Denis"))).collectList().block().size() == 0
            personRepository.findAll(where(nameEquals("Jeff")).and(nameEquals("Jeff"))).collectList().block().size() == 1
            personRepository.findAll(where(nameEquals("Jeff")).or(nameEquals("James"))).collectList().block().size() == 2
            personRepository.findAll(where(nameEquals("Jeff")).or(nameEquals("James")), Sort.of(Sort.Order.desc("name"))).collectList().block()[1].name == "James"
            personRepository.findAll(where(nameEquals("Jeff")).or(nameEquals("James")), Sort.of(Sort.Order.asc("name"))).collectList().block()[1].name == "Jeff"
        when:
            def unpaged = personRepository.findAll(nameEquals("Jeff").or(nameEquals("James")), Pageable.UNPAGED).block()
        then:
            unpaged.content.size() == 2
            unpaged.totalSize == 2
        when:
            def unpagedSortedDesc = personRepository.findAll(nameEquals("Jeff").or(nameEquals("James")), Pageable.UNPAGED.order(Sort.Order.desc("name"))).block()
            def unpagedSortedAsc = personRepository.findAll(nameEquals("Jeff").or(nameEquals("James")), Pageable.UNPAGED.order(Sort.Order.asc("name"))).block()
        then:
            unpagedSortedDesc.content.size() == 2
            unpagedSortedDesc.content[1].name == "James"
            unpagedSortedAsc.content.size() == 2
            unpagedSortedAsc.content[1].name == "Jeff"
        when:
            def paged = personRepository.findAll(nameEquals("Jeff").or(nameEquals("James")), Pageable.from(0, 1)).block()
        then:
            paged.content.size() == 1
            paged.pageNumber == 0
            paged.totalPages == 2
            paged.totalSize == 2
        when:
            def pagedSortedDesc = personRepository.findAll(nameEquals("Jeff").or(nameEquals("James")), Pageable.from(0, 1).order(Sort.Order.desc("name"))).block()
        then:
            pagedSortedDesc.content.size() == 1
            pagedSortedDesc.content[0].name == "Jeff"
            pagedSortedDesc.pageNumber == 0
            pagedSortedDesc.totalPages == 2
            pagedSortedDesc.totalSize == 2
        when:
            def pagedSortedAsc = personRepository.findAll(where(nameEquals("Jeff")).or(nameEquals("James")), Pageable.from(0, 1).order(Sort.Order.asc("name"))).block()
        then:
            pagedSortedAsc.content.size() == 1
            pagedSortedAsc.content[0].name == "James"
            pagedSortedAsc.pageNumber == 0
            pagedSortedAsc.totalPages == 2
            pagedSortedAsc.totalSize == 2
        when:
            def countAllByPredicateSpec = personRepository.count(nameEquals("Jeff").or(nameEquals("James"))).block()
        then:
            countAllByPredicateSpec == 2
        when:
            def countOneByPredicateSpec = personRepository.count(nameEquals("Jeff")).block()
        then:
            countOneByPredicateSpec == 1
        when:
            def countAllByQuerySpec = personRepository.count(where(nameEquals("Jeff").or(nameEquals("James")))).block()
        then:
            countAllByQuerySpec == 2
        when:
            def countOneByQuerySpec = personRepository.count(where(nameEquals("Jeff"))).block()
        then:
            countOneByQuerySpec == 1
        when:
            def countAppByNullByPredicateSpec = personRepository.count(null as PredicateSpecification).block()
            def countAppByNullByQuerySpec = personRepository.count(null as QuerySpecification).block()
        then:
            countAppByNullByPredicateSpec == 2
            countAppByNullByQuerySpec == 2
        when:
            def deleted = personRepository.deleteAll(nameEquals("Jeff")).block()
            def all = personRepository.findAll().collectList().block()
        then:
            deleted == 1
            all.size() == 1
            all[0].name == "James"
        when:
            deleted = personRepository.deleteAll(null as DeleteSpecification).block()
            all = personRepository.findAll().collectList().block()
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
            }).block()
        then:
            updated == 1
            personRepository.count(nameEquals("Xyz")).block() == 1
            personRepository.count(nameEquals("Jeff")).block() == 0
        when:
            deleted = personRepository.deleteAll(DeleteSpecification.where(nameEquals("Xyz"))).block()
        then:
            deleted == 1
            personRepository.count(nameEquals("Xyz")).block() == 0
        when:
            savePersons(["Jeff"])
            def existsPredicateSpec = personRepository.exists(nameEquals("Jeff")).block()
            def existsNotPredicateSpec = personRepository.exists(nameEquals("NotJeff")).block()
            def existsQuerySpec = personRepository.exists(where(nameEquals("Jeff"))).block()
            def existsNotQuerySpec = personRepository.exists(where(nameEquals("NotJeff"))).block()
        then:
            existsPredicateSpec
            !existsNotPredicateSpec
            existsQuerySpec
            !existsNotQuerySpec
    }

    def setupPersonsForPageableTest() {
        personRepository.deleteAll().block()
        List<Person> people = []
        50.times { num ->
            ('A'..'Z').each {
                people << new Person(name: it * 5 + num)
            }
        }
        personRepository.saveAll(people).collectList().block()
    }

    void "test pageable list"() {
        given:
            setupPersonsForPageableTest()
        when: "All the people are count"
            def count = personRepository.count().block()

        then: "the count is correct"
            count == 1300

        when: "10 people are paged"
            def pageable = Pageable.from(0, 10, Sort.of(Sort.Order.asc("id")))
            Page<Person> page = personRepository.findAll(pageable).block()

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
            page = personRepository.findAll(pageable).block()

        then: "it is correct"
            page.offset == 10
            page.pageNumber == 1
            page.content[0].name.startsWith("K")
            page.content.size() == 10

        when: "The previous page is selected"
            pageable = page.previousPageable()
            page = personRepository.findAll(pageable).block()

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
            def count = personRepository.count().block()

        then: "the count is correct"
            count == 1300

        when: "10 people are paged"
            Page<Person> page = personRepository.findAll(
                    Pageable.from(0, 10)
                            .order("name", Sort.Order.Direction.DESC)
            ).block()

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
            page = personRepository.findAll(page.nextPageable()).block()

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
            Page<Person> page = personRepository.findByNameLike("A%", pageable).block()
            Page<Person> page2 = personRepository.findPeople("A%", pageable).block()

        then: "The page is correct"
            page.offset == 0
            page.pageNumber == 0
            page.totalSize == 50
            page2.totalSize == page.totalSize
            page.content

        when: "The next page is retrieved"
            page = personRepository.findByNameLike("A%", page.nextPageable()).block()

        then: "it is correct"
            page.offset == 10
            page.pageNumber == 1
            page.totalSize == 50
            page.nextPageable().offset == 20
            page.nextPageable().number == 2
    }

    protected void savePersons(List<String> names) {
        personRepository.saveAll(names.collect { new Person(name: it) }).collectList().block()
    }
}
