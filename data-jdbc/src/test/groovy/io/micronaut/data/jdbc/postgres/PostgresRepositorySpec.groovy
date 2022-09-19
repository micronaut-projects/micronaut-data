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
import io.micronaut.data.tck.repositories.*
import io.micronaut.data.tck.tests.AbstractRepositorySpec

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
    @Override
    boolean isSupportsArrays() {
        return true
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
            def books1 = bookRepository.listNativeBooksNullableSearch(null)
        then:
            books1.size() == 8
        when:
            def books2 = bookRepository.listNativeBooksNullableSearch("The Stand")
        then:
            books2.size() == 1
        when:
            def books3 = bookRepository.listNativeBooksNullableSearch("Xyz")
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


}
