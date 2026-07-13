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
package io.micronaut.data.jdbc.oraclexe

import groovy.transform.Memoized
import io.micronaut.data.tck.entities.Address
import io.micronaut.data.tck.entities.AssignedIdReturningEntity
import io.micronaut.data.tck.entities.Restaurant
import io.micronaut.data.tck.entities.Book
import io.micronaut.data.tck.entities.BookDto
import io.micronaut.data.tck.entities.Face
import io.micronaut.data.tck.jdbc.entities.IntervalEntity
import io.micronaut.data.tck.repositories.*
import io.micronaut.data.tck.tests.AbstractRepositorySpec

import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

import java.time.Duration
import java.time.Period

class OracleXERepositorySpec extends AbstractRepositorySpec implements OracleTestPropertyProvider {

    @Override
    boolean isOracle() {
        return true
    }

    @Memoized
    @Override
    PersonRepository getPersonRepository() {
        return context.getBean(OracleXEPersonRepository)
    }

    @Memoized
    @Override
    OracleXEBookRepository getBookRepository() {
        return context.getBean(OracleXEBookRepository)
    }

    @Memoized
    OracleRestaurantRepository getRestaurantRepository() {
        return context.getBean(OracleRestaurantRepository)
    }

    @Memoized
    @Override
    GenreRepository getGenreRepository() {
        return context.getBean(OracleXEGenreRepository)
    }

    @Memoized
    @Override
    AuthorRepository getAuthorRepository() {
        return context.getBean(OracleXEAuthorRepository)
    }

    @Memoized
    @Override
    CompanyRepository getCompanyRepository() {
        return context.getBean(OracleXECompanyRepository)
    }

    @Memoized
    @Override
    BookDtoRepository getBookDtoRepository() {
        return context.getBean(OracleXEBookDtoRepository)
    }

    @Memoized
    @Override
    CountryRepository getCountryRepository() {
        return context.getBean(OracleXECountryRepository)
    }

    @Memoized
    @Override
    CityRepository getCityRepository() {
        return context.getBean(OracleXECityRepository)
    }

    @Memoized
    @Override
    RegionRepository getRegionRepository() {
        return context.getBean(OracleXERegionRepository)
    }

    @Memoized
    @Override
    NoseRepository getNoseRepository() {
        return context.getBean(OracleXENoseRepository)
    }

    @Memoized
    @Override
    FaceRepository getFaceRepository() {
        return context.getBean(OracleXEFaceRepository)
    }

    @Memoized
    @Override
    CountryRegionCityRepository getCountryRegionCityRepository() {
        return context.getBean(OracleXECountryRegionCityRepository)
    }

    @Memoized
    @Override
    UserRoleRepository getUserRoleRepository() {
        return context.getBean(OracleXEUserRoleRepository)
    }

    @Memoized
    @Override
    RoleRepository getRoleRepository() {
        return context.getBean(OracleXERoleRepository)
    }

    @Memoized
    @Override
    UserRepository getUserRepository() {
        return context.getBean(OracleXEUserRepository)
    }

    @Memoized
    @Override
    MealRepository getMealRepository() {
        return context.getBean(OracleXEMealRepository)
    }

    @Memoized
    @Override
    FoodRepository getFoodRepository() {
        return context.getBean(OracleXEFoodRepository)
    }

    @Memoized
    @Override
    StudentRepository getStudentRepository() {
        return context.getBean(OracleXEStudentRepository)
    }

    @Memoized
    @Override
    CarRepository getCarRepository() {
        return context.getBean(OracleXECarRepository)
    }

    @Memoized
    @Override
    BasicTypesRepository getBasicTypeRepository() {
        return context.getBean(OracleXEBasicTypesRepository)
    }

    @Memoized
    @Override
    TimezoneBasicTypesRepository getTimezoneBasicTypeRepository() {
        return context.getBean(OracleXETimezoneBasicTypesRepository)
    }

    @Memoized
    @Override
    PageRepository getPageRepository() {
        return context.getBean(OracleXEPageRepository)
    }

    @Memoized
    @Override
    EntityWithIdClassRepository getEntityWithIdClassRepository() {
        return context.getBean(OracleXEEntityWithIdClassRepository)
    }

    @Memoized
    @Override
    EntityWithIdClass2Repository getEntityWithIdClass2Repository() {
        return context.getBean(OracleXEEntityWithIdClass2Repository)
    }

    @Memoized
    @Override
    ExampleEntityRepository getExampleEntityRepository() {
        return context.getBean(OracleExampleEntityRepository)
    }

    @Memoized
    @Override
    OracleXEIntervalRepository getIntervalRepository() {
        return context.getBean(OracleXEIntervalRepository)
    }

    @Override
    protected boolean skipCustomSchemaAndCatalogTest() {
        // ORA-04043: object "FORD"."CARS" does not exist
        return true
    }

    @Override
    protected boolean skipQueryByDataArray() {
        // ORA-00932: inconsistent datatypes: expected - got BLOB
        return true
    }

    void "test procedure"() {
        expect:
            bookRepository.add1(123) == 124
            bookRepository.add1Aliased(123) == 124
    }

    void "test ANY queries"() {
        given:
            saveSampleBooks()
        when:
            def books1 = bookRepository.listNativeBooksWithTitleAnyCollection(null)
        then:
            books1.size() == 0
        when:
            def books2 = bookRepository.listNativeBooksWithTitleAnyCollection(["The Stand", "Along Came a Spider", "FFF"])
        then:
            books2.size() == 2
        when:
            def books3 = bookRepository.listNativeBooksWithTitleAnyCollection([])
        then:
            books3.size() == 0
        when:
            def books4 = bookRepository.listNativeBooksWithTitleAnyArray(null)
        then:
            books4.size() == 0
        when:
            def books5 = bookRepository.listNativeBooksWithTitleAnyArray(new String[]{"The Stand", "Along Came a Spider", "FFF"})
        then:
            books5.size() == 2
        when:
            def books6 = bookRepository.listNativeBooksWithTitleAnyArray(new String[0])
        then:
            books6.size() == 0
        cleanup:
            cleanupBooks()
    }

    void "test update returning book"() {
        given:
            setupBooks()
        when:
            def book = bookRepository.findByTitle("Pet Cemetery")
            book.title = "Xyz"
            Book newBook = bookRepository.updateReturning(book)
            book.title = "old"
        then:
            newBook.title == "Xyz"
    }

    void "test update returning book title"() {
        given:
            setupBooks()
        when:
            def book = bookRepository.findByTitle("Pet Cemetery")
            book.title = "Xyz"
            String newTitle = bookRepository.updateReturningTitle(book)
        then:
            newTitle == "Xyz"
            bookRepository.findById(book.id).get().title == "Xyz"
    }

    void "test update returning book title 2"() {
        given:
            setupBooks()
            def book = bookRepository.findByTitle("Pet Cemetery")
        when:
            String newTitle = bookRepository.updateReturningTitle(book.id, "Xyz")
        then:
            newTitle == "Xyz"
            bookRepository.findById(book.id).get().title == "Xyz"
    }

    void "test update returning book title 3"() {
        given:
            setupBooks()
            def book = bookRepository.findByTitle("Pet Cemetery")
        when:
            String newTitle = bookRepository.updateByIdReturningTitle(book.id, "Xyz")
        then:
            newTitle == "Xyz"
            bookRepository.findById(book.id).get().title == "Xyz"
    }

    void "test native query with colon"() {
        given:
        def face = faceRepository.save(new Face("New"))
        def oracleFaceRepository = (OracleXEFaceRepository) faceRepository
        when:
        def faces = oracleFaceRepository.findAllWithOptionalFilters(null, "2024-01-01")
        then:
        faces
        faces[0].name == face.name
        when:"Call repository void method"
        oracleFaceRepository.lock()
        then:"No error thrown"
        noExceptionThrown()
        cleanup:
        faceRepository.delete(face)
    }

    void "test comparison operations on interval properties"() {
        given:
        def entity1 = new IntervalEntity()
        entity1.setDuration(Duration.ofHours(4))
        entity1.setPeriod(Period.ofYears(7))

        def entity2 = new IntervalEntity()
        entity2.setDuration(Duration.ofHours(4).plusMinutes(15))
        entity2.setPeriod(Period.ofYears(7).plusMonths(4))

        def entity3 = new IntervalEntity()
        entity3.setDuration(Duration.ofHours(4).plusMinutes(30))
        entity3.setPeriod(Period.ofYears(7).minusMonths(4))

        def entity4 = new IntervalEntity()
        entity4.setDuration(Duration.ofHours(4).plusMinutes(45))
        entity4.setPeriod(Period.ofYears(7).plusMonths(2))

        def entity5 = new IntervalEntity()
        entity5.setDuration(Duration.ofHours(5))
        entity5.setPeriod(Period.ofYears(8))

        def savedEntities = intervalRepository.saveAll([entity1, entity2, entity3, entity4, entity5])

        when:
        def foundEntities = intervalRepository.findByDurationBetweenOrderById(
                Duration.ofHours(4).plusMinutes(10),
                Duration.ofHours(4).plusMinutes(50))

        then:
        foundEntities != null
        foundEntities.size() == 3
        foundEntities.get(0).id == savedEntities.get(1).id
        foundEntities.get(1).id == savedEntities.get(2).id
        foundEntities.get(2).id == savedEntities.get(3).id

        when:
        foundEntities = intervalRepository.findByDurationGreaterThanEqualsOrderById(
                Duration.ofHours(4).plusMinutes(45))

        then:
        foundEntities != null
        foundEntities.size() == 2
        foundEntities.get(0).id == savedEntities.get(3).id
        foundEntities.get(1).id == savedEntities.get(4).id

        when:
        Integer count = intervalRepository.countByDurationLessThan(Duration.ofHours(5))

        then:
        count == 4

        when:
        foundEntities = intervalRepository.findByPeriodBetweenOrderById(
                Period.ofYears(7).minusMonths(2),
                Period.ofYears(7).plusMonths(5))

        then:
        foundEntities != null
        foundEntities.size() == 3
        foundEntities.get(0).id == savedEntities.get(0).id
        foundEntities.get(1).id == savedEntities.get(1).id
        foundEntities.get(2).id == savedEntities.get(3).id

        when:
        foundEntities = intervalRepository.findByPeriodGreaterThanOrderById(
                Period.ofYears(7))

        then:
        foundEntities != null
        foundEntities.size() == 3
        foundEntities.get(0).id == savedEntities.get(1).id
        foundEntities.get(1).id == savedEntities.get(3).id
        foundEntities.get(2).id == savedEntities.get(4).id

        when:
        count = intervalRepository.countByPeriodLessThanEquals(Period.ofYears(7))

        then:
        count == 2
    }

    void "test insert returning book"() {
        given:
        setupBooks()
        def existing = bookRepository.findByTitle("Pet Cemetery")
        def bookToCreate = new Book(title: "My book ORA", totalPages: 321, author: existing.author)
        when:
        def newBook = bookRepository.saveReturning(bookToCreate)
        then:
        newBook.id
        !newBook.is(bookToCreate)
        // lifecycle events
        bookToCreate.prePersist == 1
        newBook.postLoad == 1
        newBook.postPersist == 1
        // verify persisted
        bookRepository.findById(newBook.id).get().title == "My book ORA"
        bookRepository.findByTitle("My book ORA")
    }

    void "test returning insert update and delete with assigned id"() {
        given:
        def repository = context.getBean(OracleXEAssignedIdReturningRepository)
        repository.deleteAll()
        def entity = new AssignedIdReturningEntity(1L, "Assigned Insert")

        when:
        def inserted = repository.insertReturning(entity)

        then:
        !inserted.is(entity)
        inserted.id == 1L
        inserted.title == "Assigned Insert"
        repository.findById(1L).get().title == "Assigned Insert"

        when:
        inserted.title = "Assigned Update"
        def updated = repository.updateReturning(inserted)

        then:
        !updated.is(inserted)
        updated.id == 1L
        updated.title == "Assigned Update"
        repository.findById(1L).get().title == "Assigned Update"

        when:
        def deleted = repository.deleteReturning(updated)

        then:
        deleted.id == 1L
        deleted.title == "Assigned Update"
        !repository.existsById(1L)

        when:
        def entities = [
                new AssignedIdReturningEntity(2L, "Assigned Insert 2"),
                new AssignedIdReturningEntity(3L, "Assigned Insert 3")
        ]
        def insertedEntities = repository.insertReturning(entities)

        then:
        insertedEntities*.id == [2L, 3L]
        insertedEntities*.title == ["Assigned Insert 2", "Assigned Insert 3"]
        repository.findById(2L).get().title == "Assigned Insert 2"
        repository.findById(3L).get().title == "Assigned Insert 3"

        when:
        insertedEntities[0].title = "Assigned Update 2"
        insertedEntities[1].title = "Assigned Update 3"
        def updatedEntities = repository.updateReturning(insertedEntities)

        then:
        updatedEntities*.id == [2L, 3L]
        updatedEntities*.title == ["Assigned Update 2", "Assigned Update 3"]
        repository.findById(2L).get().title == "Assigned Update 2"
        repository.findById(3L).get().title == "Assigned Update 3"

        when:
        def deletedEntities = repository.deleteReturning(updatedEntities)

        then:
        deletedEntities*.id == [2L, 3L]
        deletedEntities*.title == ["Assigned Update 2", "Assigned Update 3"]
        !repository.existsById(2L)
        !repository.existsById(3L)
    }

    void "test insert returning books"() {
        given:
        setupBooks()
        def book = bookRepository.findByTitle("Pet Cemetery")

        def booksToCreate = List.of(
                new Book(title: "My book 1", totalPages: 123, author: book.author),
                new Book(title: "My book 2", totalPages: 123, author: book.author),
                new Book(title: "My book 3", totalPages: 123, author: book.author),
        )
        when:
        def newBooks = bookRepository.saveReturning(
                booksToCreate
        )
        then:
        newBooks.size() == 3
        newBooks[0].id
        !newBooks[0].is(booksToCreate[0])
        newBooks[0].title == "My book 1"
        newBooks[1].title == "My book 2"
        newBooks[2].title == "My book 3"
        def newBook = newBooks[0]
        bookRepository.findById(newBook.id).get().title == "My book 1"
        bookRepository.findByTitle("My book 1")
        booksToCreate.forEach {
            assert it.prePersist == 1
        }
        newBooks.forEach {
            assert it.postLoad == 1
            assert it.postPersist == 1
        }
    }

    void "test delete returning book"() {
        given:
        setupBooks()
        when:
        def book = bookRepository.findByTitle("Pet Cemetery")
        Book deletedBook = bookRepository.deleteReturning(book)
        then:
        deletedBook.id == book.id
        deletedBook.title == book.title
        deletedBook.postLoad == 1
    }

    void "test delete returning title book"() {
        given:
        setupBooks()
        when:
        def book = bookRepository.findByTitle("Pet Cemetery")
        String deletedTitle = bookRepository.deleteReturningTitle(book)
        then:
        deletedTitle == book.title
        bookRepository.findById(book.id).isEmpty()
    }

    void "test insert returning restaurant with embedded fields"() {
        given:
        def restaurantToCreate = new Restaurant("Una", new Address("Main", "21002"))
        when:
        def newRestaurant = restaurantRepository.saveReturning(restaurantToCreate)
        then:
        newRestaurant.id
        !newRestaurant.is(restaurantToCreate)
        newRestaurant.address
        newRestaurant.address.street == "Main"
        // verify persisted
        restaurantRepository.findById(newRestaurant.id).get().name == "Una"
        cleanup:
        restaurantRepository.deleteAll()
    }

    void "test custom insert/update/delete returning book(s) with @Query"() {
        given:
        setupBooks()
        def existing = bookRepository.findByTitle("Pet Cemetery")
        when:
        def one = bookRepository.customInsertReturningBook(existing.author.id, null, "CI one", 111, null, LocalDateTime.now())
        then:
        one
        one.id
        one.title == "CI one"
        when:
        def current = LocalDateTime.now()
        def updated = bookRepository.customUpdateReturning(one.id, "CI one - updated", 110, current)
        then:
        updated.title == "CI one - updated"
        updated.totalPages == 110
        updated.lastUpdated.truncatedTo(ChronoUnit.MILLIS) == current.truncatedTo(ChronoUnit.MILLIS)
        when:
        def title = bookRepository.customDeleteReturningTitle(updated.id)
        then:
        title == "CI one - updated"
        !bookRepository.findById(updated.id).present
        when:
        def many = bookRepository.customInsertReturningBooks(existing.author.id, null, "CI many", 112, null, LocalDateTime.now())
        then:
        many
        many.size() >= 1
        when:
        def onlyTitle = bookRepository.customInsertReturningTitle(existing.author.id, null, "CI title", 113, null, LocalDateTime.now())
        then:
        onlyTitle == "CI title"
        bookRepository.findByTitle("CI title")
        cleanup:
        bookRepository.deleteAll()
    }

    void "test custom update returning title with expanded ids"() {
        given:
        setupBooks()
        def book = bookRepository.findByTitle("Pet Cemetery")
        when:
        def updatedTitle = bookRepository.customUpdateReturningTitleWithExpandedIds(book.id, "Expanded Oracle Title", [book.id, Long.MAX_VALUE])
        then:
        updatedTitle == "Expanded Oracle Title"
        bookRepository.findById(book.id).get().title == "Expanded Oracle Title"
    }

    void "test custom update returning dto projection"() {
        given:
        setupBooks()
        def book = bookRepository.findByTitle("Pet Cemetery")
        when:
        BookDto dto = bookRepository.customUpdateReturningDto(book.id, "Oracle DTO Title", 777)
        then:
        dto.title == "Oracle DTO Title"
        dto.totalPages == 777
        def updated = bookRepository.findById(book.id).get()
        updated.title == "Oracle DTO Title"
        updated.totalPages == 777
    }

    void "test custom update returning method-level projection dto"() {
        given:
        setupBooks()
        def book = bookRepository.findByTitle("Pet Cemetery")
        when:
        OracleBookMethodProjectionDto dto = bookRepository.customUpdateReturningMethodProjectionDto(book.id, "Oracle Projected Title", 321)
        then:
        dto.bookTitle() == "Oracle Projected Title"
        dto.pageCount() == 321
        def updated = bookRepository.findById(book.id).get()
        updated.title == "Oracle Projected Title"
        updated.totalPages == 321
    }

    void "test custom update returning mapped property dto"() {
        given:
        setupBooks()
        def book = bookRepository.findByTitle("Pet Cemetery")
        when:
        OracleBookMappedPropertyDto dto = bookRepository.customUpdateReturningMappedPropertyDto(book.id, "Oracle Mapped Title", 654)
        then:
        dto.renamedTitle == "Oracle Mapped Title"
        dto.renamedPages == 654
        def updated = bookRepository.findById(book.id).get()
        updated.title == "Oracle Mapped Title"
        updated.totalPages == 654
    }

    void "test custom delete returning object array projection"() {
        given:
        setupBooks()
        def book = bookRepository.findByTitle("Pet Cemetery")
        when:
        Object[] values = bookRepository.customDeleteReturningTitleAndPages(book.id)
        then:
        values as List == [book.title, book.totalPages]
        bookRepository.findById(book.id).isEmpty()
    }

}
