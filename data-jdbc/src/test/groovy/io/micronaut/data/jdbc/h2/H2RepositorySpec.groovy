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
package io.micronaut.data.jdbc.h2

import groovy.transform.Memoized
import io.micronaut.data.tck.entities.embedded.BookEntity
import io.micronaut.data.tck.entities.embedded.BookState
import io.micronaut.data.tck.entities.embedded.ResourceEntity
import io.micronaut.data.tck.repositories.*
import io.micronaut.data.tck.tests.AbstractRepositorySpec
import spock.lang.Shared

import static io.micronaut.data.tck.repositories.PersonRepository.Specifications.findNameSubqueryEq
import static io.micronaut.data.tck.repositories.PersonRepository.Specifications.findNameSubqueryIn
import static io.micronaut.data.tck.repositories.PersonRepository.Specifications.nameEqualsCaseInsensitive
import static io.micronaut.data.tck.repositories.PersonRepository.Specifications.subqueriesWithJoinReferencingOuter

class H2RepositorySpec extends AbstractRepositorySpec implements H2TestPropertyProvider {

    @Shared
    H2PersonRepository pr = context.getBean(H2PersonRepository)

    @Shared
    H2BookRepository br = context.getBean(H2BookRepository)

    @Shared
    GenreRepository genreRepo = context.getBean(H2GenreRepository)

    @Shared
    H2AuthorRepository ar = context.getBean(H2AuthorRepository)

    @Shared
    H2CompanyRepository cr = context.getBean(H2CompanyRepository)

    @Shared
    H2BookDtoRepository dto = context.getBean(H2BookDtoRepository)

    @Shared
    H2CountryRepository countryr = context.getBean(H2CountryRepository)

    @Shared
    H2CountryRegionCityRepository countryrcr = context.getBean(H2CountryRegionCityRepository)

    @Shared
    H2CityRepository cityr = context.getBean(H2CityRepository)

    @Shared
    H2RegionRepository regr = context.getBean(H2RegionRepository)

    @Shared
    H2FaceRepository fr = context.getBean(H2FaceRepository)

    @Shared
    H2NoseRepository nr = context.getBean(H2NoseRepository)

    @Shared
    H2CarRepository carRepo = context.getBean(H2CarRepository)

    @Shared
    H2UserRoleRepository userRoleRepo = context.getBean(H2UserRoleRepository)

    @Shared
    H2RoleRepository roleRepo = context.getBean(H2RoleRepository)

    @Shared
    H2UserRepository userRepo = context.getBean(H2UserRepository)

    @Shared
    H2MealRepository mealRepo = context.getBean(H2MealRepository)

    @Shared
    H2FoodRepository foodRepo = context.getBean(H2FoodRepository)

    @Shared
    H2StudentRepository studentRepo = context.getBean(H2StudentRepository)

    @Shared
    H2PageRepository pageRepo = context.getBean(H2PageRepository)

    @Shared
    H2EntityWithIdClassRepository entityWithIdClassRepo = context.getBean(H2EntityWithIdClassRepository)

    @Shared
    H2EntityWithIdClass2Repository entityWithIdClass2Repo = context.getBean(H2EntityWithIdClass2Repository)

    @Shared
    H2BookEntityRepository bookEntityRepository = context.getBean(H2BookEntityRepository)

    @Shared
    H2ExampleEntityRepository exampleEntityRepo = context.getBean(H2ExampleEntityRepository)

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
    CarRepository getCarRepository() {
        return carRepo
    }

    @Memoized
    @Override
    BasicTypesRepository getBasicTypeRepository() {
        return context.getBean(H2BasicTypesRepository)
    }

    @Memoized
    @Override
    TimezoneBasicTypesRepository getTimezoneBasicTypeRepository() {
        return context.getBean(H2TimezoneBasicTypesRepository)
    }

    @Override
    PageRepository getPageRepository() {
        return pageRepo
    }

    @Override
    ExampleEntityRepository getExampleEntityRepository() {
        return exampleEntityRepo
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
        def author = context.getBean(H2BookService).findByName("Stephen King")

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
}
