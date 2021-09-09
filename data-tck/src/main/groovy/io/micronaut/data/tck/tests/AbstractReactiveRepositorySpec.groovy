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
import io.micronaut.data.model.Pageable
import io.micronaut.data.tck.entities.Person
import io.micronaut.data.tck.entities.PersonDto
import io.micronaut.data.tck.entities.Student
import io.micronaut.data.tck.repositories.PersonReactiveRepository
import io.micronaut.data.tck.repositories.StudentReactiveRepository
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Stepwise

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
        personRepository.deleteAll().blockingGet()
        studentRepository.deleteAll().blockingGet()
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
        personRepository.deleteAll().blockingGet()
        studentRepository.deleteAll().blockingGet()
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
        def recordsUpdated = personRepository.updateAll(people).toList().blockingGet().size()
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
        def saved = personRepository.saveCustom([new Person(name: "Abc", age: 12), new Person(name: "Xyz", age: 22)]).blockingGet()

        when:
        def people = personRepository.findAll().blockingIterable().toList()

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
        personRepository.deleteAll().blockingGet()
        def saved = personRepository.saveCustomSingle(new Person(name: "Abc", age: 12)).blockingGet()

        when:
        def people = personRepository.findAll().toList().blockingGet()

        then:
        saved == 1
        people.size() == 1
        people.get(0).name == "Abc"
    }

    void "test custom update"() {
        given:
        personRepository.deleteAll().blockingGet()
        def saved = personRepository.saveCustom([
                new Person(name: "Dennis", age: 12),
                new Person(name: "Jeff", age: 22),
                new Person(name: "James", age: 12),
                new Person(name: "Dennis", age: 22)]
        ).blockingGet()

        when:
        def updated = personRepository.updateNamesCustom("Denis", "Dennis").blockingGet()
        def people = personRepository.findAll().blockingIterable().toList()

        then:
        saved == 4
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
        def updated =personRepository.updatePerson(frank.id, "Jack").blockingGet()

        then:"the person is updated"
        updated == 1
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
        personRepository.deleteAll().blockingGet()
        savePersons(["Dennis", "Jeff", "James", "Dennis"])

        when:
        def people = personRepository.findAll().toList().blockingGet()
        people.findAll {it.name == "Dennis"}.forEach{ it.name = "DoNotDelete"}
        def deleted = personRepository.deleteCustom(people).blockingGet()
        people = personRepository.findAll().toList().blockingGet()

        then:
        deleted == 2
        people.size() == 2
        people.count {it.name == "Dennis"}
    }

    void "test custom delete single"() {
        given:
        personRepository.deleteAll().blockingGet()
        savePersons(["Dennis", "Jeff", "James", "Dennis"])

        when:
        def people = personRepository.findAll().toList().blockingGet()
        def jeff = people.find {it.name == "Jeff"}
        def deleted = personRepository.deleteCustomSingle(jeff).blockingGet()
        people = personRepository.findAll().toList().blockingGet()

        then:
        deleted == 1
        people.size() == 3

        when:
        def james = people.find {it.name == "James"}
        james.name = "DoNotDelete"
        deleted = personRepository.deleteCustomSingle(james).blockingGet()
        people = personRepository.findAll().toList().blockingGet()

        then:
        deleted == 0
        people.size() == 3
    }

    void "test custom delete single no entity"() {
        given:
        personRepository.deleteAll().blockingGet()
        savePersons(["Dennis", "Jeff", "James", "Dennis"])

        when:
        def people = personRepository.findAll().toList().blockingGet()
        def jeff = people.find {it.name == "Jeff"}
        def deleted = personRepository.deleteCustomSingleNoEntity(jeff.getName()).blockingGet()
        people = personRepository.findAll().toList().blockingGet()

        then:
        deleted == 1
        people.size() == 3
    }

    protected void savePersons(List<String> names) {
        personRepository.saveAll(names.collect { new Person(name: it) }).toList().blockingGet()
    }
}
