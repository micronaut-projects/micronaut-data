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
package io.micronaut.data.r2dbc.mariadb

import groovy.transform.Memoized
import io.micronaut.data.r2dbc.mysql.*
import io.micronaut.data.tck.repositories.*
import io.micronaut.data.tck.tests.AbstractRepositorySpec

class MariaDbRepositorySpec extends AbstractRepositorySpec implements MariaDbTestPropertyProvider {

    @Memoized
    @Override
    PersonRepository getPersonRepository() {
        return context.getBean(MySqlPersonRepository)
    }

    @Memoized
    @Override
    BookRepository getBookRepository() {
        return context.getBean(MySqlBookRepository)
    }

    @Memoized
    @Override
    GenreRepository getGenreRepository() {
        return context.getBean(MySqlGenreRepository)
    }

    @Memoized
    @Override
    AuthorRepository getAuthorRepository() {
        return context.getBean(MySqlAuthorRepository)
    }

    @Memoized
    @Override
    CompanyRepository getCompanyRepository() {
        return context.getBean(MySqlCompanyRepository)
    }

    @Memoized
    @Override
    BookDtoRepository getBookDtoRepository() {
        return context.getBean(MySqlBookDtoRepository)
    }

    @Memoized
    @Override
    CountryRepository getCountryRepository() {
        return context.getBean(MySqlCountryRepository)
    }

    @Memoized
    @Override
    CityRepository getCityRepository() {
        return context.getBean(MySqlCityRepository)
    }

    @Memoized
    @Override
    RegionRepository getRegionRepository() {
        return context.getBean(MySqlRegionRepository)
    }

    @Memoized
    @Override
    NoseRepository getNoseRepository() {
        return context.getBean(MySqlNoseRepository)
    }

    @Memoized
    @Override
    FaceRepository getFaceRepository() {
        return context.getBean(MySqlFaceRepository)
    }

    @Memoized
    @Override
    CountryRegionCityRepository getCountryRegionCityRepository() {
        return context.getBean(MySqlCountryRegionCityRepository)
    }

    @Memoized
    @Override
    UserRoleRepository getUserRoleRepository() {
        return context.getBean(MySqlUserRoleRepository)
    }

    @Memoized
    @Override
    RoleRepository getRoleRepository() {
        return context.getBean(MySqlRoleRepository)
    }

    @Memoized
    @Override
    UserRepository getUserRepository() {
        return context.getBean(MySqlUserRepository)
    }

    @Memoized
    @Override
    MealRepository getMealRepository() {
        return context.getBean(MySqlMealRepository)
    }

    @Memoized
    @Override
    FoodRepository getFoodRepository() {
        return context.getBean(MySqlFoodRepository)
    }

    @Memoized
    @Override
    StudentRepository getStudentRepository() {
        return context.getBean(MySqlStudentRepository)
    }

    @Memoized
    @Override
    CarRepository getCarRepository() {
        return context.getBean(MySqlCarRepository)
    }

    @Memoized
    @Override
    BasicTypesRepository getBasicTypeRepository() {
        return context.getBean(MySqlBasicTypesRepository)
    }

    @Memoized
    @Override
    EntityWithIdClassRepository getEntityWithIdClassRepository() {
        return context.getBean(MySqlEntityWithIdClassRepository)
    }

    @Memoized
    @Override
    EntityWithIdClass2Repository getEntityWithIdClass2Repository() {
        return context.getBean(MySqlEntityWithIdClass2Repository)
    }

    @Memoized
    @Override
    TimezoneBasicTypesRepository getTimezoneBasicTypeRepository() {
        return null
    }

    @Memoized
    @Override
    PageRepository getPageRepository() {
        return context.getBean(MySqlPageRepository)
    }

    @Memoized
    @Override
    ExampleEntityRepository getExampleEntityRepository() {
        return context.getBean(MySqlExampleEntityRepository)
    }

    @Override
    protected boolean skipCustomSchemaAndCatalogTest() {
        // INSERT command denied to user 'test'@'172.17.0.1' for table 'cars'
        return true
    }

    @Override
    protected boolean skipQueryByDataArray() {
        return true
    }
}
