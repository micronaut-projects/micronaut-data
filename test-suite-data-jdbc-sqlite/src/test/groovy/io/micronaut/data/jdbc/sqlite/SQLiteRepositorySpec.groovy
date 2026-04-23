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
package io.micronaut.data.jdbc.sqlite

import groovy.sql.Sql
import groovy.transform.Memoized
import io.micronaut.data.connection.jdbc.advice.DelegatingDataSource
import io.micronaut.data.model.Pageable
import io.micronaut.data.model.Sort
import io.micronaut.data.tck.entities.Book
import io.micronaut.data.tck.entities.Student
import io.micronaut.data.tck.entities.embedded.BookEntity
import io.micronaut.data.tck.entities.embedded.BookState
import io.micronaut.data.tck.entities.embedded.ResourceEntity
import io.micronaut.data.tck.repositories.*
import io.micronaut.data.tck.tests.AbstractRepositorySpec
import io.micronaut.inject.qualifiers.Qualifiers
import spock.lang.Shared

import javax.sql.DataSource

import static io.micronaut.data.tck.repositories.PersonRepository.Specifications.findNameSubqueryEq
import static io.micronaut.data.tck.repositories.PersonRepository.Specifications.findNameSubqueryIn
import static io.micronaut.data.tck.repositories.PersonRepository.Specifications.nameEqualsCaseInsensitive
import static io.micronaut.data.tck.repositories.PersonRepository.Specifications.subqueriesWithJoinReferencingOuter

class SQLiteRepositorySpec extends AbstractRepositorySpec implements SQLiteTestPropertyProvider {

    @Shared
    DataSource dataSource = DelegatingDataSource.unwrapDataSource(context.getBean(DataSource, Qualifiers.byName("default")))

    @Shared
    SQLitePersonRepository pr = context.getBean(SQLitePersonRepository)

    @Shared
    SQLiteBookRepository br = context.getBean(SQLiteBookRepository)

    @Shared
    GenreRepository genreRepo = context.getBean(SQLiteGenreRepository)

    @Shared
    SQLiteAuthorRepository ar = context.getBean(SQLiteAuthorRepository)

    @Shared
    SQLiteCompanyRepository cr = context.getBean(SQLiteCompanyRepository)

    @Shared
    SQLiteBookDtoRepository dto = context.getBean(SQLiteBookDtoRepository)

    @Shared
    SQLiteCountryRepository countryr = context.getBean(SQLiteCountryRepository)

    @Shared
    SQLiteCountryRegionCityRepository countryrcr = context.getBean(SQLiteCountryRegionCityRepository)

    @Shared
    SQLiteCityRepository cityr = context.getBean(SQLiteCityRepository)

    @Shared
    SQLiteRegionRepository regr = context.getBean(SQLiteRegionRepository)

    @Shared
    SQLiteFaceRepository fr = context.getBean(SQLiteFaceRepository)

    @Shared
    SQLiteNoseRepository nr = context.getBean(SQLiteNoseRepository)

    @Shared
    SQLiteCarRepository carRepo = context.getBean(SQLiteCarRepository)

    @Shared
    SQLiteUserRoleRepository userRoleRepo = context.getBean(SQLiteUserRoleRepository)

    @Shared
    SQLiteRoleRepository roleRepo = context.getBean(SQLiteRoleRepository)

    @Shared
    SQLiteUserRepository userRepo = context.getBean(SQLiteUserRepository)

    @Shared
    SQLiteMealRepository mealRepo = context.getBean(SQLiteMealRepository)

    @Shared
    SQLiteFoodRepository foodRepo = context.getBean(SQLiteFoodRepository)

    @Shared
    SQLiteStudentRepository studentRepo = context.getBean(SQLiteStudentRepository)

    @Shared
    SQLitePageRepository pageRepo = context.getBean(SQLitePageRepository)

    @Shared
    SQLiteEntityWithIdClassRepository entityWithIdClassRepo = context.getBean(SQLiteEntityWithIdClassRepository)

    @Shared
    SQLiteEntityWithIdClass2Repository entityWithIdClass2Repo = context.getBean(SQLiteEntityWithIdClass2Repository)

    @Shared
    SQLiteBookEntityRepository bookEntityRepository = context.getBean(SQLiteBookEntityRepository)

    @Shared
    SQLiteExampleEntityRepository exampleEntityRepo = context.getBean(SQLiteExampleEntityRepository)

    @Shared
    IntervalRepository intervalRepo = context.getBean(SQLiteIntervalRepository)

    @Override
    EntityWithIdClassRepository getEntityWithIdClassRepository() {
        return entityWithIdClassRepo
    }

    @Override
    EntityWithIdClass2Repository getEntityWithIdClass2Repository() {
        return entityWithIdClass2Repo
    }

    @Override
    NoseRepository getNoseRepository() {
        return nr
    }

    @Override
    FaceRepository getFaceRepository() {
        return fr
    }

    @Override
    PersonRepository getPersonRepository() {
        return pr
    }

    @Override
    BookRepository getBookRepository() {
        return br
    }

    @Override
    GenreRepository getGenreRepository() {
        return genreRepo
    }

    @Override
    AuthorRepository getAuthorRepository() {
        return ar
    }

    @Override
    CompanyRepository getCompanyRepository() {
        return cr
    }

    @Override
    BookDtoRepository getBookDtoRepository() {
        return dto
    }

    @Override
    CountryRepository getCountryRepository() {
        return countryr
    }

    @Override
    CityRepository getCityRepository() {
        return cityr
    }

    @Override
    RegionRepository getRegionRepository() {
        return regr
    }

    @Override
    CountryRegionCityRepository getCountryRegionCityRepository() {
        return countryrcr
    }

    @Override
    UserRoleRepository getUserRoleRepository() {
        return userRoleRepo
    }

    @Override
    RoleRepository getRoleRepository() {
        return roleRepo
    }

    @Override
    UserRepository getUserRepository() {
        return userRepo
    }

    @Override
    MealRepository getMealRepository() {
        return mealRepo
    }

    @Override
    FoodRepository getFoodRepository() {
        return foodRepo
    }

    @Override
    StudentRepository getStudentRepository() {
        return studentRepo
    }

    @Override
    protected void cleanupBooks() {
        Sql sql = new Sql(dataSource)
        try {
            sql.executeUpdate('DELETE FROM "book_student"')
        } finally {
            sql.close()
        }
        super.cleanupBooks()
    }

    @Override
    protected void cleanupData() {
        studentRepository.deleteAll()
        super.cleanupData()
    }

    @Override
    CarRepository getCarRepository() {
        return carRepo
    }

    @Memoized
    @Override
    BasicTypesRepository getBasicTypeRepository() {
        return context.getBean(SQLiteBasicTypesRepository)
    }

    @Memoized
    @Override
    TimezoneBasicTypesRepository getTimezoneBasicTypeRepository() {
        return context.getBean(SQLiteTimezoneBasicTypesRepository)
    }

    @Override
    PageRepository getPageRepository() {
        return pageRepo
    }

    @Override
    ExampleEntityRepository getExampleEntityRepository() {
        return exampleEntityRepo
    }

    @Memoized
    @Override
    IntervalRepository getIntervalRepository() {
        return intervalRepo
    }

    @Override
    boolean isSupportsArrays() {
        return true
    }

    @Override
    protected boolean skipQueryByDataArray() {
        return true
    }

    void "test subquery with JOIN" () {
        given:
            saveSampleBooks()
        when:
            def books = bookRepository.findAll(subqueriesWithJoinReferencingOuter())
        then:
            books.size() == 6
    }

    void "test subquery IN" () {
        when:
            savePersons(["Jeff", "James"])
            def person = personRepository.findOne(findNameSubqueryIn("James"))
        then:
            person
    }

    void "test subquery EQ" () {
        when:
            savePersons(["Jeff", "James"])
            def person = personRepository.findOne(findNameSubqueryEq("James"))
        then:
            person
    }

    void "test criteria lower select" () {
        when:
            savePersons(["Jeff", "James"])
            def person = personRepository.findOne(nameEqualsCaseInsensitive("james"))
        then:
            person.isPresent()
    }

    void "test manual joining on many ended association"() {
        given:
        saveSampleBooks()

        when:
        def author = context.getBean(SQLiteBookService).findByName("Stephen King")

        then:
        author != null
        author.name == "Stephen King"
        author.books.size() == 2
        author.books.find { it.title == "The Stand"}
        author.books.find { it.title == "Pet Cemetery"}

        cleanup:
        cleanupData()
    }

    void "test SQL mapping function"() {
        given:
        saveSampleBooks()

        when:"using a function that maps a single value"
        def book = ar.testReadSingleProperty("The Stand", 700)

        then:"The result is correct"
        book != null
        book.author.name == 'Stephen King'

        when:"using a function that maps an associated entity value"
        book = ar.testReadAssociatedEntity("The Stand", 700)

        then:"The result is correct"
        book != null
        book.author.name == 'Stephen King'
        book.author.id

        when:"using a function that maps a DTO"
        book = ar.testReadDTO("The Stand", 700)

        then:"The result is correct"
        book != null
        book.author.name == 'Stephen King'

        then:
        cleanupData()
    }

    void "find by embedded entity field"() {
        when:
        def bookEntity = new BookEntity(1L, new ResourceEntity<BookState>("1984", BookState.BORROWED))
        bookEntityRepository.save(bookEntity)
        def result = bookEntityRepository.findAllByResourceState(BookState.BORROWED)
        then:
        result
        cleanup:
        bookEntityRepository.deleteAll()
    }

    void "test JOIN pagination xxx"() {
        if (skipJoinPagination()) {
            return
        }
        given:
            Student denis = new Student("Denis")
            Student josh = new Student("Josh")
            Student kevin = new Student("Kevin")
            def book1 = new Book(title: "The Stand", students: [denis, josh])
            def book2 = new Book(title: "Pet Cemetery", students: [kevin])
            def book3 = new Book(title: "Along Came a Spider", students: [kevin, josh])
            bookRepository.save(book1)
            bookRepository.save(book2)
            bookRepository.save(book3
            )
            List<String> names = [denis.name, josh.name]
        when:
            io.micronaut.data.model.Page<Book> page = bookRepository.findAllByStudentsNameIn(names, Pageable.from(0, 10, Sort.of(Sort.Order.asc("title"))))

        then:
            page.totalSize == page.content.size()
            page.totalSize == 2
            page.content.collect { it.title }.sort() == ["Along Came a Spider", "The Stand"]
            page.content[0].students.collect { it.name }.sort() == ["Josh", "Kevin"]
            page.content[1].students.collect { it.name }.sort() == ["Denis", "Josh"]

        when:
            def pageable = Pageable.from(0, 1, Sort.of(Sort.Order.asc("title")))
            page = bookRepository.findAllByStudentsNameIn(names, pageable)

        then:
            page.totalSize == 2
            page.content.size() == 1
            page.content[0].title == "Along Came a Spider"
            page.content[0].students.collect { it.name }.sort() == ["Josh", "Kevin"]

        when:
            pageable = pageable.next()
            page = bookRepository.findAllByStudentsNameIn(names, pageable)

        then:
            page.totalSize == 2
            page.content.size() == 1
            page.content[0].title == "The Stand"
            page.content[0].students.collect { it.name }.sort() == ["Denis", "Josh"]

        when:
            pageable = pageable.next()
            page = bookRepository.findAllByStudentsNameIn(names, pageable)

        then:
            page.totalSize == 2
            page.content.size() == 0

        when:
            pageable = pageable.previous()
            page = bookRepository.findAllByStudentsNameIn(names, pageable)

        then:
            page.totalSize == 2
            page.content.size() == 1
            page.content[0].title == "The Stand"
            page.content[0].students.collect { it.name }.sort() == ["Denis", "Josh"]
    }
}
