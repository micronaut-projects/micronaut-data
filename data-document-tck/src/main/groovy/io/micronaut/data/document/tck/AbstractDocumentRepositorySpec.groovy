/*
 * Copyright 2017-2022 original authors
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
package io.micronaut.data.document.tck

import io.micronaut.context.ApplicationContext
import io.micronaut.core.util.CollectionUtils
import io.micronaut.data.document.tck.entities.Author
import io.micronaut.data.document.tck.entities.AuthorBooksDto
import io.micronaut.data.document.tck.entities.BasicTypes
import io.micronaut.data.document.tck.entities.Book
import io.micronaut.data.document.tck.entities.BookDto
import io.micronaut.data.document.tck.entities.DomainEvents
import io.micronaut.data.document.tck.entities.Page
import io.micronaut.data.document.tck.entities.Person
import io.micronaut.data.document.tck.entities.Student
import io.micronaut.data.document.tck.repositories.AuthorRepository
import io.micronaut.data.document.tck.repositories.BasicTypesRepository
import io.micronaut.data.document.tck.repositories.BookRepository
import io.micronaut.data.document.tck.repositories.DocumentRepository
import io.micronaut.data.document.tck.repositories.DomainEventsRepository
import io.micronaut.data.document.tck.repositories.PersonRepository
import io.micronaut.data.document.tck.repositories.SaleRepository
import io.micronaut.data.document.tck.repositories.StudentRepository
import io.micronaut.data.exceptions.OptimisticLockException
import io.micronaut.data.model.Pageable
import io.micronaut.data.model.Slice
import io.micronaut.data.model.Sort
import io.micronaut.data.repository.jpa.criteria.DeleteSpecification
import io.micronaut.data.repository.jpa.criteria.PredicateSpecification
import io.micronaut.data.repository.jpa.criteria.QuerySpecification
import io.micronaut.data.repository.jpa.criteria.UpdateSpecification
import io.micronaut.transaction.SynchronousTransactionManager
import io.micronaut.transaction.TransactionCallback
import io.micronaut.transaction.TransactionStatus
import jakarta.persistence.criteria.CriteriaBuilder
import jakarta.persistence.criteria.CriteriaUpdate
import jakarta.persistence.criteria.Predicate
import jakarta.persistence.criteria.Root
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

import java.time.LocalDate
import java.util.stream.Collectors
import java.util.stream.Stream

import static io.micronaut.data.document.tck.repositories.PersonRepository.Specifications.dateOfBirthEquals
import static io.micronaut.data.document.tck.repositories.PersonRepository.Specifications.nameEquals

abstract class AbstractDocumentRepositorySpec extends Specification {

    abstract BasicTypesRepository getBasicTypeRepository()

    abstract PersonRepository getPersonRepository()

    abstract BookRepository getBookRepository()

    abstract AuthorRepository getAuthorRepository()

    abstract StudentRepository getStudentRepository()

    abstract SaleRepository getSaleRepository()

    abstract DomainEventsRepository getEventsRepository()

    abstract DocumentRepository getDocumentRepository()

    @AutoCleanup
    @Shared
    ApplicationContext context = ApplicationContext.run(properties)

    @Shared
    Optional<SynchronousTransactionManager<Object>> transactionManager = context.findBean(SynchronousTransactionManager)

    protected void setupBooks() {
        // book without an author
        bookRepository.save(new Book(title: "Anonymous", totalPages: 400))

        // blank title
        bookRepository.save(new Book(title: "", totalPages: 0))

        saveSampleBooks()
    }

    protected void saveSampleBooks() {
        bookRepository.saveAuthorBooks([
                new AuthorBooksDto("Stephen King", Arrays.asList(
                        new BookDto("The Stand", 1000),
                        new BookDto("Pet Cemetery", 400)
                )),
                new AuthorBooksDto("James Patterson", Arrays.asList(
                        new BookDto("Along Came a Spider", 300),
                        new BookDto("Double Cross", 300)
                )),
                new AuthorBooksDto("Don Winslow", Arrays.asList(
                        new BookDto("The Power of the Dog", 600),
                        new BookDto("The Border", 700)
                ))])
    }

    protected List<Person> savePersons(List<String> names) {
        int i = 0
        return personRepository.saveAll(names.collect { new Person(name: it, dateOfBirth: LocalDate.of(1986, 6, 1 + i++)) })
    }

    protected void setup() {
        cleanupData()
    }

    protected void cleanupData() {
        personRepository.deleteAll()
        bookRepository.deleteAll()
        authorRepository.deleteAll()
    }

    void "test save and retrieve basic types"() {
        when: "we save a new saved"
            def saved = basicTypeRepository.save(new BasicTypes())

        then: "The ID is assigned"
            saved.myId != null

        when: "A saved is found"
            def retrieved = basicTypeRepository.findById(saved.myId).orElse(null)

        then: "The saved is correct"
            retrieved.uuid == saved.uuid
            retrieved.bigDecimal == saved.bigDecimal
            retrieved.byteArray == saved.byteArray
            retrieved.charSequence == saved.charSequence
            retrieved.charset == saved.charset
            retrieved.primitiveBoolean == saved.primitiveBoolean
            retrieved.primitiveByte == saved.primitiveByte
            retrieved.primitiveChar == saved.primitiveChar
            retrieved.primitiveDouble == saved.primitiveDouble
            retrieved.primitiveFloat == saved.primitiveFloat
            retrieved.primitiveInteger == saved.primitiveInteger
            retrieved.primitiveLong == saved.primitiveLong
            retrieved.primitiveShort == saved.primitiveShort
            retrieved.wrapperBoolean == saved.wrapperBoolean
            retrieved.wrapperByte == saved.wrapperByte
            retrieved.wrapperChar == saved.wrapperChar
            retrieved.wrapperDouble == saved.wrapperDouble
            retrieved.wrapperFloat == saved.wrapperFloat
            retrieved.wrapperInteger == saved.wrapperInteger
            retrieved.wrapperLong == saved.wrapperLong
            retrieved.uri == saved.uri
            retrieved.url == saved.url
//            retrieved.instant == saved.instant
//            retrieved.localDateTime == saved.localDateTime
//            retrieved.zonedDateTime == saved.zonedDateTime
//            retrieved.offsetDateTime == saved.offsetDateTime
//            retrieved.dateCreated == saved.dateCreated
//            retrieved.dateUpdated == saved.dateUpdated
            retrieved.date == saved.date
    }

    void "test save and fetch author with no books"() {
        given:
            def author = new Author(name: "Some Dude")
            authorRepository.save(author)

            author = authorRepository.queryByName("Some Dude")

        expect:
            author.books.size() == 0

        cleanup:
            authorRepository.deleteById(author.id)
    }

    void "test save one"() {
        given:
            savePersons(["Jeff", "James"])

        when: "one is saved"
            def person = new Person(name: "Fred")
            personRepository.save(person)

        then: "the instance is persisted"
            person.id != null
            personRepository.findById(person.id).isPresent()
            personRepository.get(person.id).name == 'Fred'
            personRepository.existsById(person.id)
            !personRepository.existsById("61d69d67e8cb2c06b66d2e67")
            personRepository.count() == 3
            personRepository.count("Fred") == 1
            personRepository.findAll().size() == 3
    }

    void "test save many"() {
        given:
            savePersons(["Jeff", "James"])

        when: "many are saved"
            def p1 = personRepository.save("Frank", 0)
            def p2 = personRepository.save("Bob", 0)
            def people = [p1, p2]

        then: "all are saved"
            people.every { it.id != null }
            people.every { personRepository.findById(it.id).isPresent() }
            personRepository.findAll().size() == 4
            personRepository.count() == 4
            personRepository.count("Jeff") == 1

            personRepository.list(Pageable.from(1)).isEmpty()
            personRepository.list(Pageable.from(0, 1)).size() == 1
    }

    void "test update many"() {
        given:
            savePersons(["Jeff", "James"])

        when:
            def people = personRepository.findAll().toList()
            people.forEach() { it.name = it.name + " updated" }
            personRepository.updateAll(people)
            people = personRepository.findAll().toList()

        then:
            people.get(0).name.endsWith(" updated")
            people.get(1).name.endsWith(" updated")

        when:
            people = personRepository.findAll().toList()
            people.forEach() { it.name = it.name + " X" }
            def peopleUpdated = personRepository.updatePeople(people)
            people = personRepository.findAll().toList()

        then:
            peopleUpdated.size() == 2
            people.get(0).name.endsWith(" X")
            people.get(1).name.endsWith(" X")
            peopleUpdated.get(0).name.endsWith(" X")
            peopleUpdated.get(1).name.endsWith(" X")
    }

    void "test delete by id"() {
        given:
            savePersons(["Jeff", "James"])

        when: "an entity is retrieved"
            def person = personRepository.findByName("Jeff")

        then: "the person is not null"
            person != null
            person.name == 'Jeff'
            personRepository.findById(person.id).isPresent()

        when: "the person is deleted"
            personRepository.deleteById(person.id)

        then: "They are really deleted"
            !personRepository.findById(person.id).isPresent()
            old(personRepository.count()) - 1 == personRepository.count()
    }

    void "test delete by id and author id"() {
        given:
            setupBooks()
            def book = bookRepository.findByTitle("Pet Cemetery")
        when:
            int deleted = bookRepository.deleteByIdAndAuthorId(book.id, book.author.id)
        then:
            deleted == 1
            !bookRepository.findById(book.id).isPresent()
    }

    void "test delete by multiple ids"() {
        given:
            savePersons(["Jeff", "James"])

        when: "A search for some people"
            def people = personRepository.findByNameRegex(/^J/)

        then:
            people.size() == 2

        when: "the people are deleted"
            personRepository.deleteAll(people)

        then: "Only the correct people are deleted"
            old(personRepository.count()) - 2 == personRepository.count()
            people.every { !personRepository.findById(it.id).isPresent() }
    }

    void "test delete one"() {
        given:
            savePersons(["Bob"])

        when: "A specific person is found and deleted"
            def bob = personRepository.findByName("Bob")

        then: "The person is present"
            bob != null

        when: "The person is deleted"
            personRepository.delete(bob)

        then: "They are deleted"
            !personRepository.findById(bob.id).isPresent()
            old(personRepository.count()) - 1 == personRepository.count()
    }

    void "test update one"() {
        given:
            savePersons(["Jeff", "James"])

        when: "A person is retrieved"
            def fred = personRepository.findByName("Jeff")

        then: "The person is present"
            fred != null

        when: "The person is updated"
            personRepository.updatePerson(fred.id, "Jack")

        then: "the person is updated"
            personRepository.findByName("Jeff") == null
            personRepository.findByName("Jack") != null

        when: "an update is issued that returns a number"
            def updated = personRepository.updateByName("Jack", 20)

        then: "The result is correct"
            updated == 1
            personRepository.findByName("Jack").age == 20

        when: "A whole entity is updated"
            def jack = personRepository.findByName("Jack")
            jack.setName("Jeffrey")
            jack.setAge(30)
            personRepository.update(jack)

        then:
            personRepository.findByName("Jack") == null
            personRepository.findByName("Jeffrey").age == 30
    }

    void "test delete all"() {
        given:
            int personsWithG = personRepository.findByNameRegex("/^G/").size()

        when: "A new person is saved"
            personRepository.save("Greg", 30)
            personRepository.save("Groot", 300)

        then: "The count is "
            old(personRepository.count()) + 2 == personRepository.count()

        when: "batch delete occurs"
            def deleted = personRepository.deleteByNameRegex(/^G/)

        then: "The count is back to 1 and it entries were deleted"
            deleted == personsWithG + 2
            old(personRepository.count()) - (personsWithG + 2) == personRepository.count()

        when: "everything is deleted"
            personRepository.deleteAll()

        then: "data is gone"
            personRepository.count() == 0
    }

    void "test update method variations"() {
        when:
            def person = personRepository.save("Groot", 300)

        then:
            old(personRepository.count()) + 1 == personRepository.count()

        when:
            long result = personRepository.updatePersonCount(person.id, "Greg")

        then:
            personRepository.findByName("Greg")
            result == 1
    }

    void "test is null or empty"() {
        given:
            setupBooks()

        expect:
            bookRepository.count() == 8
            bookRepository.findByAuthorIsNull().size() == 2
            bookRepository.findByAuthorIsNotNull().size() == 6
            bookRepository.countByTitleIsEmpty() == 1
            bookRepository.countByTitleIsNotEmpty() == 7
    }

    void "test order by association"() {
        given:
            setupBooks()

        when:"Sorting by an assocation"
            def page = bookRepository.findAll(Pageable.from(Sort.of(
                    Sort.Order.desc("author.name")
            )))

        then:
            page.content
    }

    void "test project on single property"() {
        given:
            setupBooks()

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
            personRepository.findByNameRegexOrderByAge("J.*")*.age == [35,40]
            personRepository.findByNameRegexOrderByAgeDesc("J.*")*.age == [40,35]
            personRepository.findAgeByName("Jeff") == 40
            personRepository.findAgeByName("Ivan") == 30
            personRepository.findAgeByName("James") == 35
            personRepository.findMaxAgeByNameRegex("J.*") == 40
            personRepository.findMinAgeByNameRegex("J.*") == 35
            personRepository.getSumAgeByNameRegex("J.*") == 75
            personRepository.getAvgAgeByNameRegex("J.*") == 37
            personRepository.readAgeByNameRegex("J.*").sort() == [35,40]
    }

    void "test date project on single property"() {
        given:
            savePersons(["Jeff", "Jonas", "Denis", "Kevin", "Jojo"])

        expect:
            personRepository.findMaxDateOfBirthByNameRegex("J.*") == LocalDate.of(1986 , 06, 05)
            personRepository.findMinDateOfBirthByNameRegex("J.*") == LocalDate.of(1986, 06, 1)
            personRepository.findByDateOfBirthGreaterThan(LocalDate.of(1986, 06, 3))*.name == ["Kevin", "Jojo"]
            personRepository.findByDateOfBirthGreaterThanEquals(LocalDate.of(1986, 06, 3))*.name == ["Denis", "Kevin", "Jojo"]
            personRepository.findByDateOfBirthLessThan(LocalDate.of(1986, 06, 3))*.name == ["Jeff", "Jonas"]
            personRepository.findByDateOfBirthLessThanEquals(LocalDate.of(1986, 06, 3))*.name == ["Jeff", "Jonas", "Denis"]
    }

    void "test null argument handling" () {
        given:
            savePersons(["Jeff", "James"])
            setupBooks()

        when:
            personRepository.countByAgeGreaterThan(null) == 2

        then:
            def e = thrown(IllegalArgumentException)
            e.message == 'Argument [wrapper] value is null and the method parameter is not declared as nullable'

        when:
            def author = authorRepository.findByName("Stephen King")
            authorRepository.updateNickname(author.id, "SK")
            author = authorRepository.findByName("Stephen King")

        then:
            author.nickName == 'SK'

        when:
            authorRepository.updateNickname(author.id, null)
            author = authorRepository.findByName("Stephen King")

        then:
            author.nickName == null
    }

    void "test project on single ended association"() {
        given:
            setupBooks()

        expect:
            bookRepository.count() == 8
            try (def stream = bookRepository.queryTop3ByAuthorNameOrderByTitle("Stephen King").stream()) {
                assert stream.findFirst().get().title == "Pet Cemetery"
            }
            bookRepository.queryTop3ByAuthorNameOrderByTitle("Stephen King")
                    .size() == 2
            authorRepository.findByBooksTitle("The Stand").name == "Stephen King"
            authorRepository.findByBooksTitle("The Border").name == "Don Winslow"
            bookRepository.findByAuthorName("Stephen King").size() == 2
    }

    void "test join on single ended association"() {
        given:
            setupBooks()

        when:
            def book = bookRepository.findByTitle("Pet Cemetery")

        then:
            book != null
            book.title == "Pet Cemetery"
            book.author != null
            book.author.id != null
            book.author.name == "Stephen King"
    }

    void "test join on many ended association"() {
        given:
            saveSampleBooks()

        when:
            def author = authorRepository.searchByName("Stephen King")

        then:
            author != null
            author.books.size() == 2
            author.books.find { it.title == "The Stand"}
            author.books.find { it.title == "Pet Cemetery"}

        when:
            def allAuthors = CollectionUtils.iterableToList(authorRepository.findAll())

        then:
            allAuthors.size() == 3
            allAuthors.collect {it.books }.every { it.isEmpty() }
    }

    void "test one-to-many mappedBy"() {
        when:"a one-to-many is saved"
            def author = new Author()
            author.name = "author"

            def book1 = new Book()
            book1.title = "Book1"
            def page1 = new Page()
            page1.num = 1
            book1.getPages().add(page1)

            def book2 = new Book()
            book2.title = "Book2"
            def page21 = new Page()
            def page22 = new Page()
            page21.num = 21
            page22.num = 22
            book2.getPages().add(page21)
            book2.getPages().add(page22)

            def book3 = new Book()
            book3.title = "Book3"
            def page31 = new Page()
            def page32 = new Page()
            def page33 = new Page()
            page31.num = 31
            page32.num = 32
            page33.num = 33
            book3.getPages().add(page31)
            book3.getPages().add(page32)
            book3.getPages().add(page33)

            author.getBooks().add(book1)
            author.getBooks().add(book2)
            author.getBooks().add(book3)
            author = authorRepository.save(author)

        then: "They are saved correctly"
            author.id
            book1.prePersist == 1
            book1.postPersist == 1
            book2.prePersist == 1
            book2.postPersist == 1
            book3.prePersist == 1
            book3.postPersist == 1
            book3.preUpdate == 0
            book3.postUpdate == 0
            book3.preRemove == 0
            book3.postRemove == 0
            book3.postLoad == 0

        when:"retrieving an author"
            author = authorRepository.findById(author.id).orElse(null)

        then:"the associations are correct"
            author.getBooks().size() == 3
            author.getBooks()[0].postLoad == 1
            author.getBooks()[1].postLoad == 1
            author.getBooks()[2].postLoad == 1
            author.getBooks()[0].prePersist == 0
            author.getBooks()[0].postPersist == 0
            author.getBooks()[0].preUpdate == 0
            author.getBooks()[0].postUpdate == 0
            author.getBooks()[0].preRemove == 0
            author.getBooks()[0].postRemove == 0

//            def result1 = author.getBooks().find {book -> book.title == "Book1" }
//            result1.pages.size() == 1
//            result1.pages.find {page -> page.num = 1}
//
//            def result2 = author.getBooks().find {book -> book.title == "Book2" }
//            result2.pages.size() == 2
//            result2.pages.find {page -> page.num = 21}
//            result2.pages.find {page -> page.num = 22}
//
//            def result3 = author.getBooks().find {book -> book.title == "Book3" }
//            result3.pages.size() == 3
//            result3.pages.find {page -> page.num = 31}
//            result3.pages.find {page -> page.num = 32}
//            result3.pages.find {page -> page.num = 33}

        when:
            def newBook = new Book()
            newBook.title = "added"
            author.getBooks().add(newBook)
            authorRepository.update(author)

        then:
            newBook.id
            bookRepository.findById(newBook.id).isPresent()

        when:
            author = authorRepository.findById(author.id).get()

        then:
            author.getBooks().size() == 4

        when:
            authorRepository.delete(author)
        then:
            author.getBooks().size() == 4
            author.getBooks()[0].postLoad == 1
            author.getBooks()[0].prePersist == 0
            author.getBooks()[0].postPersist == 0
            author.getBooks()[0].preUpdate == 0
            author.getBooks()[0].postUpdate == 0
//     TODO: Consider whether to support cascade removes
//        author.getBooks()[0].preRemove == 1
//        author.getBooks()[0].postRemove == 1
    }

    void "test IN queries"() {
        given:
            setupBooks()
        when:
            def books1 = bookRepository.listByTitleIn(null as Collection)
        then:
            books1.size() == 0
        when:
            def books2 = bookRepository.listByTitleIn(["The Stand", "Along Came a Spider", "FFF"] as Collection)
        then:
            books2.size() == 2
        when:
            def books3 = bookRepository.listByTitleIn([] as Collection)
        then:
            books3.size() == 0
        when:
            def books4 = bookRepository.listByTitleIn(null as String[])
        then:
            books4.size() == 0
        when:
            def books5 = bookRepository.listByTitleIn(new String[] {"The Stand", "Along Came a Spider", "FFF"})
        then:
            books5.size() == 2
        when:
            def books6 = bookRepository.listByTitleIn(new String[0])
        then:
            books6.size() == 0
    }

    void "test string array data type"() {
        given:
            setupBooks()
        when:
            def books4 = bookRepository.listByTitleIn(["The Stand", "FFF"])
        then:
            books4.size() == 1
        when:
            def books5 = bookRepository.listByTitleIn(["Xyz", "FFF"])
        then:
            books5.size() == 0
        when:
            def books6 = bookRepository.listByTitleIn([])
        then:
            books6.size() == 0
        when:
            def books7 = bookRepository.listByTitleIn(null as Collection)
        then:
            books7.size() == 0
        when:
            def books8 = bookRepository.findByTitleIn(new String[] {"Xyz", "Ffff", "zzz"})
        then:
            books8.size() == 0
        when:
            def books9 = bookRepository.findByTitleIn(new String[] {})
        then:
            books9.size() == 0
        when:
            def books11 = bookRepository.findByTitleIn(null as String[])
        then:
            books11.size() == 0
        then:
            def books12 = bookRepository.findByTitleIn(new String[] {"The Stand"})
        then:
            books12.size() == 1
    }

    def "test optimistic locking"() {
        given:
            def student = new Student("Denis")
        when:
            studentRepository.save(student)
        then:
            student.version == 0
        when:
            student = studentRepository.findById(student.getId()).get()
        then:
            student.version == 0
        when:
            student.setVersion(5)
            student.setName("Xyz")
            studentRepository.update(student)
        then:
            def e = thrown(OptimisticLockException)
            e.message == "Execute update returned unexpected row count. Expected: 1 got: 0"
        when:
            studentRepository.updateByIdAndVersion(student.getId(), student.getVersion(), student.getName())
        then:
            e = thrown(OptimisticLockException)
            e.message == "Execute update returned unexpected row count. Expected: 1 got: 0"
        when:
            studentRepository.delete(student)
        then:
            e = thrown(OptimisticLockException)
            e.message == "Execute update returned unexpected row count. Expected: 1 got: 0"
        when:
            studentRepository.deleteByIdAndVersionAndName(student.getId(), student.getVersion(), student.getName())
        then:
            e = thrown(OptimisticLockException)
            e.message == "Execute update returned unexpected row count. Expected: 1 got: 0"
        when:
            studentRepository.deleteByIdAndVersion(student.getId(), student.getVersion())
        then:
            e = thrown(OptimisticLockException)
            e.message == "Execute update returned unexpected row count. Expected: 1 got: 0"
        when:
            studentRepository.deleteAll([student])
        then:
            e = thrown(OptimisticLockException)
            e.message == "Execute update returned unexpected row count. Expected: 1 got: 0"
        when:
            student = studentRepository.findById(student.getId()).get()
        then:
            student.name == "Denis"
            student.version == 0
        when:
            student.setName("Abc")
            studentRepository.update(student)
            def student2 = studentRepository.findById(student.getId()).get()
        then:
            student.version == 1
            student2.name == "Abc"
            student2.version == 1
        when:
            studentRepository.updateByIdAndVersion(student2.getId(), student2.getVersion(), "Joe")
            def student3 = studentRepository.findById(student2.getId()).get()
        then:
            student3.name == "Joe"
            student3.version == 2
        when:
            studentRepository.updateById(student2.getId(), "Joe2")
            def student4 = studentRepository.findById(student2.getId()).get()
        then:
            student4.name == "Joe2"
            student4.version == 2
        when:
            studentRepository.deleteByIdAndVersionAndName(student4.getId(), student4.getVersion(), student4.getName())
            def student5 = studentRepository.findById(student2.getId())
        then:
            !student5.isPresent()
        when:
            student = new Student("Denis2")
            studentRepository.save(student)
            studentRepository.update(student)
            studentRepository.update(student)
            studentRepository.update(student)
        then:
            student.version == 3
        when:
            student = studentRepository.findById(student.getId()).orElseThrow()
        then:
            student.version == 3
        when:
            studentRepository.delete(student)
        then:
            !studentRepository.findById(student.getId()).isPresent()
        cleanup:
            studentRepository.deleteAll()
    }

    def "test save all with empty collection"() {
        given:
        personRepository.deleteAll()

        when:
        personRepository.saveAll([])

        then:
        personRepository.count() == 0
    }

    def "test batch optimistic locking"() {
        given:
            def student1 = new Student("Denis")
            def student2 = new Student("Frank")
        when:
            studentRepository.saveAll([student1, student2])
        then:
            student1.version == 0
            student2.version == 0
        when:
            student1 = studentRepository.findById(student1.getId()).get()
            student2 = studentRepository.findById(student2.getId()).get()
        then:
            student1.version == 0
            student2.version == 0
        when:
            studentRepository.updateAll([student1, student2])
            student1 = studentRepository.findById(student1.getId()).get()
            student2 = studentRepository.findById(student2.getId()).get()
        then:
            student1.version == 1
            student2.version == 1
        when:
            studentRepository.updateAll([student1, student2])
            student1 = studentRepository.findById(student1.getId()).get()
            student2 = studentRepository.findById(student2.getId()).get()
        then:
            student1.version == 2
            student2.version == 2
        when:
            student1.setVersion(5)
            student1.setName("Xyz")
            studentRepository.updateAll([student1, student2])
        then:
            def e = thrown(OptimisticLockException)
            e.message == "Execute update returned unexpected row count. Expected: 2 got: 1"
        when:
            student1 = studentRepository.findById(student1.getId()).get()
            student2 = studentRepository.findById(student2.getId()).get()
            student1.setVersion(5)
            studentRepository.deleteAll([student1, student2])
        then:
            e = thrown(OptimisticLockException)
            e.message == "Execute update returned unexpected row count. Expected: 2 got: 1"
        cleanup:
            studentRepository.deleteAll()
    }

    void "test update relation"() {
        given:
            setupBooks()
        when:
            def book = bookRepository.findByTitle("Along Came a Spider")
            def author = authorRepository.searchByName("Stephen King")
            bookRepository.updateAuthor(book.id, author)
            book = bookRepository.findById(book.id).get()
        then:
            book.author.id == book.author.id
    }

    void "test criteria" () {
        when:
            savePersons(["Jeff", "James"])
        then:
            personRepository.findOne(dateOfBirthEquals(LocalDate.of(1986, 6, 1))).get().name == "Jeff"
            personRepository.findOne(dateOfBirthEquals(LocalDate.of(1986, 6, 2))).get().name == "James"
            personRepository.findOne(nameEquals("Jeff")).isPresent()
            !personRepository.findOne(nameEquals("Denis")).isPresent()
            personRepository.findOne(QuerySpecification.where(nameEquals("Jeff"))).isPresent()
            !personRepository.findOne(QuerySpecification.where(nameEquals("Denis"))).isPresent()
        then:
            personRepository.findAll(nameEquals("Jeff")).size() == 1
            personRepository.findAll(QuerySpecification.where(nameEquals("Jeff"))).size() == 1
            personRepository.findAll(nameEquals("Denis")).size() == 0
            personRepository.findAll(null as QuerySpecification).size() == 2
            personRepository.findAll(null as PredicateSpecification).size() == 2
            personRepository.findAll(nameEquals("Jeff").or(nameEquals("Denis"))).size() == 1
            personRepository.findAll(nameEquals("Jeff").and(nameEquals("Denis"))).size() == 0
            personRepository.findAll(nameEquals("Jeff").and(nameEquals("Jeff"))).size() == 1
            personRepository.findAll(nameEquals("Jeff").or(nameEquals("James"))).size() == 2
            personRepository.findAll(QuerySpecification.where(nameEquals("Jeff")).or(nameEquals("Denis"))).size() == 1
            personRepository.findAll(QuerySpecification.where(nameEquals("Jeff")).and(nameEquals("Denis"))).size() == 0
            personRepository.findAll(QuerySpecification.where(nameEquals("Jeff")).and(nameEquals("Jeff"))).size() == 1
            personRepository.findAll(QuerySpecification.where(nameEquals("Jeff")).or(nameEquals("James"))).size() == 2
            personRepository.findAll(QuerySpecification.where(nameEquals("Jeff")).or(nameEquals("James")), Sort.of(Sort.Order.desc("name")))[1].name == "James"
            personRepository.findAll(QuerySpecification.where(nameEquals("Jeff")).or(nameEquals("James")), Sort.of(Sort.Order.asc("name")))[1].name == "Jeff"
        when:
            def unpaged = personRepository.findAll(nameEquals("Jeff").or(nameEquals("James")), Pageable.UNPAGED)
        then:
            unpaged.content.size() == 2
            unpaged.totalSize == 2
        when:
            def unpagedSortedDesc = personRepository.findAll(nameEquals("Jeff").or(nameEquals("James")), Pageable.UNPAGED.order(Sort.Order.desc("name")))
            def unpagedSortedAsc = personRepository.findAll(nameEquals("Jeff").or(nameEquals("James")), Pageable.UNPAGED.order(Sort.Order.asc("name")))
        then:
            unpagedSortedDesc.content.size() == 2
            unpagedSortedDesc.content[1].name == "James"
            unpagedSortedAsc.content.size() == 2
            unpagedSortedAsc.content[1].name == "Jeff"
        when:
            def paged = personRepository.findAll(nameEquals("Jeff").or(nameEquals("James")), Pageable.from(0, 1))
        then:
            paged.content.size() == 1
            paged.pageNumber == 0
            paged.totalPages == 2
            paged.totalSize == 2
        when:
            def pagedSortedDesc = personRepository.findAll(nameEquals("Jeff").or(nameEquals("James")), Pageable.from(0, 1).order(Sort.Order.desc("name")))
        then:
            pagedSortedDesc.content.size() == 1
            pagedSortedDesc.content[0].name == "Jeff"
            pagedSortedDesc.pageNumber == 0
            pagedSortedDesc.totalPages == 2
            pagedSortedDesc.totalSize == 2
        when:
            def pagedSortedAsc = personRepository.findAll(QuerySpecification.where(nameEquals("Jeff")).or(nameEquals("James")), Pageable.from(0, 1).order(Sort.Order.asc("name")))
        then:
            pagedSortedAsc.content.size() == 1
            pagedSortedAsc.content[0].name == "James"
            pagedSortedAsc.pageNumber == 0
            pagedSortedAsc.totalPages == 2
            pagedSortedAsc.totalSize == 2
        when:
            def countAllByPredicateSpec = personRepository.count(nameEquals("Jeff").or(nameEquals("James")))
        then:
            countAllByPredicateSpec == 2
        when:
            def countOneByPredicateSpec = personRepository.count(nameEquals("Jeff"))
        then:
            countOneByPredicateSpec == 1
        when:
            def countAllByQuerySpec = personRepository.count(QuerySpecification.where(nameEquals("Jeff").or(nameEquals("James"))))
        then:
            countAllByQuerySpec == 2
        when:
            def countOneByQuerySpec = personRepository.count(QuerySpecification.where(nameEquals("Jeff")))
        then:
            countOneByQuerySpec == 1
        when:
            def countAppByNullByPredicateSpec = personRepository.count(null as PredicateSpecification)
            def countAppByNullByQuerySpec = personRepository.count(null as QuerySpecification)
        then:
            countAppByNullByPredicateSpec == 2
            countAppByNullByQuerySpec == 2
        when:
            def deleted = personRepository.deleteAll(nameEquals("Jeff"))
            def all = personRepository.findAll().toList()
        then:
            deleted == 1
            all.size() == 1
            all[0].name == "James"
        when:
            deleted = personRepository.deleteAll(null as DeleteSpecification)
            all = personRepository.findAll().toList()
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
            })
        then:
            updated == 1
            personRepository.count(nameEquals("Xyz")) == 1
            personRepository.count(nameEquals("Jeff")) == 0
        when:
            deleted = personRepository.deleteAll(DeleteSpecification.where(nameEquals("Xyz")))
        then:
            deleted == 1
            personRepository.count(nameEquals("Xyz")) == 0
    }

    void "test events"() {
        when:
            DomainEvents entityUnderTest = new DomainEvents(name:'test')
            eventsRepository.save(entityUnderTest)

        then:
            entityUnderTest.prePersist == 1
            entityUnderTest.postPersist == 1
            entityUnderTest.preUpdate == 0
            entityUnderTest.postUpdate == 0
            entityUnderTest.preRemove == 0
            entityUnderTest.postRemove == 0
            entityUnderTest.postLoad == 0
        when:
            DomainEvents loaded = eventsRepository.findById(entityUnderTest.id).orElse(null)

        then:
            loaded.prePersist == 0
            loaded.postPersist == 0
            loaded.preUpdate == 0
            loaded.postUpdate == 0
            loaded.preRemove == 0
            loaded.postRemove == 0
            loaded.postLoad == 1

        when:
            entityUnderTest.name = 'changed'
            eventsRepository.update(entityUnderTest)

        then:
            entityUnderTest.prePersist == 1
            entityUnderTest.postPersist == 1
            entityUnderTest.preUpdate == 1
            entityUnderTest.postUpdate == 1
            entityUnderTest.preRemove == 0
            entityUnderTest.postRemove == 0
            entityUnderTest.postLoad == 0

        when:
            entityUnderTest.name = 'changed'
            eventsRepository.delete(entityUnderTest)

        then:
            entityUnderTest.prePersist == 1
            entityUnderTest.postPersist == 1
            entityUnderTest.preUpdate == 1
            entityUnderTest.postUpdate == 1
            entityUnderTest.preRemove == 1
            entityUnderTest.postRemove == 1
            entityUnderTest.postLoad == 0
    }

    def createPeopleData() {
        List<Person> people = []
        50.times { num ->
            ('A'..'Z').each {
                people << new Person(name: it * 5 + num)
            }
        }
        personRepository.saveAll(people)
    }

    void "test sort"() {
        given:
            createPeopleData()
        when: "Sorted results are returned"
            def results = personRepository.listTop10(
                    Sort.unsorted().order("name", Sort.Order.Direction.DESC)
            )

        then: "The results are correct"
            results.size() == 10
            results[0].name.startsWith("Z")
    }

    void "test pageable list"() {
        given:
            createPeopleData()
        when: "All the people are count"
            def count = personRepository.count()

        then: "the count is correct"
            count == 1300

        when: "10 people are paged"
            def pageable = Pageable.from(0, 10)
            io.micronaut.data.model.Page<Person> page = personRepository.findAll(pageable)

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
            page = personRepository.findAll(pageable)

        then: "it is correct"
            page.offset == 10
            page.pageNumber == 1
            page.content[0].name.startsWith("K")
            page.content.size() == 10

        when: "The previous page is selected"
            pageable = page.previousPageable()
            page = personRepository.findAll(pageable)

        then: "it is correct"
            page.offset == 0
            page.pageNumber == 0
            page.content[0].name.startsWith("A")
            page.content.size() == 10
    }

    void "test pageable sort"() {
        given:
            createPeopleData()
        when: "All the people are count"
            def count = personRepository.count()

        then: "the count is correct"
            count == 1300

        when: "10 people are paged"
            io.micronaut.data.model.Page<Person> page = personRepository.findAll(
                    Pageable.from(0, 10)
                            .order("name", Sort.Order.Direction.DESC)
            )

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
            page = personRepository.findAll(page.nextPageable())

        then: "it is correct"
            page.offset == 10
            page.pageNumber == 1
            page.content[0].name.startsWith("Z")
    }

    void "test pageable findBy"() {
        given:
            createPeopleData()
        when: "People are searched for"
            def pageable = Pageable.from(0, 10)
            io.micronaut.data.model.Page<Person> page = personRepository.getByNameRegex(/A.*/, pageable)
            io.micronaut.data.model.Page<Person> page2 = personRepository.findAllByNameRegex(/A.*/, pageable)
            Slice<Person> slice = personRepository.queryByNameRegex(/A.*/, pageable)

        then: "The page is correct"
            page.offset == 0
            page.pageNumber == 0
            page.totalSize == 50
            page2.totalSize == page.totalSize
            slice.offset == 0
            slice.pageNumber == 0
            slice.size == 10
            slice.content
            page.content

        when: "The next page is retrieved"
            page = personRepository.findAllByNameRegex(/A.*/, page.nextPageable())

        then: "it is correct"
            page.offset == 10
            page.pageNumber == 1
            page.totalSize == 50
            page.nextPageable().offset == 20
            page.nextPageable().number == 2
    }

    void "test total size of find with left join"() {
        given:
            def books = bookRepository.saveAll([
                    new Book(title: "Book 1", totalPages: 100),
                    new Book(title: "Book 2", totalPages: 100)
            ])

        when:
            def page = bookRepository.findByTotalPagesGreaterThan(0, Pageable.from(0, books.size()))

        then:
            page.getContent().size() == books.size()
            page.getTotalSize() == books.size()

        cleanup:
            bookRepository.deleteAll()
    }

    void "test stream string comparison methods"() {
        if (!transactionManager.isPresent()) {
            return
        }
        given:
            setupBooks()

        when:
            List<Author> authors = transactionManager.get().executeRead(new TransactionCallback<Object, List<Author>>() {
                @Override
                List<Author> call(TransactionStatus<Object> status) throws Exception {
                    try (Stream<Author> stream = authorRepository.queryByNameRegex(/.*e.*/)) {
                        return stream.collect(Collectors.toList())
                    }
                }
            })

        then:
            authors.size() == 2

        when:
            List<Author> emptyAuthors = transactionManager.get().executeRead(new TransactionCallback<Object, List<Author>>() {
                @Override
                List<Author> call(TransactionStatus<Object> status) throws Exception {
                    try (Stream<Author> stream = authorRepository.queryByNameRegex(/.*x.*/)) {
                        return stream.collect(Collectors.toList())
                    }
                }
            })

        then:
            emptyAuthors.size() == 0
    }

    void "test find (not)equal/partial case (in)sensitive"() {
        given:
        savePersons(["John", "Michael", "Hellen"])
        when:
        def people = personRepository.findAll()
        then:
        people.size() == 3
        when:
        def optPerson = personRepository.findByNameEqualIgnoreCase("michael")
        def otherPeople = personRepository.findByNameNotEqualIgnoreCase("HELLEN")
        then:
        optPerson.present
        optPerson.get().name == "Michael"
        otherPeople.size() == 2
        otherPeople.every{ it.name != 'Hellen'}
        when:
        people = personRepository.findByNameStartsWith("Mich")
        otherPeople = personRepository.findByNameStartsWith("he")
        then:
        people.size() == 1
        people[0].name == "Michael"
        otherPeople.size() == 0
        when:
        people = personRepository.findByNameStartsWithIgnoreCase("jo")
        otherPeople = personRepository.findByNameStartsWithIgnoreCase("Heel")
        then:
        people.size() == 1
        people[0].name == "John"
        otherPeople.size() == 0
        when:
        people = personRepository.findByNameEndsWith("hael")
        otherPeople = personRepository.findByNameEndsWith("ELLEN")
        then:
        people.size() == 1
        people[0].name == "Michael"
        otherPeople.size() == 0
        when:
        people = personRepository.findByNameEndsWithIgnoreCase("ellen")
        otherPeople = personRepository.findByNameEndsWithIgnoreCase("hael1")
        then:
        people.size() == 1
        people[0].name == "Hellen"
        otherPeople.size() == 0
        when:
        people = personRepository.findByNameContains("oh")
        otherPeople = personRepository.findByNameContains("OH")
        then:
        people.size() == 1
        people[0].name == "John"
        otherPeople.size() == 0
        when:
        people = personRepository.findByNameContainsIgnoreCase("oh")
        otherPeople = personRepository.findByNameContainsIgnoreCase("OH")
        then:
        people.size() == 1
        people[0].name == "John"
        otherPeople.size() == 1
        otherPeople[0].name == "John"
    }
}
