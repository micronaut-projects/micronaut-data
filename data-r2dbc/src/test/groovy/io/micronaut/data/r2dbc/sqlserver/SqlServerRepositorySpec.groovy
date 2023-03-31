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
package io.micronaut.data.r2dbc.sqlserver

import groovy.transform.Memoized
import io.micronaut.data.tck.repositories.*
import io.micronaut.data.tck.tests.AbstractRepositorySpec

class SqlServerRepositorySpec extends AbstractRepositorySpec implements SqlServerTestPropertyProvider {

    @Override
    boolean testLockingForUpdate() {
        return false
    }

    @Memoized
    @Override
    PersonRepository getPersonRepository() {
        return context.getBean(MSPersonRepository)
    }

    @Memoized
    @Override
    GenreRepository getGenreRepository() {
        return context.getBean(MSGenreRepository)
    }

    @Memoized
    @Override
    BookRepository getBookRepository() {
        return context.getBean(MSBookRepository)
    }

    @Memoized
    @Override
    AuthorRepository getAuthorRepository() {
        return context.getBean(MSAuthorRepository)
    }

    @Memoized
    @Override
    CompanyRepository getCompanyRepository() {
        return context.getBean(MSCompanyRepository)
    }

    @Memoized
    @Override
    BookDtoRepository getBookDtoRepository() {
        return context.getBean(MSBookDtoRepository)
    }

    @Memoized
    @Override
    CountryRepository getCountryRepository() {
        return context.getBean(MSCountryRepository)
    }

    @Memoized
    @Override
    CityRepository getCityRepository() {
        return context.getBean(MSCityRepository)
    }

    @Memoized
    @Override
    RegionRepository getRegionRepository() {
        return context.getBean(MSRegionRepository)
    }

    @Memoized
    @Override
    NoseRepository getNoseRepository() {
        return context.getBean(MSNoseRepository)
    }

    @Memoized
    @Override
    FaceRepository getFaceRepository() {
        return context.getBean(MSFaceRepository)
    }

    @Memoized
    @Override
    CountryRegionCityRepository getCountryRegionCityRepository() {
        return context.getBean(MSCountryRegionCityRepository)
    }

    @Memoized
    @Override
    UserRoleRepository getUserRoleRepository() {
        return context.getBean(MSUserRoleRepository)
    }

    @Memoized
    @Override
    RoleRepository getRoleRepository() {
        return context.getBean(MSRoleRepository)
    }

    @Memoized
    @Override
    UserRepository getUserRepository() {
        return context.getBean(MSUserRepository)
    }

    @Memoized
    @Override
    MealRepository getMealRepository() {
        return context.getBean(MSMealRepository)
    }

    @Memoized
    @Override
    FoodRepository getFoodRepository() {
        return context.getBean(MSFoodRepository)
    }

    @Memoized
    @Override
    StudentRepository getStudentRepository() {
        return context.getBean(MSStudentRepository)
    }

    @Memoized
    @Override
    CarRepository getCarRepository() {
        return context.getBean(MSCarRepository)
    }

    @Memoized
    @Override
    BasicTypesRepository getBasicTypeRepository() {
        return context.getBean(MSBasicTypesRepository)
    }

    @Memoized
    @Override
    TimezoneBasicTypesRepository getTimezoneBasicTypeRepository() {
        return null
    }

    @Memoized
    @Override
    PageRepository getPageRepository() {
        return context.getBean(MSPageRepository)
    }

    @Override
    boolean supportsNullCharacter() {
        false
    }
}
