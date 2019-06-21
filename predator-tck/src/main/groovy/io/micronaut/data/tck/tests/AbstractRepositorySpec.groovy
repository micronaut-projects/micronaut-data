package io.micronaut.data.tck.tests

import io.micronaut.data.model.Pageable
import io.micronaut.data.tck.entities.Book
import io.micronaut.data.tck.entities.Person
import io.micronaut.data.tck.repositories.AuthorRepository
import io.micronaut.data.tck.repositories.BookRepository
import io.micronaut.data.tck.repositories.PersonRepository
import spock.lang.Specification

abstract class AbstractRepositorySpec extends Specification {

    abstract PersonRepository getPersonRepository()
    abstract BookRepository getBookRepository()
    abstract AuthorRepository getAuthorRepository()

    abstract void init()

    def setupSpec() {
        init()
        personRepository.saveAll([
                new Person(name: "Jeff"),
                new Person(name: "James")
        ])
        bookRepository.save(new Book(title: "Anonymous", pages: 400))
        // blank title
        bookRepository.save(new Book(title: "", pages: 0))
        // book without an author
        bookRepository.setupData()
    }

    void "test save one"() {
        when:"one is saved"
        def person = new Person(name: "Fred")
        personRepository.save(person)

        then:"the instance is persisted"
        person.id != null
        personRepository.findById(person.id).isPresent()
        personRepository.get(person.id).name == 'Fred'
        personRepository.existsById(person.id)
        personRepository.count() == 3
        personRepository.count("Fred") == 1
        personRepository.findAll().size() == 3
    }

    void "test save many"() {
        when:"many are saved"
        def p1 = personRepository.save("Frank", 0)
        def p2 = personRepository.save("Bob", 0)
        def people = [p1,p2]

        then:"all are saved"
        people.every { it.id != null }
        people.every { personRepository.findById(it.id).isPresent() }
        personRepository.findAll().size() == 5
        personRepository.count() == 5
        personRepository.count("Fred") == 1
        personRepository.list(Pageable.from(1)).isEmpty()
        personRepository.list(Pageable.from(0, 1)).size() == 1
    }

    void "test delete by id"() {
        when:"an entity is retrieved"
        def person = personRepository.findByName("Frank")

        then:"the person is not null"
        person != null
        person.name == 'Frank'
        personRepository.findById(person.id).isPresent()

        when:"the person is deleted"
        personRepository.deleteById(person.id)

        then:"They are really deleted"
        !personRepository.findById(person.id).isPresent()
        personRepository.count() == 4
    }

    void "test delete by multiple ids"() {
        when:"A search for some people"
        def people = personRepository.findByNameLike("J%")

        then:
        people.size() == 2

        when:"the people are deleted"
        personRepository.deleteAll(people)

        then:"Only the correct people are deleted"
        people.every { !personRepository.findById(it.id).isPresent() }
        personRepository.count() == 2
    }

    void "test delete one"() {
        when:"A specific person is found and deleted"
        def bob = personRepository.findByName("Bob")

        then:"The person is present"
        bob != null

        when:"The person is deleted"
        personRepository.delete(bob)

        then:"They are deleted"
        !personRepository.findById(bob.id).isPresent()
        personRepository.count() == 1
    }

    void "test update one"() {
        when:"A person is retrieved"
        def fred = personRepository.findByName("Fred")

        then:"The person is present"
        fred != null

        when:"The person is updated"
        personRepository.updatePerson(fred.id, "Jack")

        then:"the person is updated"
        personRepository.findByName("Fred") == null
        personRepository.findByName("Jack") != null
    }

    void "test delete all"() {
        when:"everything is deleted"
        personRepository.deleteAll()

        then:"data is gone"
        personRepository.count() == 0
    }


    void "test is null or empty"() {
        expect:
        bookRepository.count() == 8
        bookRepository.findByAuthorIsNull().size() == 2
        bookRepository.findByAuthorIsNotNull().size() == 6
        bookRepository.countByTitleIsEmpty() == 1
        bookRepository.countByTitleIsNotEmpty() == 7
    }


    void "test string comparison methods"() {
        expect:
        authorRepository.countByNameContains("e") == 2
        authorRepository.findByNameStartsWith("S").name == "Stephen King"
        authorRepository.findByNameEndsWith("w").name == "Don Winslow"
        authorRepository.findByNameIgnoreCase("don winslow").name == "Don Winslow"
    }
}
