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
import io.micronaut.core.util.CollectionUtils
import io.micronaut.data.exceptions.EmptyResultException
import io.micronaut.data.exceptions.OptimisticLockException
import io.micronaut.data.model.Pageable
import io.micronaut.data.model.Sort
import io.micronaut.data.repository.jpa.criteria.DeleteSpecification
import io.micronaut.data.repository.jpa.criteria.PredicateSpecification
import io.micronaut.data.repository.jpa.criteria.QuerySpecification
import io.micronaut.data.repository.jpa.criteria.UpdateSpecification
import io.micronaut.data.tck.entities.Author
import io.micronaut.data.tck.entities.AuthorBooksDto
import io.micronaut.data.tck.entities.BasicTypes
import io.micronaut.data.tck.entities.Book
import io.micronaut.data.tck.entities.BookDto
import io.micronaut.data.tck.entities.Car
import io.micronaut.data.tck.entities.Chapter
import io.micronaut.data.tck.entities.City
import io.micronaut.data.tck.entities.Company
import io.micronaut.data.tck.entities.Country
import io.micronaut.data.tck.entities.CountryRegion
import io.micronaut.data.tck.entities.CountryRegionCity
import io.micronaut.data.tck.entities.Face
import io.micronaut.data.tck.entities.Food
import io.micronaut.data.tck.entities.Genre
import io.micronaut.data.tck.entities.Meal
import io.micronaut.data.tck.entities.Nose
import io.micronaut.data.tck.entities.Page
import io.micronaut.data.tck.entities.Person
import io.micronaut.data.tck.entities.Student
import io.micronaut.data.tck.entities.TimezoneBasicTypes
import io.micronaut.data.tck.jdbc.entities.Role
import io.micronaut.data.tck.jdbc.entities.UserRole
import io.micronaut.data.tck.repositories.*
import io.micronaut.transaction.SynchronousTransactionManager
import io.micronaut.transaction.TransactionCallback
import io.micronaut.transaction.TransactionStatus
import jakarta.persistence.criteria.CriteriaBuilder
import jakarta.persistence.criteria.CriteriaUpdate
import jakarta.persistence.criteria.Predicate
import jakarta.persistence.criteria.Root
import spock.lang.AutoCleanup
import spock.lang.IgnoreIf
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Unroll

import java.sql.Connection
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.stream.Collectors

import static io.micronaut.data.repository.jpa.criteria.QuerySpecification.where
import static io.micronaut.data.tck.repositories.BookSpecifications.hasChapter
import static io.micronaut.data.tck.repositories.BookSpecifications.titleEquals
import static io.micronaut.data.tck.repositories.BookSpecifications.titleEqualsWithJoin
import static io.micronaut.data.tck.repositories.PersonRepository.Specifications.idsIn
import static io.micronaut.data.tck.repositories.PersonRepository.Specifications.nameEquals

abstract class AbstractRepositorySpec extends Specification {

    abstract PersonRepository getPersonRepository()
    abstract BookRepository getBookRepository()
    abstract AuthorRepository getAuthorRepository()
    abstract GenreRepository getGenreRepository()
    abstract CompanyRepository getCompanyRepository()
    abstract BookDtoRepository getBookDtoRepository()
    abstract CountryRepository getCountryRepository()
    abstract CityRepository getCityRepository()
    abstract RegionRepository getRegionRepository()
    abstract CountryRegionCityRepository getCountryRegionCityRepository()
    abstract NoseRepository getNoseRepository()
    abstract FaceRepository getFaceRepository()
    abstract UserRepository getUserRepository()
    abstract UserRoleRepository getUserRoleRepository()
    abstract RoleRepository getRoleRepository()
    abstract MealRepository getMealRepository()
    abstract FoodRepository getFoodRepository()
    abstract StudentRepository getStudentRepository()
    abstract CarRepository getCarRepository()
    abstract BasicTypesRepository getBasicTypeRepository()
    abstract TimezoneBasicTypesRepository getTimezoneBasicTypeRepository()
    abstract PageRepository getPageRepository()

    abstract Map<String, String> getProperties()

    @AutoCleanup
    @Shared
    ApplicationContext context = ApplicationContext.run(properties)

    @Shared
    Optional<SynchronousTransactionManager<Connection>> transactionManager = context.findBean(SynchronousTransactionManager)

    boolean isOracle() {
        return false
    }

    boolean isSupportsArrays() {
        return false
    }

    protected void savePersons(List<String> names) {
        personRepository.saveAll(names.collect { new Person(name: it) })
    }

    protected void setupBooks() {
        // book without an author
        bookRepository.save(new Book(title: "Anonymous", totalPages: 400))

        // Oracle interprets blank string as null and fail on non-null condition
        if (!isOracle()) {
            // blank title
            bookRepository.save(new Book(title: "", totalPages: 0))
        }

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

    protected void setup() {
        cleanup()
    }

    protected void cleanupBooks() {
        bookRepository.deleteAll()
        authorRepository.deleteAll()
    }

    protected void cleanupData() {
        bookRepository.deleteAll()
        authorRepository.deleteAll()
        personRepository.deleteAll()
    }

    protected void cleanupMeals() {
        foodRepository.deleteAll()
        mealRepository.deleteAll()
    }

    def cleanup() {
        cleanupBooks()
        cleanupData()
        cleanupMeals()
    }

    protected boolean skipQueryByDataArray() {
        return false
    }

    void "test save and retrieve basic types"() {
        when: "we save a new book"
        def book = basicTypeRepository.save(new BasicTypes())

        then: "The ID is assigned"
        book.myId != null

        when:"A book is found"
        def retrievedBook = basicTypeRepository.findById(book.myId).orElse(null)

        then:"The book is correct"
        retrievedBook.uuid == book.uuid
        retrievedBook.bigDecimal == book.bigDecimal
        retrievedBook.byteArray == book.byteArray
        retrievedBook.charSequence == book.charSequence
        retrievedBook.charset == book.charset
        retrievedBook.primitiveBoolean == book.primitiveBoolean
        retrievedBook.primitiveByte == book.primitiveByte
        retrievedBook.primitiveChar == book.primitiveChar
        retrievedBook.primitiveDouble == book.primitiveDouble
        retrievedBook.primitiveFloat == book.primitiveFloat
        retrievedBook.primitiveInteger == book.primitiveInteger
        retrievedBook.primitiveLong == book.primitiveLong
        retrievedBook.primitiveShort == book.primitiveShort
        retrievedBook.wrapperBoolean == book.wrapperBoolean
        retrievedBook.wrapperByte == book.wrapperByte
        retrievedBook.wrapperChar == book.wrapperChar
        retrievedBook.wrapperDouble == book.wrapperDouble
        retrievedBook.wrapperFloat == book.wrapperFloat
        retrievedBook.wrapperInteger == book.wrapperInteger
        retrievedBook.wrapperLong == book.wrapperLong
        retrievedBook.uri == book.uri
        retrievedBook.url == book.url
        retrievedBook.instant == book.instant
        retrievedBook.localDateTime == book.localDateTime
        retrievedBook.zonedDateTime == book.zonedDateTime
        retrievedBook.offsetDateTime == book.offsetDateTime
        retrievedBook.dateCreated == book.dateCreated
        retrievedBook.dateUpdated == book.dateUpdated

        // stored as a DATE type without time
//        retrievedBookProj.date == book.date
        when:
        def retrievedBookProj = basicTypeRepository.queryById(book.myId)

        then:"The book is correct"
        retrievedBookProj.uuid == book.uuid
        retrievedBookProj.bigDecimal == book.bigDecimal
        retrievedBookProj.byteArray == book.byteArray
        retrievedBookProj.charSequence == book.charSequence
        retrievedBookProj.charset == book.charset
        retrievedBookProj.primitiveBoolean == book.primitiveBoolean
        retrievedBookProj.primitiveByte == book.primitiveByte
        retrievedBookProj.primitiveChar == book.primitiveChar
        retrievedBookProj.primitiveDouble == book.primitiveDouble
        retrievedBookProj.primitiveFloat == book.primitiveFloat
        retrievedBookProj.primitiveInteger == book.primitiveInteger
        retrievedBookProj.primitiveLong == book.primitiveLong
        retrievedBookProj.primitiveShort == book.primitiveShort
        retrievedBookProj.wrapperBoolean == book.wrapperBoolean
        retrievedBookProj.wrapperByte == book.wrapperByte
        retrievedBookProj.wrapperChar == book.wrapperChar
        retrievedBookProj.wrapperDouble == book.wrapperDouble
        retrievedBookProj.wrapperFloat == book.wrapperFloat
        retrievedBookProj.wrapperInteger == book.wrapperInteger
        retrievedBookProj.wrapperLong == book.wrapperLong
        retrievedBookProj.uri == book.uri
        retrievedBookProj.url == book.url
        retrievedBookProj.instant == book.instant
        retrievedBookProj.localDateTime == book.localDateTime
        retrievedBookProj.zonedDateTime == book.zonedDateTime
        retrievedBookProj.offsetDateTime == book.offsetDateTime
        retrievedBookProj.dateCreated == book.dateCreated
        retrievedBookProj.dateUpdated == book.dateUpdated

        when:
        retrievedBookProj = basicTypeRepository.findAllById(book.myId).iterator().next()

        then:"The book is correct"
        retrievedBookProj.uuid == book.uuid
        retrievedBookProj.bigDecimal == book.bigDecimal
        retrievedBookProj.byteArray == book.byteArray
        retrievedBookProj.charSequence == book.charSequence
        retrievedBookProj.charset == book.charset
        retrievedBookProj.primitiveBoolean == book.primitiveBoolean
        retrievedBookProj.primitiveByte == book.primitiveByte
        retrievedBookProj.primitiveChar == book.primitiveChar
        retrievedBookProj.primitiveDouble == book.primitiveDouble
        retrievedBookProj.primitiveFloat == book.primitiveFloat
        retrievedBookProj.primitiveInteger == book.primitiveInteger
        retrievedBookProj.primitiveLong == book.primitiveLong
        retrievedBookProj.primitiveShort == book.primitiveShort
        retrievedBookProj.wrapperBoolean == book.wrapperBoolean
        retrievedBookProj.wrapperByte == book.wrapperByte
        retrievedBookProj.wrapperChar == book.wrapperChar
        retrievedBookProj.wrapperDouble == book.wrapperDouble
        retrievedBookProj.wrapperFloat == book.wrapperFloat
        retrievedBookProj.wrapperInteger == book.wrapperInteger
        retrievedBookProj.wrapperLong == book.wrapperLong
        retrievedBookProj.uri == book.uri
        retrievedBookProj.url == book.url
        retrievedBookProj.instant == book.instant
        retrievedBookProj.localDateTime == book.localDateTime
        retrievedBookProj.zonedDateTime == book.zonedDateTime
        retrievedBookProj.offsetDateTime == book.offsetDateTime
        retrievedBookProj.dateCreated == book.dateCreated
        retrievedBookProj.dateUpdated == book.dateUpdated
    }

    void "test save and retrieve timezone basic types"() {
        if (!timezoneBasicTypeRepository) {
            return
        }
        when: "we save a new book"
        def book = timezoneBasicTypeRepository.save(new TimezoneBasicTypes())

        then: "The ID is assigned"
        book.myId != null

        when:"A book is found"
        def retrievedBook = timezoneBasicTypeRepository.findById(book.myId).orElse(null)

        then:"The book is correct"
        retrievedBook.instant == book.instant
        retrievedBook.instantWithTimezone == book.instantWithTimezone
        retrievedBook.timestamp == book.timestamp
        retrievedBook.timestampWithTimezone == book.timestampWithTimezone
        retrievedBook.localDateTime == book.localDateTime
        retrievedBook.zonedDateTime == book.zonedDateTime
        retrievedBook.zonedDateTimeWithTimezone == book.zonedDateTimeWithTimezone
        retrievedBook.offsetDateTime == book.offsetDateTime
        retrievedBook.offsetDateTimeWithTimezone == book.offsetDateTimeWithTimezone
        retrievedBook.time == book.time

        when:
        def retrievedBookProj = timezoneBasicTypeRepository.queryById(book.myId)

        then:"The book is correct"
        retrievedBookProj.instant == book.instant
        retrievedBookProj.instantWithTimezone == book.instantWithTimezone
        retrievedBookProj.timestamp == book.timestamp
        retrievedBookProj.timestampWithTimezone == book.timestampWithTimezone
        retrievedBookProj.localDateTime == book.localDateTime
        retrievedBookProj.zonedDateTime == book.zonedDateTime
        retrievedBookProj.zonedDateTimeWithTimezone == book.zonedDateTimeWithTimezone
        retrievedBookProj.offsetDateTime == book.offsetDateTime
        retrievedBookProj.offsetDateTimeWithTimezone == book.offsetDateTimeWithTimezone
        retrievedBookProj.localDateTime == book.localDateTime
        retrievedBookProj.zonedDateTime == book.zonedDateTime
        retrievedBookProj.offsetDateTime == book.offsetDateTime
        retrievedBookProj.time == book.time

        when:
        retrievedBookProj = timezoneBasicTypeRepository.findAllById(book.myId).iterator().next()

        then:"The book is correct"
        retrievedBookProj.instant == book.instant
        retrievedBookProj.instantWithTimezone == book.instantWithTimezone
        retrievedBookProj.timestamp == book.timestamp
        retrievedBookProj.timestampWithTimezone == book.timestampWithTimezone
        retrievedBookProj.localDateTime == book.localDateTime
        retrievedBookProj.zonedDateTime == book.zonedDateTime
        retrievedBookProj.zonedDateTimeWithTimezone == book.zonedDateTimeWithTimezone
        retrievedBookProj.offsetDateTime == book.offsetDateTime
        retrievedBookProj.offsetDateTimeWithTimezone == book.offsetDateTimeWithTimezone
        retrievedBookProj.localDateTime == book.localDateTime
        retrievedBookProj.zonedDateTime == book.zonedDateTime
        retrievedBookProj.offsetDateTime == book.offsetDateTime
        retrievedBookProj.time == book.time
    }

    @IgnoreIf({ !jvm.isJava11Compatible() })
    void "test query by byte array"() {
        if (skipQueryByDataArray()) {
            return
        }

        when:
            def book = basicTypeRepository.save(new BasicTypes())
            def changed = "changed byte".bytes
            basicTypeRepository.update(book.myId, changed)

        then:
            basicTypeRepository.findByByteArray(changed) != null
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

    void "test total dto"() {
        given:
        savePersons(["Jeff", "James"])

        expect:
        personRepository.getTotal().total == 2

        cleanup:
        personRepository.deleteAll()
    }

    void "order by joined collection"() {
        given:
            cleanupData()
            saveSampleBooks()

        when:
            def books1 = bookRepository.listPageableCustomQuery(Pageable.from(0).order("author.name").order("title")).getContent()
            def books2 = bookRepository.findAll(Pageable.from(0).order("author.name").order("title")).getContent()

        then:
            books1.size() == 6
            books2.size() == 6
            books1[0].title == "The Border"
            books2[0].title == "The Border"

        cleanup:
            cleanupData()
    }

    protected boolean skipCustomSchemaAndCatalogTest() {
        return false
    }

    void "test CRUD with custom schema and catalog"() {
        if (skipCustomSchemaAndCatalogTest()) {
            return
        }
        when:
        def a5 = carRepository.save(new Car(name: "A5"))

        then:
        a5.id

        when:
        a5 = carRepository.findById(a5.id).orElse(null)

        then:
        a5.id
        a5.name == 'A5'
        carRepository.getById(a5.id).parts.size() == 0

        when:"an update happens"
        carRepository.update(a5.id, "A6")
        a5 = carRepository.findById(a5.id).orElse(null)

        then:"the updated worked"
        a5.name == 'A6'

        when:"an update to null happens"
        carRepository.update(a5.id, null)
        a5 = carRepository.findById(a5.id).orElse(null)

        then:"the updated to null worked"
            a5.name == null

        when:"A deleted"
        carRepository.deleteById(a5.id)

        then:"It was deleted"
        !carRepository.findById(a5.id).isPresent()
        carRepository.deleteAll()
    }

    void "test In Native Query function"() {
        given:
        savePersons(["Cemo", "Deniz", "Utku"])

        when:"using a mix of parameters with collection types with IN queries"
        def persons = personRepository.queryNames(
            ["Ali"],
            "James",
            ["Onur"],
            ["Cemo","Deniz","Olcay"],
            "Utku");

        then:"The result is correct"
        persons != null
        persons.size() == 3

        then:
        cleanupData()
    }

    void "test custom alias"() {
        given:
        saveSampleBooks()

        when:
        def book = bookRepository.queryByTitle("The Stand")

        then:
        book.title == "The Stand"
        book.author != null
        book.author.name == "Stephen King"

        cleanup:
        cleanupData()
    }

    void "test @Query with DTO"() {
        given:
        saveSampleBooks()

        when:
        def book = bookDtoRepository.findByTitleWithQuery("The Stand")

        then:
        book.isPresent()
        book.get().title == "The Stand"

        cleanup:
        cleanupData()
    }

    void "test save one"() {
        given:
        savePersons(["Jeff", "James"])

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
        given:
        savePersons(["Jeff", "James"])

        when:"many are saved"
        def p1 = personRepository.save("Frank", 0)
        def p2 = personRepository.save("Bob", 0)
        def people = [p1,p2]

        then:"all are saved"
        people.every { it.id != null }
        people.every { personRepository.findById(it.id).isPresent() }
        personRepository.findAll().size() == 4
        personRepository.count() == 4
        personRepository.count("Jeff") == 1

        personRepository.list(Pageable.from(1)).isEmpty()
        personRepository.list(Pageable.from(0, 1)).size() == 1
    }

    void "test save many custom"() {
        given:
        savePersons(["Jeff", "James"])

        when:"many are saved"
        def r1 = personRepository.saveCustom("Frank", 0)
        def r2 =  personRepository.saveCustom("Bob", 0)

        then:"all are saved"
        r1 == 1
        r2 == 1
        personRepository.findAll().size() == 4
        personRepository.count() == 4
        personRepository.count("Jeff") == 1

        personRepository.list(Pageable.from(1)).isEmpty()
        personRepository.list(Pageable.from(0, 1)).size() == 1
    }

    void "test save all with empty collection"() {
        given:
        personRepository.deleteAll()

        when:
        personRepository.saveAll([])

        then:
        personRepository.count() == 0
    }

    void "save entity using repository method with different mapped entity argument"() {
        given:
            Author author = authorRepository.save(new Author().with(true, {it.name = "Kartarka Jolanda"}))
        when:
            Book book = bookRepository.save("Hodne Budes Nekde", 42, author)
        then:
            book.author.id == author.id
    }

    void "test delete by mapped entity"() {
        given:
            saveSampleBooks()
        when:
            def author = authorRepository.findByName("Stephen King")
            int deleted = bookRepository.deleteByAuthor(author)
        then:
            deleted == 2
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

    void "test update many2"() {
        given:
        saveSampleBooks()

        when:
        def books = bookRepository.findByAuthorName("Stephen King")
        then:
        books.size() == 2

        when:
        bookRepository.updateByIdInList(books.collect { it.id }, "Modified")
        books = bookRepository.findByAuthorName("Stephen King")

        then:
        books[0].title == "Modified"
        books[1].title == "Modified"
    }

    void "test custom insert"() {
        given:
        personRepository.saveCustom([new Person(name: "Abc", age: 12), new Person(name: "Xyz", age: 22)])

        when:
        def people = personRepository.findAll().toList()

        then:
        people.size() == 2
        people.get(0).name == "Abc"
        people.get(1).name == "Xyz"
        people.get(0).age == 12
        people.get(1).age == 22
    }

    void "test custom single insert"() {
        given:
        personRepository.saveCustomSingle(new Person(name: "Abc", age: 12))

        when:
        def people = personRepository.findAll().toList()

        then:
        people.size() == 1
        people.get(0).name == "Abc"
    }

    void "test custom update"() {
        given:
        savePersons(["Dennis", "Jeff", "James", "Dennis"])

        when:
        personRepository.updateNamesCustom("Denis", "Dennis")
        def people = personRepository.findAll().toList()

        then:
        people.count { it.name == "Dennis"} == 0
        people.count { it.name == "Denis"} == 2
    }

    void "test custom update only names"() {
        when:
        savePersons(["Dennis", "Jeff", "James", "Dennis"])
        def people = personRepository.findAll().toList()
        people.forEach {it.age = 100 }
        personRepository.updateAll(people)
        people = personRepository.findAll().toList()

        then:
        people.size() == 4
        people.every{it.age > 0 }

        when:
        people.forEach() {
            it.name = it.name + " updated"
            it.age = -1
        }
        int updated = personRepository.updateCustomOnlyNames(people)
        people = personRepository.findAll().toList()

        then:
        updated == 4
        people.size() == 4
        people.every {it.name.endsWith(" updated") }
        people.every {it.age > 0 }
    }

    void "test custom delete"() {
        given:
        savePersons(["Dennis", "Jeff", "James", "Dennis"])

        when:
        def people = personRepository.findAll().toList()
        people.findAll {it.name == "Dennis"}.forEach{ it.name = "DoNotDelete"}
        def deleted = personRepository.deleteCustom(people)
        people = personRepository.findAll().toList()

        then:
        deleted == 2
        people.size() == 2
        people.count {it.name == "Dennis"}
    }

    void "test custom delete single"() {
        given:
        savePersons(["Dennis", "Jeff", "James", "Dennis"])

        when:
        def people = personRepository.findAll().toList()
        def jeff = people.find {it.name == "Jeff"}
        def deleted = personRepository.deleteCustomSingle(jeff)
        people = personRepository.findAll().toList()

        then:
        deleted == 1
        people.size() == 3

        when:
        def james = people.find {it.name == "James"}
        james.name = "DoNotDelete"
        deleted = personRepository.deleteCustomSingle(james)
        people = personRepository.findAll().toList()

        then:
        deleted == 0
        people.size() == 3
    }

    void "test custom delete single no entity"() {
        given:
        savePersons(["Dennis", "Jeff", "James", "Dennis"])

        when:
        def people = personRepository.findAll().toList()
        def jeff = people.find {it.name == "Jeff"}
        def deleted = personRepository.deleteCustomSingleNoEntity(jeff.getName())
        people = personRepository.findAll().toList()

        then:
        deleted == 1
        people.size() == 3
    }

    void "test delete by id"() {
        given:
        savePersons(["Jeff", "James"])

        when:"an entity is retrieved"
        def person = personRepository.findByName("Jeff")

        then:"the person is not null"
        person != null
        person.name == 'Jeff'
        personRepository.findById(person.id).isPresent()

        when:"the person is deleted"
        personRepository.deleteById(person.id)

        then:"They are really deleted"
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

        when:"A search for some people"
        def people = personRepository.findByNameLike("J%")

        then:
        people.size() == 2

        when:"the people are deleted"
        personRepository.deleteAll(people)

        then:"Only the correct people are deleted"
        old(personRepository.count()) - 2 == personRepository.count()
        people.every { !personRepository.findById(it.id).isPresent() }
    }

    void "test delete one"() {
        given:
        savePersons(["Bob"])

        when:"A specific person is found and deleted"
        def bob = personRepository.findByName("Bob")

        then:"The person is present"
        bob != null

        when:"The person is deleted"
        personRepository.delete(bob)

        then:"They are deleted"
        !personRepository.findById(bob.id).isPresent()
        old(personRepository.count()) - 1 == personRepository.count()
    }

    void "test update one"() {
        given:
        savePersons(["Jeff", "James"])

        when:"A person is retrieved"
        def fred = personRepository.findByName("Jeff")

        then:"The person is present"
        fred != null

        when:"The person is updated"
        personRepository.updatePerson(fred.id, "Jack")

        then:"the person is updated"
        personRepository.findByName("Jeff") == null
        personRepository.findByName("Jack") != null

        when:"an update is issued that returns a number"
        def updated = personRepository.updateByName("Jack", 20)

        then:"The result is correct"
        updated == 1
        personRepository.findByName("Jack").age == 20

        when:"A whole entity is updated"
        def jack = personRepository.findByName("Jack")
        jack.setName("Jeffrey")
        jack.setAge(30)
        personRepository.update(jack)

        then:
        personRepository.findByName("Jack") == null
        personRepository.findByName("Jeffrey").age == 30
    }

    void "test update by multiple fields"() {
        given:
        savePersons(["Jeff", "James"])
        def jeff = personRepository.findByName("Jeff")

        when: "The person is updated by name and age"
        def optPerson = personRepository.findById(jeff.id)
        optPerson.present
        def person = optPerson.get()
        person.enabled = false
        def updatedPerson = personRepository.updateByNameAndAge(person.name, person.age, person)
        then: "the person is updated and update returns updated entity"
        updatedPerson && !updatedPerson.enabled
    }

    void "test delete all"() {
        given:
        int personsWithG = personRepository.findByNameLike("G%").size()

        when:"A new person is saved"
        personRepository.save("Greg", 30)
        personRepository.save("Groot", 300)

        then:"The count is "
        old(personRepository.count()) + 2 == personRepository.count()

        when:"batch delete occurs"
        def deleted = personRepository.deleteByNameLike("G%")

        then:"The count is back to 1 and it entries were deleted"
        deleted == personsWithG + 2
        old(personRepository.count()) - (personsWithG + 2) == personRepository.count()

        when:"everything is deleted"
        personRepository.deleteAll()

        then:"data is gone"
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

        when:
        personRepository.updatePersonFuture(person.id, "Fred").get()
        personRepository.updatePersonRx(person.id, "Freddie").blockingGet()
        result = personRepository.updatePersonCustom(person.id)

        then:
        result == 1
    }

    void "test is null or empty"() {
        given:
        setupBooks()

        expect:
        // NOTE: The empty title is skipped for Oracle because it treats blank and null the same, failing the not-null condition
        bookRepository.count() == (isOracle() ? 7 : 8)
        bookRepository.findByAuthorIsNull().size() == (isOracle() ? 1 : 2)
        bookRepository.findByAuthorIsNotNull().size() == 6
        bookRepository.countByTitleIsEmpty() == (isOracle() ? 0 : 1)
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

        cleanup:
        cleanupBooks()
    }

    void "test string comparison methods"() {
        given:
        setupBooks()

        when:
        def authors = authorRepository.findByNameContains("e")
        authors.each { println it.name }

        then:
        authorRepository.countByNameContains("e") == 2
        authorRepository.findByNameStartsWith("S").name == "Stephen King"
        authorRepository.findByNameEndsWith("w").name == "Don Winslow"
        authorRepository.findByNameIgnoreCase("don winslow").name == "Don Winslow"
    }

    void "test string comparison methods ignore case"() {
        given:
        setupBooks()

        expect:
        authorRepository.findByNameContainsIgnoreCase("KIN").size() == 1
        authorRepository.findByNameStartsWithIgnoreCase("DON")
        authorRepository.findByNameEndsWithIgnoreCase("KING")
    }

    void "test stream string comparison methods"() {
        if (!transactionManager.isPresent()) {
            return
        }
        given:
        setupBooks()

        when:
        List<Author> authors = transactionManager.get().executeRead(new TransactionCallback<Connection, List<Author>>() {
            @Override
            List<Author> call(TransactionStatus<Connection> status) throws Exception {
                authorRepository.queryByNameContains("e").collect(Collectors.toList())
            }
        })

        then:
        authors.size() == 2

        when:
        List<Author> emptyAuthors = transactionManager.get().executeRead(new TransactionCallback<Connection, List<Author>>() {
            @Override
            List<Author> call(TransactionStatus<Connection> status) throws Exception {
                authorRepository.queryByNameContains("x").collect(Collectors.toList())
            }
        })

        then:
        emptyAuthors.size() == 0
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

    void "test find by age InRange"() {
        given:

        personRepository.save(new Person(name: "Jeff", age: 40))
        personRepository.saveAll([
                new Person(name: "Ivan", age: 30),
                new Person(name: "James", age: 35)
        ])

        expect:
        personRepository.findAllByAgeInRange(20, 50).size() >= 3
    }

    void "test dto projection"() {
        given:
        saveSampleBooks()

        when:
        def results = bookDtoRepository.findByTitleLike("The%")

        then:
        results.size() == 3
        results.every { it instanceof BookDto }
        results.every { it.title.startsWith("The")}
        bookDtoRepository.findOneByTitle("The Stand").title == "The Stand"

        when:"paged result check"
        def result = bookDtoRepository.searchByTitleLike("The%", Pageable.from(0))
        def all = bookDtoRepository.queryAll(Pageable.from(0))

        then:"the result is correct"
        result.totalSize == 3
        result.size == 10
        result.content.every { it instanceof BookDto }
        result.content.every { it.title.startsWith("The")}
        all.content.every { it instanceof BookDto }
        all.content.collect { it.title }.every {  it }

        if (!transactionManager.isPresent()) {
            return
        }
        when:"Stream is used"
        def dto = transactionManager.get().executeRead(new TransactionCallback<Connection, BookDto>() {
            @Override
            BookDto call(TransactionStatus<Connection> status) throws Exception {
                bookDtoRepository.findStream("The Stand").findFirst().get()
            }
        })

        then:"The result is correct"
        dto instanceof BookDto
        dto.title == "The Stand"
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
        bookRepository.count() == (isOracle() ? 7 : 8)
        bookRepository.queryTop3ByAuthorNameOrderByTitle("Stephen King")
                .stream().findFirst().get().title == "Pet Cemetery"
        bookRepository.queryTop3ByAuthorNameOrderByTitle("Stephen King")
                .size() == 2
        if (transactionManager.isPresent()) {
            transactionManager.get().executeRead(new TransactionCallback<Connection, Object>() {
                @Override
                Object call(TransactionStatus<Connection> status) throws Exception {
                    assert bookRepository.findTop3ByAuthorNameOrderByTitle("Stephen King")
                            .findFirst().get().title == "Pet Cemetery"
                    assert bookRepository.findTop3ByAuthorNameOrderByTitle("Stephen King")
                            .count() == 2
                }
            })
        }

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

    @Unroll
    void "test different join types on many ended association"(String methodName) {
        given:
            saveSampleBooks()

        when:
            def authors = authorRepository."$methodName"()

        then:
            authors.size() == 3
            authors.collect { [authorName: it.name, books: it.books.size()] }.every { it.books == 2 }

        where:
            methodName << [
                    "listAll", // DEFAULT
                    "findByIdIsNotNull", // LEFT_FETCH
                    "findByNameIsNotNull" // RIGHT_FETCH
            ]
    }

    void "stream joined"() {
        if (!transactionManager.isPresent()) {
            return
        }
        given:
            saveSampleBooks()

        when:
            def authors = transactionManager.get().executeRead(new TransactionCallback<Connection, List<Author>>() {
                @Override
                List<Author> call(TransactionStatus<Connection> status) throws Exception {
                    authorRepository.queryByIdIsNotNull().collect(Collectors.toList())
                }
            })

        then:
            authors.size() == 3
            authors.collect { [authorName: it.name, books: it.books.size()] }.every { it.books == 2 }
    }

    void "test saveAll with assigned ads"() {
        when:
        def spain = new Country("Spain")
        def france = new Country("France")
        countryRepository.saveAll(Arrays.asList(spain, france))
        def countries = countryRepository.findAll().toList()
        then:
        countries.size() == 2
        countries[0].uuid
        countries[1].uuid
        cleanup:
        countryRepository.deleteAll()
    }

    void "test query across multiple associations"() {
        when:
        def spain = new Country("Spain")
        def france = new Country("France")
        countryRepository.save(spain)
        countryRepository.save(france)
        def madrid = new CountryRegion("Madrid", spain)
        def pv = new CountryRegion("Pais Vasco", spain)
        regionRepository.save(madrid)
        regionRepository.save(pv)
        def b = new CountryRegion("Bordeaux", france)
        regionRepository.save(b)
        def bdx = new City("Bordeaux", b)
        def bilbao = new City("Bilbao", pv)
        def mad = new City("Madrid", madrid)
        cityRepository.save(bdx)
        cityRepository.save(bilbao)
        cityRepository.save(mad)
        countryRegionCityRepository.save(new CountryRegionCity(
                b,
                bdx
        ))
        countryRegionCityRepository.save(new CountryRegionCity(
                pv,
                bilbao
        ))
        countryRegionCityRepository.save(new CountryRegionCity(
                madrid,
                mad
        ))

        then:"The counts are correct"
        cityRepository.countByCountryRegionCountryName("Spain") == 2
        cityRepository.countByCountryRegionCountryName("France") == 1

        when:"A single level join is executed"
        def results = cityRepository.findByCountryRegionCountryName("Spain")

        then:"The results include the joined table"
        results.size() == 2
        results[0].name
        results[0].id
        results[0].countryRegion
        results[0].countryRegion.name
        results[0].countryRegion.country == null

        when:"A multiple level join is executed"
        results = cityRepository.getByCountryRegionCountryName("Spain")
        results.sort { it.name }

        then:"The results include the joined table"
        results.size() == 2
        results[0].name == 'Bilbao'
        results[0].id
        results[0].countryRegion
        results[0].countryRegion.name == 'Pais Vasco'
        results[0].countryRegion.country.uuid == spain.uuid
        results[0].countryRegion.country.name == "Spain"
        results[1].name == 'Madrid'
        results[1].id
        results[1].countryRegion
        results[1].countryRegion.name == 'Madrid'
        results[1].countryRegion.country.uuid == spain.uuid
        results[1].countryRegion.country.name == "Spain"

        when:"A join that uses a join table is executed"
        def region = regionRepository.findByCitiesName("Bilbao")

        then:"The result is correct"
        region.name == 'Pais Vasco'

        cleanup:
        cityRepository.deleteAll()
        regionRepository.deleteAll()
        countryRepository.deleteAll()
    }

    void "test find by name"() {

        when:
        personRepository.getByName("Fred")

        then:
        thrown(EmptyResultException)
        personRepository.findByName("Fred") == null // declares nullable
        !personRepository.findOptionalByName("Fred").isPresent()

        when:
        personRepository.save(new Person(name: "Fred"))
        personRepository.saveAll([new Person(name: "Bob"), new Person(name: "Fredrick")])
        Person p = personRepository.findByName("Bob")

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
        def company = new Company("Apple", new URL("https://apple.com"))
        def google = new Company("Google", new URL("https://google.com"))
        companyRepository.save(company)
        sleep(1000)
        companyRepository.save(google)
        def dateCreated = company.dateCreated

        GregorianCalendar calendar = getYearMonthDay(dateCreated)
        def retrieved = companyRepository.findById(company.myId).get()

        then:
        company.myId != null
        dateCreated != null
        company.lastUpdated.toEpochMilli().toString().startsWith(company.dateCreated.getTime().toString())
        getYearMonthDay(retrieved.dateCreated) == calendar

        when:
        companyRepository.update(company.myId, "Changed")
        def company2 = companyRepository.findById(company.myId).orElse(null)

        then:
        company.dateCreated.time == dateCreated.time
        retrieved.dateCreated.time == company2.dateCreated.time
        company2.name == 'Changed'
        def lastUpdated = company2.lastUpdated
        lastUpdated.toEpochMilli() > company2.dateCreated.time

        when:"batch updating with the entity"
        company2.name = "Changed Again"
        sleep(500)
        companyRepository.update(company2)

        then:
        company2.lastUpdated > lastUpdated

        when:"Sorting by date created"
        def results = companyRepository.findAll(Sort.of(
                Sort.Order.desc("name")
        )).toList()

        then:"no error occurs"
        results.size() == 2
        results.first().name == 'Google'

        when:
        company2 = companyRepository.findById(company2.myId).get()
        google = companyRepository.findById(google.myId).get()

        then:
        companyRepository.findMaxLastUpdated() == company2.lastUpdated
        companyRepository.findMinLastUpdated() == google.lastUpdated
        companyRepository.findByLastUpdatedGreaterThan(google.lastUpdated).myId == company2.myId
        companyRepository.findByLastUpdatedLessThan(company2.lastUpdated).myId == google.myId

        cleanup:
        companyRepository.deleteAll()
    }

    LocalDate toLocalDate(Date date) {
        if (date instanceof java.sql.Date) {
            return date.toLocalDate()
        }
        date.toInstant().atZone(ZoneId.systemDefault()).toLocalDate()
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

        def result1 = author.getBooks().find {book -> book.title == "Book1" }
        result1.pages.size() == 1
        result1.pages.find {page -> page.num = 1}

        def result2 = author.getBooks().find {book -> book.title == "Book2" }
        result2.pages.size() == 2
        result2.pages.find {page -> page.num = 21}
        result2.pages.find {page -> page.num = 22}

        def result3 = author.getBooks().find {book -> book.title == "Book3" }
        result3.pages.size() == 3
        result3.pages.find {page -> page.num = 31}
        result3.pages.find {page -> page.num = 32}
        result3.pages.find {page -> page.num = 33}

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

    void "test one-to-one mappedBy"() {
        when:"when a one-to-one mapped by is saved"
        def face = faceRepository.save(new Face("Bob"))
        def nose = noseRepository.save(new Nose(face: face))

        // so that we have a few records
        def anotherFace = faceRepository.save(new Face("Fred"))
        noseRepository.save(new Nose(face: anotherFace))

        then:"They are saved correctly"
        face.id
        nose.id
        nose.face.id

        when:"retrieving a face"
        face = faceRepository.findById(face.id).orElse(null)

        then:"The association is not fetched"
        face
        face.id
        face.nose == null

        when:"Querying with a join"
        face = faceRepository.queryById(face.id)

        then:"The association is fetched"
        face
        face.id
        face.name == "Bob"
        face.nose.id

        when:"querying the inverse side"
        nose = noseRepository.findById(nose.id).orElse(null)

        then:"The association is not initialized"
        nose
        nose.id
        nose.face == null

        cleanup:
        noseRepository.deleteAll()
        faceRepository.deleteAll()
    }

    void "test a composite primary key with relations"() {
        io.micronaut.data.tck.jdbc.entities.User adminUser = userRepository.save(new io.micronaut.data.tck.jdbc.entities.User("admin@gmail.com"))
        io.micronaut.data.tck.jdbc.entities.User user = userRepository.save(new io.micronaut.data.tck.jdbc.entities.User("user@gmail.com"))
        Role adminRole = roleRepository.save(new Role("ROLE_ADMIN"))
        Role role = roleRepository.save(new Role("ROLE_USER"))

        when:
        UserRole userRole = userRoleRepository.save(adminUser, adminRole)

        then:
        userRoleRepository.count() == 1
        userRole.user.id == adminUser.id
        userRole.role.id == adminRole.id

        when:
        userRoleRepository.save(adminUser, role)
        userRoleRepository.save(user, role)

        then:
        userRoleRepository.count() == 3

        when:
        List<Role> roles = userRoleRepository.findRoleByUser(adminUser).toList()

        then:
        roles.size() == 2
        roles.stream().anyMatch {r -> r.name == "ROLE_ADMIN" }
        roles.stream().anyMatch {r -> r.name == "ROLE_USER" }

        when:
        userRoleRepository.delete(user, role)

        then:
        userRoleRepository.count() == 2

        cleanup:
        userRepository.deleteAll()
        roleRepository.deleteAll()
        userRoleRepository.deleteAll()
    }

    void "test finding authors by book"() {
        given:
        setupBooks()

        when:
        def book = bookRepository.findByTitle("Pet Cemetery")
        def author = bookRepository.findAuthorById(book.id)

        then:
        author.name == "Stephen King"

        cleanup:
        cleanupBooks()
    }

    void "test finding by UUID"() {
        when:
        Meal meal = mealRepository.save(new Meal(100))

        then:
        meal != null

        expect:
        mealRepository.findById(meal.mid).get().currentBloodGlucose == 100

        cleanup:
        cleanupMeals()
    }

    boolean testLockingForUpdate() {
        return true
    }

    void "test find one for update"() {
        if (!transactionManager.isPresent() || !testLockingForUpdate()) {
            return
        }
        given:
        def meal = mealRepository.save(new Meal(10))
        def food = foodRepository.save(new Food("food", 80, 200, meal))

        when:
        Meal mealById = transactionManager.get().executeWrite(new TransactionCallback<Connection, Meal>() {
            @Override
            Meal call(TransactionStatus<Connection> status) throws Exception {
                return mealRepository.findByIdForUpdate(meal.mid)
            }
        })
        then:
        meal.currentBloodGlucose == mealById.currentBloodGlucose

        when: "finding with associations"
        Meal mealWithFood = transactionManager.get().executeWrite(new TransactionCallback<Connection, Meal>() {
            @Override
            Meal call(TransactionStatus<Connection> status) throws Exception {
                return mealRepository.searchByIdForUpdate(meal.mid)
            }
        })
        then: "the association is fetched"
        food.carbohydrates == mealWithFood.foods.first().carbohydrates

        cleanup:
        cleanupMeals()
    }

    void "test find many for update"(Closure forUpdateMethod, Object[] args) {
        if (!transactionManager.isPresent() || !testLockingForUpdate()) {
            return
        }

        given:
        def meals = mealRepository.saveAll([
                new Meal(10),
                new Meal(20),
                new Meal(30)
        ])
        foodRepository.saveAll(meals.collect { new Food("food", 10, 100, it) })

        when:
        def mealsForUpdate = transactionManager.get().executeWrite(new TransactionCallback<Connection, Object>() {
            @Override
            Object call(TransactionStatus<Connection> status) throws Exception {
                return forUpdateMethod.call(*args)
            }
        })

        then:
        mealsForUpdate.collect { it.currentBloodGlucose }.sort() ==
                normalMethod.call(*args).collect { it.currentBloodGlucose }.sort()

        cleanup:
        cleanupMeals()

        where:
        forUpdateMethod                                               | normalMethod                                         | args
        mealRepository::findAllForUpdate                              | mealRepository::findAll                              | []
        mealRepository::findAllByCurrentBloodGlucoseLessThanForUpdate | mealRepository::findAllByCurrentBloodGlucoseLessThan | [100]
        mealRepository::findByFoodsPortionGramsGreaterThanForUpdate   | mealRepository::findByFoodsPortionGramsGreaterThan   | [10]
    }

    void "test find for update locking"() {
        if (!transactionManager.isPresent() || !testLockingForUpdate()) {
            return
        }

        given:
        def meal = mealRepository.save(new Meal(10))
        def threadCount = 2

        when:
        def latch = new CountDownLatch(threadCount)
        (1..threadCount).collect {
            Thread.start {
                transactionManager.get().executeWrite(new TransactionCallback<Connection, Object>() {
                    @Override
                    Object call(TransactionStatus<Connection> status) throws Exception {
                        def mealToUpdate = mealRepository.findByIdForUpdate(meal.mid)
                        latch.countDown()
                        latch.await(5, TimeUnit.SECONDS)
                        mealToUpdate.currentBloodGlucose++
                        mealRepository.update(mealToUpdate)
                    }
                })
            }
        }.forEach { it.join() }

        then:
        mealRepository.findById(meal.mid).get().currentBloodGlucose == meal.currentBloodGlucose + threadCount

        cleanup:
        cleanupMeals()
    }

    void "test find for update locking with associations"() {
        if (!transactionManager.isPresent() || !testLockingForUpdate()) {
            return
        }
        given:
        def meal = mealRepository.save(new Meal(10))
        foodRepository.save(new Food("food", 80, 200, meal))
        def threadCount = 2

        when:
        def latch = new CountDownLatch(threadCount)
        (1..threadCount).collect {
            Thread.start {
                transactionManager.get().executeWrite(new TransactionCallback<Connection, Object>() {
                    @Override
                    Object call(TransactionStatus<Connection> status) throws Exception {
                        def food = foodRepository.findByMealMidForUpdate(meal.mid)
                        def mealToUpdate = food.meal
                        latch.countDown()
                        latch.await(5, TimeUnit.SECONDS)
                        mealToUpdate.currentBloodGlucose++
                        mealRepository.update(mealToUpdate)
                    }
                })
            }
        }.forEach { it.join() }

        then:
        mealRepository.findById(meal.mid).get().currentBloodGlucose == meal.currentBloodGlucose + threadCount

        cleanup:
        cleanupMeals()
    }

    void "test IN queries"() {
        given:
            setupBooks()
        when:
            def books1 = bookRepository.listNativeBooksWithTitleInCollection(null)
        then:
            books1.size() == 0
        when:
            def books2 = bookRepository.listNativeBooksWithTitleInCollection(["The Stand", "Along Came a Spider", "FFF"])
        then:
            books2.size() == 2
        when:
            def books3 = bookRepository.listNativeBooksWithTitleInCollection([])
        then:
            books3.size() == 0
        when:
            def books4 = bookRepository.listNativeBooksWithTitleInArray(null)
        then:
            books4.size() == 0
        when:
            def books5 = bookRepository.listNativeBooksWithTitleInArray(new String[] {"The Stand", "Along Came a Spider", "FFF"})
        then:
            books5.size() == 2
        when:
            def books6 = bookRepository.listNativeBooksWithTitleInArray(new String[0])
        then:
            books6.size() == 0
        cleanup:
            cleanupBooks()
    }

    void "test string array data type"() {
        if (!isSupportsArrays()) {
            return
        }
        given:
            setupBooks()
        when:
            def books4 = bookRepository.listNativeBooksNullableListAsStringArray(["The Stand", "FFF"])
        then:
            books4.size() == 1
        when:
            def books5 = bookRepository.listNativeBooksNullableListAsStringArray(["Xyz", "FFF"])
        then:
            books5.size() == 0
        when:
            def books6 = bookRepository.listNativeBooksNullableListAsStringArray([])
        then:
            books6.size() == 0
        when:
            def books7 = bookRepository.listNativeBooksNullableListAsStringArray(null)
        then:
            books7.size() == 0
        when:
            def books8 = bookRepository.listNativeBooksNullableArrayAsStringArray(new String[] {"Xyz", "Ffff", "zzz"})
        then:
            books8.size() == 0
        when:
            def books9 = bookRepository.listNativeBooksNullableArrayAsStringArray(new String[] {})
        then:
            books9.size() == 0
        when:
            def books11 = bookRepository.listNativeBooksNullableArrayAsStringArray(null)
        then:
            books11.size() == 0
        then:
            def books12 = bookRepository.listNativeBooksNullableArrayAsStringArray(new String[] {"The Stand"})
        then:
            books12.size() == 1
        cleanup:
            cleanupBooks()
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

    void "test update relation custom query"() {
        given:
            setupBooks()
        when:
            def book = bookRepository.findAllByTitleStartingWith("Along Came a Spider").first()
            def author = authorRepository.searchByName("Stephen King")
            bookRepository.updateAuthorCustom(book.id, author)
            book = bookRepository.findById(book.id).get()
        then:
            book.author.id == book.author.id
    }

    void "test update relation"() {
        given:
            setupBooks()
        when:
            def book = bookRepository.findAllByTitleStartingWith("Along Came a Spider").first()
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
            personRepository.findOne(nameEquals("Jeff")).isPresent()
            !personRepository.findOne(nameEquals("Denis")).isPresent()
            personRepository.findOne(where(nameEquals("Jeff"))).isPresent()
            !personRepository.findOne(where(nameEquals("Denis"))).isPresent()
        then:
            personRepository.findAll(nameEquals("Jeff")).size() == 1
            personRepository.findAll(where(nameEquals("Jeff"))).size() == 1
            personRepository.findAll(nameEquals("Denis")).size() == 0
            personRepository.findAll(null as QuerySpecification).size() == 2
            personRepository.findAll(null as PredicateSpecification).size() == 2
            personRepository.findAll(null as QuerySpecification, Pageable.from(Sort.of(Sort.Order.desc("name")))).size() == 2
            personRepository.findAll(null as PredicateSpecification, Pageable.from(Sort.of(Sort.Order.desc("name")))).size() == 2
            personRepository.findAll(nameEquals("Jeff").or(nameEquals("Denis"))).size() == 1
            personRepository.findAll(nameEquals("Jeff").and(nameEquals("Denis"))).size() == 0
            personRepository.findAll(nameEquals("Jeff").and(nameEquals("Jeff"))).size() == 1
            personRepository.findAll(nameEquals("Jeff").or(nameEquals("James"))).size() == 2
            personRepository.findAll(where(nameEquals("Jeff")).or(nameEquals("Denis"))).size() == 1
            personRepository.findAll(where(nameEquals("Jeff")).and(nameEquals("Denis"))).size() == 0
            personRepository.findAll(where(nameEquals("Jeff")).and(nameEquals("Jeff"))).size() == 1
            personRepository.findAll(where(nameEquals("Jeff")).or(nameEquals("James"))).size() == 2
            personRepository.findAll(where(nameEquals("Jeff")).or(nameEquals("James")), Sort.of(Sort.Order.desc("name")))[1].name == "James"
            personRepository.findAll(where(nameEquals("Jeff")).or(nameEquals("James")), Sort.of(Sort.Order.asc("name")))[1].name == "Jeff"
        when:
            def pred1 = nameEquals("Jeff").or(nameEquals("Denis"))
            def pred2 = pred1.or(nameEquals("Abc"))
            def andPred = nameEquals("Jeff").and(pred2)
        then:
            personRepository.findAll(andPred).size() == 1
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
            def pagedSortedAsc = personRepository.findAll(where(nameEquals("Jeff")).or(nameEquals("James")), Pageable.from(0, 1).order(Sort.Order.asc("name")))
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
            def countAllByQuerySpec = personRepository.count(where(nameEquals("Jeff").or(nameEquals("James"))))
        then:
            countAllByQuerySpec == 2
        when:
            def countOneByQuerySpec = personRepository.count(where(nameEquals("Jeff")))
        then:
            countOneByQuerySpec == 1
        when:
            def countAppByNullByPredicateSpec = personRepository.count(null as PredicateSpecification)
            def countAppByNullByQuerySpec = personRepository.count(null as QuerySpecification)
        then:
            countAppByNullByPredicateSpec == 2
            countAppByNullByQuerySpec == 2
        when:
            def ids = personRepository.findAll().collect { p -> p.getId() }
            long count = ids.size()
            def countByIds = count == 0 ? 0 : personRepository.count(idsIn(ids.toArray(new Long[0])))
        then:
            countByIds == count
        when:
            def jeffPerson = personRepository.findOne(nameEquals("Jeff")).get();
            def foundByIdPerson = personRepository.findOne(idsIn(jeffPerson.getId())).get()
        then:
            jeffPerson.getId() == foundByIdPerson.getId() && jeffPerson.getName() == foundByIdPerson.getName()
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

        when:
            def meal = mealRepository.save(new Meal(10))
            def food = new Food("food", 80, 200, meal)
            food.setLongName("long name")
            food = foodRepository.save(food)
        then:
            // Verify order by works on alias mapped property
            def foods = foodRepository.findAllByKeyOrderByLongName(food.key);
            foods.size() == 1
            foods[0].key == food.key
            foods[0].longName == food.longName

            def loadedFood = foodRepository.findOne(FoodRepository.Specifications.keyEquals(food.key)).get()
            loadedFood.key == food.key
            loadedFood.longName == food.longName
        when:
            savePersons(["Jeff"])
            def existsPredicateSpec = personRepository.exists(nameEquals("Jeff"))
            def existsNotPredicateSpec = personRepository.exists(nameEquals("NotJeff"))
            def existsQuerySpec = personRepository.exists(where(nameEquals("Jeff")))
        def existsNotQuerySpec = personRepository.exists(where(nameEquals("NotJeff")))
        then:
            existsPredicateSpec
            !existsNotPredicateSpec
            existsQuerySpec
            !existsNotQuerySpec

    }

    void "test join/fetch"() {
        given:
        def genre = new Genre()
        genre.setGenreName("Dystopia")
        genreRepository.save(genre)

        def book = new Book()
        book.setTitle("1984")
        book.setGenre(genre)
        def ch1 = new Chapter()
        ch1.setTitle("Ch1")
        ch1.setPages(10)
        book.getChapters().add(ch1)
        def ch2 = new Chapter()
        ch2.setTitle("Ch2")
        ch2.setPages(5)
        book.getChapters().add(ch2)
        bookRepository.save(book)

        when:
        def bookLoadedUsingFindAllByGenre = bookRepository.findAllByGenre(genre).get(0)
        def bookLoadedUsingFindOneWithCriteriaApi = bookRepository.findOne(titleEquals(book.title)).get()
        def bookNotFoundUsingFindOneWithCriteriaApi = bookRepository.findOne(titleEquals("non_existing_book_" + System.currentTimeMillis()))
        def bookLoadedUsingFindAllWithCriteriaApi = bookRepository.findAll(titleEquals(book.title)).get(0)
        def bookLoadedUsingFindAllByCriteriaWithoutAnnotationJoin = bookRepository.findAllByCriteria(titleEqualsWithJoin(book.title)).get(0)
        def bookLoadedUsingFindAllWithCriteriaApiAndJoins = bookRepository.findAll(titleEqualsWithJoin(book.title)).get(0)
        def bookLoadedUsingJoinCriteriaByChapterTitle = bookRepository.findOne(hasChapter("Ch1"))
        def bookNotLoadedUsingJoinCriteriaByChapterTitle = bookRepository.findOne(hasChapter("Ch32"))
        def booksLoadedByChapterTitleQuery = bookRepository.findAllByChaptersTitle("Ch1")
        def booksLoadedByChapterTitleAndBookTitleQuery = bookRepository.findAllByChaptersTitleAndTitle("Ch1", book.title)

        then:
        bookLoadedUsingFindAllByGenre.genre.genreName != null
        bookLoadedUsingFindOneWithCriteriaApi != null
        bookLoadedUsingFindOneWithCriteriaApi.genre.genreName == genre.genreName
        bookNotFoundUsingFindOneWithCriteriaApi.present == false
        bookLoadedUsingFindAllWithCriteriaApi != null
        bookLoadedUsingFindAllWithCriteriaApi.genre.genreName == genre.genreName
        bookLoadedUsingFindAllByCriteriaWithoutAnnotationJoin != null
        bookLoadedUsingFindAllByCriteriaWithoutAnnotationJoin.genre.genreName != null
        bookLoadedUsingFindAllWithCriteriaApiAndJoins != null
        bookLoadedUsingFindAllWithCriteriaApiAndJoins.genre.genreName != null
        bookLoadedUsingJoinCriteriaByChapterTitle.present
        bookLoadedUsingJoinCriteriaByChapterTitle.get().id == book.id
        !bookNotLoadedUsingJoinCriteriaByChapterTitle.present
        booksLoadedByChapterTitleQuery.size() > 0
        booksLoadedByChapterTitleQuery[0].id == book.id
        // Chapters not loaded
        CollectionUtils.isEmpty(booksLoadedByChapterTitleQuery[0].chapters)
        // Loaded book and also expected chapters to be loaded
        booksLoadedByChapterTitleAndBookTitleQuery.size() > 0
        booksLoadedByChapterTitleAndBookTitleQuery[0].id == book.id
        // Chapters not loaded
        !CollectionUtils.isEmpty(booksLoadedByChapterTitleAndBookTitleQuery[0].chapters)
    }

    void "test loading books vs page repository and joins"() {
        given:

        def book1 = new Book()
        book1.title = "Book1"
        def page1 = new Page()
        page1.num = 1
        def page2 = new Page()
        page2.num = 21
        book1.getPages().add(page1)
        book1.getPages().add(page2)
        def chapter1 = new Chapter()
        chapter1.title = "Ch1"
        chapter1.pages = 20
        book1.getChapters().add(chapter1)
        def chapter2 = new Chapter()
        chapter2.title = "Ch2"
        chapter2.pages = 10
        book1.getChapters().add(chapter2)
        bookRepository.save(book1)

        def book2 = new Book()
        book2.title = "Book2"
        def page3 = new Page()
        page3.num = 3
        book2.getPages().add(page3)
        def chapter3 = new Chapter()
        chapter3.title = "ChBook2_1"
        chapter3.pages = 15
        book2.getChapters().add(chapter3)
        bookRepository.save(book2)

        when:
        def loadedBookViaPage1 = pageRepository.findBookById(page1.id)
        def loadedBookViaPage3 = pageRepository.findBookById(page3.id)
        then:
        loadedBookViaPage1.present == true
        loadedBookViaPage3.present == true
        // chapters are loaded
        loadedBookViaPage1.get().chapters.size() == 2
        loadedBookViaPage3.get().chapters.size() == 1

        when: "Loaded chapters without book joined"
        def loadedChaptersViaPage1 = pageRepository.findBookChaptersById(page1.id)
        def loadedChaptersViaPage3 = pageRepository.findBookChaptersById(page3.id)
        then:
        loadedChaptersViaPage1.size() == 2
        loadedChaptersViaPage1[0].book.id == page1.book.id
        loadedChaptersViaPage1[1].book.id == page1.book.id
        // book not joined, only book with id loaded
        loadedChaptersViaPage1[0].book.title == null
        loadedChaptersViaPage1[1].book.title == null
        loadedChaptersViaPage3.size() == 1
        loadedChaptersViaPage3[0].book.id == page3.book.id
        loadedChaptersViaPage3[0].book.title == null

        when: "Loaded chapters with book joined"
        def loadedChaptersViaPage1BookJoined = pageRepository.findBookChaptersByIdAndNum(page1.id, page1.num)
        def loadedChaptersViaPage3BookJoined = pageRepository.findBookChaptersByIdAndNum(page3.id, page3.num)
        then:
        loadedChaptersViaPage1BookJoined.size() == 2
        loadedChaptersViaPage1BookJoined[0].book.id == page1.book.id
        loadedChaptersViaPage1BookJoined[1].book.id == page1.book.id
        // book IS joined and fully loaded including title
        loadedChaptersViaPage1BookJoined[0].book.title == page1.book.title
        loadedChaptersViaPage1BookJoined[1].book.title == page1.book.title
        loadedChaptersViaPage3BookJoined.size() == 1
        loadedChaptersViaPage3BookJoined[0].book.id == page3.book.id
        loadedChaptersViaPage3BookJoined[0].book.title == page3.book.title

        cleanup:
        cleanupBooks()
    }

    void "test finding by And and Or combined"() {
        given:
        setupBooks()

        when:
        def book = bookRepository.findByTitle("Pet Cemetery")
        def author = bookRepository.findAuthorById(book.id)
        def verificationBook = bookRepository.findByTitleOrAuthorAndId(book.title, author, book.id)

        then:
        author.name == "Stephen King"
        verificationBook != null
        verificationBook.id == book.id
        verificationBook.title == book.title
        verificationBook.author.id == book.author.id

        cleanup:
        cleanupBooks()
    }

    void "test ManyToMany join table with mappedBy"() {
        given:
        def student = new Student("Peter")
        def book1 = new Book()
        book1.title = "Book1"
        book1.getStudents().add(student)
        def book2 = new Book()
        book2.title = "Book2"
        book2.getStudents().add(student)
        bookRepository.save(book1)
        bookRepository.save(book2)
        when:
        def loadedStudent = studentRepository.findByName(student.name).get()
        def loadedBook1 = bookRepository.findById(book1.id).get()
        def loadedBook2 = bookRepository.findById(book2.id).get()
        then:
        loadedStudent
        loadedStudent.id == student.id
        loadedStudent.books.size() == 2
        loadedStudent.name == student.name
        loadedBook1
        loadedBook1.title == book1.title
        loadedBook1.id == book1.id
        loadedBook2
        loadedBook2.title == book2.title
        loadedBook2.id == book2.id
        cleanup:
        studentRepository.delete(student)
        bookRepository.delete(book1)
        bookRepository.delete(book2)
    }

    void "test @Where and joins"() {
        given:
        def meal = mealRepository.save(new Meal(10))
        def food = foodRepository.save(new Food("food", 80, 200, meal))
        def food1 = foodRepository.save(new Food("food1", 50, 150, meal))
        when:
        def loadedMeal = mealRepository.searchById(meal.mid)
        def optFood = foodRepository.findById(food.fid)
        def optFood1 = foodRepository.findById(food1.fid)
        then:
        loadedMeal
        loadedMeal.foods.size() == 2
        optFood.present
        optFood.get().meal.mid == loadedMeal.mid
        optFood1.present
        optFood1.get().meal.mid == loadedMeal.mid
        when:
        food.fresh = 'N'
        foodRepository.update(food)
        loadedMeal = mealRepository.searchById(meal.mid)
        optFood = foodRepository.findById(food.fid)
        optFood1 = foodRepository.findById(food1.fid)
        then:
        loadedMeal
        loadedMeal.foods.size() == 1
        !optFood.present
        optFood1.present
        when:
        meal.actual = 'N'
        mealRepository.update(meal)
        loadedMeal = mealRepository.searchById(meal.mid)
        optFood1 = foodRepository.findById(food1.fid)
        then:
        !loadedMeal
        !optFood1.present
        cleanup:
        foodRepository.deleteById(food.fid)
        foodRepository.deleteById(food1.fid)
        mealRepository.deleteById(meal.mid)
    }

    void "test author page total size"() {
        given:
        def author = new Author()
        author.name = "author"
        authorRepository.save(author)
        def book = new Book()
        book.title = "book"
        book.author = author
        def book2 = new Book()
        book2.title = "book2"
        book2.author = author
        bookRepository.save(book)
        bookRepository.save(book2)
        when:
        def authorPage = authorRepository.findAll(Pageable.UNPAGED)
        def bookPage = bookRepository.findAll(Pageable.from(0, 10, Sort.of(Sort.Order.asc("title"))))
        then:
        authorPage.totalSize == 1
        authorPage.content.size() == 1
        authorPage.content[0].books.size() == 2
        bookPage.totalSize == 2
    }

    private GregorianCalendar getYearMonthDay(Date dateCreated) {
        def cal = dateCreated.toCalendar()
        def localDate = LocalDate.of(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH))
        GregorianCalendar calendar = new GregorianCalendar(localDate.year, localDate.month.value, localDate.dayOfMonth)
        calendar
    }
}
