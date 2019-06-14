package io.micronaut.data.tck.tests

import io.micronaut.data.model.Pageable
import io.micronaut.data.tck.entities.Person
import io.micronaut.data.tck.repositories.PersonRepository
import spock.lang.Specification

abstract class AbstractCrudSpec extends Specification {

    abstract PersonRepository getCrudRepository()
    abstract void init()

    def setupSpec() {
        init()
        crudRepository.saveAll([
                new Person(name: "Jeff"),
                new Person(name: "James")
        ])
    }

    void "test save one"() {
        when:"one is saved"
        def person = new Person(name: "Fred")
        crudRepository.save(person)

        then:"the instance is persisted"
        person.id != null
        crudRepository.findById(person.id).isPresent()
        crudRepository.get(person.id).name == 'Fred'
        crudRepository.existsById(person.id)
        crudRepository.count() == 3
        crudRepository.count("Fred") == 1
        crudRepository.findAll().size() == 3
    }

    void "test save many"() {
        when:"many are saved"
        def p1 = crudRepository.save("Frank", 0)
        def p2 = crudRepository.save("Bob", 0)
        def people = [p1,p2]

        then:"all are saved"
        people.every { it.id != null }
        people.every { crudRepository.findById(it.id).isPresent() }
        crudRepository.findAll().size() == 5
        crudRepository.count() == 5
        crudRepository.count("Fred") == 1
        crudRepository.list(Pageable.from(1)).isEmpty()
        crudRepository.list(Pageable.from(0, 1)).size() == 1
    }

    void "test delete by id"() {
        when:"an entity is retrieved"
        def person = crudRepository.findByName("Frank")

        then:"the person is not null"
        person != null
        person.name == 'Frank'
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
}
