package io.micronaut.data.tck.tests

import io.micronaut.data.exceptions.EmptyResultException
import io.micronaut.data.model.Pageable
import io.micronaut.data.tck.entities.Book
import io.micronaut.data.tck.entities.BookDto
import io.micronaut.data.tck.entities.Company
import io.micronaut.data.tck.entities.Person
import io.micronaut.data.tck.repositories.AuthorRepository
import io.micronaut.data.tck.repositories.BookDtoRepository
import io.micronaut.data.tck.repositories.BookRepository
import io.micronaut.data.tck.repositories.CompanyRepository
import io.micronaut.data.tck.repositories.PersonRepository
import spock.lang.Specification

import java.time.LocalDate
import java.time.Year
import java.time.YearMonth

abstract class AbstractRepositorySpec extends Specification {

    abstract PersonRepository getPersonRepository()
    abstract BookRepository getBookRepository()
    abstract AuthorRepository getAuthorRepository()
    abstract CompanyRepository getCompanyRepository()
    abstract BookDtoRepository getBookDtoRepository()
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

    void "test project on single property"() {
        given:
        // cleanup invalid titles
        bookRepository.deleteByTitleIsEmptyOrTitleIsNull()

        personRepository.save(new Person(name: "Jeff", age: 40))
        personRepository.saveAll([
                new Person(name: "Ivan", age: 30),
                new Person(name: "James", age: 35)
        ])

        expect:
        bookRepository.findTop3OrderByTitle().size() == 3
        bookRepository.findTop3OrderByTitle()[0].title == 'Along Came a Spider'
        personRepository.countByAgeGreaterThan(33) == 2
        personRepository.countByAgeLessThan(33) == 1
        personRepository.findAgeByName("Jeff") == 40
        personRepository.findAgeByName("Ivan") == 30
        personRepository.findMaxAgeByNameLike("J%") == 40
        personRepository.findMinAgeByNameLike("J%") == 35
        personRepository.getSumAgeByNameLike("J%") == 75
        personRepository.getAvgAgeByNameLike("J%") == 37
        personRepository.readAgeByNameLike("J%").sort() == [35,40]
        personRepository.findByNameLikeOrderByAge("J%")*.age == [35,40]
        personRepository.findByNameLikeOrderByAgeDesc("J%")*.age == [40,35]
    }

    void "test dto projection"() {
        when:
        def results = bookDtoRepository.findByTitleLike("The%")

        then:
        results.size() == 3
        results.every { it instanceof BookDto }
        results.every { it.title.startsWith("The")}
        bookDtoRepository.findOneByTitle("The Stand").title == "The Stand"

        when:"paged result check"
        def result = bookDtoRepository.searchByTitleLike("The%", Pageable.from(0))

        then:"the result is correct"
        result.totalSize == 3
        result.size == 10
        result.content.every { it instanceof BookDto }
        result.content.every { it.title.startsWith("The")}

        when:"Stream is used"
        def dto = bookDtoRepository.findStream("The Stand").findFirst().get()

        then:"The result is correct"
        dto instanceof BookDto
        dto.title == "The Stand"
    }

    void "test null argument handling" () {
        when:
        personRepository.countByAgeGreaterThan(null) == 2

        then:
        def e = thrown(IllegalArgumentException)
        e.message == 'Argument [wrapper] value is null and the method parameter is not declared as nullable'
    }

    void "test project on single ended association"() {
        expect:
        bookRepository.count() == 7
        bookRepository.findTop3ByAuthorNameOrderByTitle("Stephen King")
                .findFirst().get().title == "Pet Cemetery"
        bookRepository.findTop3ByAuthorNameOrderByTitle("Stephen King")
                .count() == 2
//      TODO: Support inverse association queries?
//        authorRepository.findByBooksTitle("The Stand").name == "Stephen King"
//        authorRepository.findByBooksTitle("The Border").name == "Don Winslow"
        bookRepository.findByAuthorName("Stephen King").size() == 2
    }

    void "test join on single ended association"() {
        when:
        def book = bookRepository.findByTitle("Pet Cemetery")

        then:
        book != null
        book.title == "Pet Cemetery"
        book.author != null
        book.author.id != null
        book.author.name == "Stephen King"
    }

    void "test find by name"() {
        when:
        Person p = personRepository.getByName("Fred")

        then:
        thrown(EmptyResultException)
        personRepository.findByName("Fred") == null // declares nullable
        !personRepository.findOptionalByName("Fred").isPresent()

        when:
        personRepository.save(new Person(name: "Fred"))
        personRepository.saveAll([new Person(name: "Bob"), new Person(name: "Fredrick")])
        p = personRepository.findByName("Bob")

        then:
        p != null
        p.name == "Bob"
        personRepository.findOptionalByName("Bob").isPresent()

        when:
        def results = personRepository.findAllByName("Bob")

        then:
        results.size() == 1
        results[0].name == 'Bob'

        when:
        results = personRepository.findAllByNameLike("Fred%", Pageable.from(0, 10))

        then:
        results.size() == 2

    }


    void "test date created and last updated"() {
        when:
        def company = new Company("Apple", new URL("http://apple.com"))
        companyRepository.save(company)
        def dateCreated = company.dateCreated

        GregorianCalendar calendar = getYearMonthDay(dateCreated)
        def retrieved = companyRepository.findById(company.myId).get()

        then:
        company.myId != null
        dateCreated != null
        company.lastUpdated.toEpochMilli().toString().startsWith(company.dateCreated.getTime().toString())
        retrieved.dateCreated == calendar.time

        when:
        companyRepository.update(company.myId, "Changed")
        def company2 = companyRepository.findById(company.myId).orElse(null)

        then:
        company.dateCreated.time == dateCreated.time
        retrieved.dateCreated.time == company2.dateCreated.time
        company2.name == 'Changed'
        company2.lastUpdated.toEpochMilli() > company2.dateCreated.time
    }

    private GregorianCalendar getYearMonthDay(Date dateCreated) {
        def cal = dateCreated.toCalendar()
        def localDate = LocalDate.of(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH))
        GregorianCalendar calendar = new GregorianCalendar(localDate.year, localDate.month.value, localDate.dayOfMonth)
        calendar
    }
}
