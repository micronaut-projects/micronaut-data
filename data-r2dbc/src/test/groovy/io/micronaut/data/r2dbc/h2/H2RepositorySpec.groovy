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
package io.micronaut.data.r2dbc.h2

import groovy.transform.Memoized
import io.micronaut.data.tck.repositories.*
import io.micronaut.data.tck.tests.AbstractRepositorySpec

class H2RepositorySpec extends AbstractRepositorySpec implements H2TestPropertyProvider {

    @Memoized
    @Override
    PersonRepository getPersonRepository() {
        return context.getBean(H2PersonRepository)
    }

    @Override
    GenreRepository getGenreRepository() {
        return context.getBean(H2GenreRepository)
    }

    @Memoized
    @Override
    BookRepository getBookRepository() {
        return context.getBean(H2BookRepository)
    }

    @Memoized
    @Override
    AuthorRepository getAuthorRepository() {
        return context.getBean(H2AuthorRepository)
    }

    @Memoized
    @Override
    CompanyRepository getCompanyRepository() {
        return context.getBean(H2CompanyRepository)
    }

    @Memoized
    @Override
    BookDtoRepository getBookDtoRepository() {
        return context.getBean(H2BookDtoRepository)
    }

    @Memoized
    @Override
    CountryRepository getCountryRepository() {
        return context.getBean(H2CountryRepository)
    }

    @Memoized
    @Override
    CityRepository getCityRepository() {
        return context.getBean(H2CityRepository)
    }

    @Memoized
    @Override
    RegionRepository getRegionRepository() {
        return context.getBean(H2RegionRepository)
    }

    @Memoized
    @Override
    NoseRepository getNoseRepository() {
        return context.getBean(H2NoseRepository)
    }

    @Memoized
    @Override
    FaceRepository getFaceRepository() {
        return context.getBean(H2FaceRepository)
    }

    @Memoized
    @Override
    CountryRegionCityRepository getCountryRegionCityRepository() {
        return context.getBean(H2CountryRegionCityRepository)
    }

    @Memoized
    @Override
    UserRoleRepository getUserRoleRepository() {
        return context.getBean(H2UserRoleRepository)
    }

    @Memoized
    @Override
    RoleRepository getRoleRepository() {
        return context.getBean(H2RoleRepository)
    }

    @Memoized
    @Override
    UserRepository getUserRepository() {
        return context.getBean(H2UserRepository)
    }

    @Memoized
    @Override
    MealRepository getMealRepository() {
        return context.getBean(H2MealRepository)
    }

    @Memoized
    @Override
    FoodRepository getFoodRepository() {
        return context.getBean(H2FoodRepository)
    }

    @Memoized
    @Override
    StudentRepository getStudentRepository() {
        return context.getBean(H2StudentRepository)
    }

    @Memoized
    @Override
    CarRepository getCarRepository() {
        return context.getBean(H2CarRepository)
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

    @Memoized
    @Override
    PageRepository getPageRepository() {
        return context.getBean(H2PageRepository)
    }

    @Override
    protected boolean skipQueryByDataArray() {
        return true
    }

    @Override
    boolean testLockingForUpdate() {
        return false
    }

    @Override
    boolean supportsNullCharacter() {
        false
    }
}
