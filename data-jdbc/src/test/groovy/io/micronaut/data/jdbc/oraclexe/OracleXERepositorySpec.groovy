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
import io.micronaut.data.tck.entities.Book
import io.micronaut.data.tck.entities.Face
import io.micronaut.data.tck.jdbc.entities.IntervalEntity
import io.micronaut.data.tck.repositories.*
import io.micronaut.data.tck.tests.AbstractRepositorySpec
import spock.lang.PendingFeature

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

    @PendingFeature
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

    @PendingFeature
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

    @PendingFeature
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

    @PendingFeature
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
}
