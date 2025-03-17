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
package io.micronaut.data.jdbc.postgres

import groovy.transform.Memoized
import io.micronaut.data.model.Sort
import io.micronaut.data.tck.entities.Book
import io.micronaut.data.tck.repositories.*
import io.micronaut.data.tck.tests.AbstractRepositorySpec

import java.time.LocalDateTime

class PostgresRepositorySpec extends AbstractRepositorySpec implements PostgresTestPropertyProvider {

    @Memoized
    @Override
    PersonRepository getPersonRepository() {
        return context.getBean(PostgresPersonRepository)
    }

    @Memoized
    @Override
    PostgresBookRepository getBookRepository() {
        return context.getBean(PostgresBookRepository)
    }

    @Memoized
    @Override
    GenreRepository getGenreRepository() {
        return context.getBean(PostgresGenreRepository)
    }

    @Memoized
    @Override
    PostgresAuthorRepository getAuthorRepository() {
        return context.getBean(PostgresAuthorRepository)
    }

    @Memoized
    @Override
    CompanyRepository getCompanyRepository() {
        return context.getBean(PostgresCompanyRepository)
    }

    @Memoized
    @Override
    BookDtoRepository getBookDtoRepository() {
        return context.getBean(PostgresBookDtoRepository)
    }

    @Memoized
    @Override
    CountryRepository getCountryRepository() {
        return context.getBean(PostgresCountryRepository)
    }

    @Memoized
    @Override
    CityRepository getCityRepository() {
        return context.getBean(PostgresCityRepository)
    }

    @Memoized
    @Override
    RegionRepository getRegionRepository() {
        return context.getBean(PostgresRegionRepository)
    }

    @Memoized
    @Override
    NoseRepository getNoseRepository() {
        return context.getBean(PostgresNoseRepository)
    }

    @Memoized
    @Override
    FaceRepository getFaceRepository() {
        return context.getBean(PostgresFaceRepository)
    }

    @Memoized
    @Override
    CountryRegionCityRepository getCountryRegionCityRepository() {
        return context.getBean(PostgresCountryRegionCityRepository)
    }

    @Memoized
    @Override
    UserRoleRepository getUserRoleRepository() {
        return context.getBean(PostgresUserRoleRepository)
    }

    @Memoized
    @Override
    RoleRepository getRoleRepository() {
        return context.getBean(PostgresRoleRepository)
    }

    @Memoized
    @Override
    io.micronaut.data.tck.repositories.UserRepository getUserRepository() {
        return context.getBean(PostgresUserRepository)
    }

    @Memoized
    @Override
    MealRepository getMealRepository() {
        return context.getBean(PostgresMealRepository)
    }

    @Memoized
    @Override
    FoodRepository getFoodRepository() {
        return context.getBean(PostgresFoodRepository)
    }

    @Memoized
    @Override
    StudentRepository getStudentRepository() {
        return context.getBean(PostgresStudentRepository)
    }

    @Memoized
    @Override
    CarRepository getCarRepository() {
        return context.getBean(PostgresCarRepository)
    }

    @Memoized
    @Override
    BasicTypesRepository getBasicTypeRepository() {
        return context.getBean(PostgresBasicTypesRepository)
    }

    @Memoized
    @Override
    TimezoneBasicTypesRepository getTimezoneBasicTypeRepository() {
        return context.getBean(PostgresTimezoneBasicTypesRepository)
    }

    @Memoized
    @Override
    PageRepository getPageRepository() {
        return context.getBean(PostgresPageRepository)
    }

    @Memoized
    PostgresDtoTestService getDtoTestService() {
        context.getBean(PostgresDtoTestService)
    }

    @Memoized
    @Override
    EntityWithIdClassRepository getEntityWithIdClassRepository() {
        return context.getBean(PostgresEntityWithIdClassRepository)
    }

    @Memoized
    @Override
    EntityWithIdClass2Repository getEntityWithIdClass2Repository() {
        return context.getBean(PostgresEntityWithIdClass2Repository)
    }

    @Memoized
    @Override
    ExampleEntityRepository getExampleEntityRepository() {
        return context.getBean(PostgresExampleEntityRepository)
    }

    @Memoized
    @Override
    boolean isSupportsArrays() {
        return true
    }

    void "test procedure"() {
        expect:
            bookRepository.add1(123) == 124
            bookRepository.add1Aliased(123) == 124
    }

    void "test escaped"() {
        when:
            def escaped = bookRepository.reproduceColonErrorEscaped()
        then:
            escaped == 'one:two:three'
    }

    void "test native query with nullable property"() {
        given:
            setupBooks()
        when:
            def books1 = bookRepository.listNativeBooksNullableSearch(null, null)
            def books1Sorted = bookRepository.listNativeBooksNullableSearch(null, Sort.of(Sort.Order.asc("title")))
        then:
            books1.size() == 8
            books1Sorted.size() == 8
            // verify native query with given sort returned sorted results as expected
            def book1Titles = books1.stream().map(b -> b.title).sorted().toList()
            book1Titles.size() == 8
            for (int i = 0; i < book1Titles.size(); i++) {
                def title = book1Titles[i]
                def book1Sorted = books1Sorted[i]
                title == book1Sorted.title
            }
        when:
            def books2 = bookRepository.listNativeBooksNullableSearch("The Stand", null)
        then:
            books2.size() == 1
        when:
            def books3 = bookRepository.listNativeBooksNullableSearch("Xyz", null)
        then:
            books3.size() == 0
        when:
            def books4 = bookRepository.listNativeBooksNullableListSearch(["The Stand", "FFF"])
        then:
            books4.size() == 1
        when:
            def books5 = bookRepository.listNativeBooksNullableListSearch(["Xyz", "FFF"])
        then:
            books5.size() == 0
        when:
            def books6 = bookRepository.listNativeBooksNullableListSearch([])
        then:
            books6.size() == 0
        when:
            def books7 = bookRepository.listNativeBooksNullableListSearch(null)
        then:
            books7.size() == 0
        when:
            def books8 = bookRepository.listNativeBooksNullableArraySearch(new String[]{"Xyz", "Ffff", "zzz"})
        then:
            books8.size() == 0
        when:
            def books9 = bookRepository.listNativeBooksNullableArraySearch(new String[]{})
        then:
            books9.size() == 0
        when:
            def books11 = bookRepository.listNativeBooksNullableArraySearch(null)
        then:
            books11.size() == 0
        then:
            def books12 = bookRepository.listNativeBooksNullableArraySearch(new String[]{"The Stand"})
        then:
            books12.size() == 1
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
            newBook.postLoad == 1
            newBook.postUpdate == 1
            book.postLoad == 1
            book.preUpdate == 1
    }

    void "test update returning books"() {
        given:
            setupBooks()
        when:
            def book = bookRepository.findByTitle("Pet Cemetery")
            def books = bookRepository.findByAuthorName(book.author.name)
            books.forEach {
                it.title += "UPDATED"
            }
            List<Book> newBooks = bookRepository.updateReturning(books)
            book.title = "old"
        then:
            newBooks.size() == 2
            newBooks.forEach {
                assert it.title.endsWith("UPDATED")
                assert it.postLoad == 1
                assert it.postUpdate == 1
            }
            books.forEach {
                assert it.postLoad == 1
                assert it.preUpdate == 1
            }
    }

    void "test insert returning book"() {
        given:
            setupBooks()
            def book = bookRepository.findByTitle("Pet Cemetery")
            def bookToCreate = new Book(title: "My book", totalPages: 123, author: book.author)
        when:
            def newBook = bookRepository.saveReturning(
                    bookToCreate
            )
        then:
            newBook.id
            !newBook.is(bookToCreate)
            bookToCreate.prePersist == 1
            newBook.postLoad == 1
            newBook.postPersist == 1
            bookRepository.findById(newBook.id).get().title == "My book"
            bookRepository.findByTitle("My book")
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

    void "test custom insert returning book"() {
        given:
            setupBooks()
            def book = bookRepository.findByTitle("Pet Cemetery")
        when:
            def newBook = bookRepository.customInsertReturningBook(
                    book.getAuthor().getId(),
                    null,
                    "My book",
                    123,
                    null,
                    LocalDateTime.now()
            )
        then:
            bookRepository.findById(newBook.id).get().title == "My book"
            bookRepository.findByTitle("My book")
    }

    void "test custom insert returning books"() {
        given:
            setupBooks()
            def book = bookRepository.findByTitle("Pet Cemetery")
        when:
            def newBooks = bookRepository.customInsertReturningBooks(
                    book.getAuthor().getId(),
                    null,
                    "My book",
                    123,
                    null,
                    LocalDateTime.now()
            )
        then:
            newBooks.size() == 1
            def newBook = newBooks[0]
            bookRepository.findById(newBook.id).get().title == "My book"
            bookRepository.findByTitle("My book")
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

    void "test update all books with author and returning books"() {
        given:
            setupBooks()
            def books = bookRepository.findAll()
            def petCemetery = bookRepository.findByTitle("Pet Cemetery")
        when:
            def b = bookRepository.updateReturning(petCemetery.author.id)
        then:
            b.size() == books.size()
        when:
            def allBooks = bookRepository.findAll()
        then:
            allBooks.forEach {
                assert it.author.id == petCemetery.author.id
            }
            b.forEach {
                assert it.postLoad == 1
            }
    }

    void "test update all books with author and returning a book"() {
        given:
            setupBooks()
            def petCemetery = bookRepository.findByTitle("Pet Cemetery")
        when:
            def b = bookRepository.modifyReturning(petCemetery.author.id)
        then:
            b.author.id == petCemetery.author.id
            b.postLoad == 1
        when:
            def allBooks = bookRepository.findAll()
        then:
            allBooks.forEach {
                assert it.author.id == petCemetery.author.id
            }
    }

    void "test custom update all books with author and returning books"() {
        given:
            setupBooks()
            def books = bookRepository.findAll()
            def petCemetery = bookRepository.findByTitle("Pet Cemetery")
        when:
            def b = bookRepository.customUpdateReturningBooks(petCemetery.author.id)
        then:
            b.size() == books.size()
        when:
            def allBooks = bookRepository.findAll()
        then:
            allBooks.forEach {
                assert it.author.id == petCemetery.author.id
            }
            b.forEach {
                assert it.postLoad == 1
            }
    }

    void "test custom update all books with author and returning a book"() {
        given:
            setupBooks()
            def petCemetery = bookRepository.findByTitle("Pet Cemetery")
        when:
            def b = bookRepository.customUpdateReturningBook(petCemetery.author.id)
        then:
            b.author.id == petCemetery.author.id
            b.postLoad == 1
        when:
            def allBooks = bookRepository.findAll()
        then:
            allBooks.forEach {
                assert it.author.id == petCemetery.author.id
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

    void "test delete returning book custom query"() {
        given:
            setupBooks()
        when:
            def book = bookRepository.findByTitle("Pet Cemetery")
            Book deletedBook = bookRepository.customDeleteOne(book.id)
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

    void "test delete returning last updated book"() {
        given:
            setupBooks()
        when:
            def book = bookRepository.findByTitle("Pet Cemetery")
            LocalDateTime lastUpdated = bookRepository.deleteReturningLastUpdated(book.id, book.title)
        then:
            lastUpdated == book.lastUpdated
            bookRepository.findById(book.id).isEmpty()
    }

    void "test delete returning last updated book 2"() {
        given:
            setupBooks()
        when:
            def book = bookRepository.findByTitle("Pet Cemetery")
            LocalDateTime lastUpdated = bookRepository.deleteByIdAndTitleReturningLastUpdated(book.id, book.title)
        then:
            lastUpdated == book.lastUpdated
            bookRepository.findById(book.id).isEmpty()
    }

    void "test delete returning books"() {
        given:
            setupBooks()
        when:
            def book = bookRepository.findByTitle("Pet Cemetery")
            def books = bookRepository.findByAuthorName(book.author.name)
            List<Book> deletedBooks = bookRepository.deleteReturning(books)
        then:
            deletedBooks.size() == books.size()
            deletedBooks[0].id == books[0].id
            deletedBooks[0].title == books[0].title
            bookRepository.findByAuthorName(book.author.name).isEmpty()
    }

    void "test delete returning author books"() {
        given:
            setupBooks()
        when:
            def book = bookRepository.findByTitle("Pet Cemetery")
            def books = bookRepository.findByAuthorName(book.author.name)
            List<Book> deletedBooks = bookRepository.deleteReturning(book.author.id)
        then:
            deletedBooks.size() == books.size()
            deletedBooks[0].id == books[0].id
            deletedBooks[0].title == books[0].title
            bookRepository.findByAuthorName(book.author.name).isEmpty()
    }

    void "test custom delete all"() {
        given:
            setupBooks()
        when:
            def allBooks = bookRepository.findAll().sort {it.id }
            def deletedBooks = bookRepository.customDeleteAll().sort {it.id }
        then:
            allBooks.size() == deletedBooks.size()
            allBooks.eachWithIndex { book, index ->
                def deletedBook = deletedBooks[index]
                assert book.id == deletedBook.id
                assert book.title == deletedBook.title
            }
    }

    void "test DTO with and without constructor"() {
        when:"Retrieve DTO using Jdbc Operations"
        def result0 = dtoTestService.getDto(0, PostgresDtoTestService.DtoWithoutConstructor.class)
        def result1 = dtoTestService.getDto(1, PostgresDtoTestService.DtoWithAllArgsConstructor.class)
        def result2 = dtoTestService.getDto(2, PostgresDtoTestService.DtoRecord.class)
        then:
        result0.id == 0
        result1.id == 1
        result2.id() == 2
        result0.tags == ["foo", "bar"]
        result1.tags == ["foo", "bar"]
        result2.tags() == ["foo", "bar"]
        when:"Retrieve DTO using repository"
        result0 = dtoTestService.getDtoUsingRepository(0, PostgresDtoTestService.DtoWithoutConstructor.class)
        result1 = dtoTestService.getDtoUsingRepository(1, PostgresDtoTestService.DtoWithAllArgsConstructor.class)
        result2 = dtoTestService.getDtoUsingRepository(2, PostgresDtoTestService.DtoRecord.class)
        then:
        result0.id == 0
        result1.id == 1
        result2.id() == 2
        result0.tags == ["foo", "bar"]
        result1.tags == ["foo", "bar"]
        result2.tags() == ["foo", "bar"]
    }

}
