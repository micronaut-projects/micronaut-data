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
package io.micronaut.data.jdbc.sqlserver


import io.micronaut.data.jdbc.BasicTypes
import io.micronaut.data.tck.repositories.AuthorRepository
import io.micronaut.data.tck.repositories.BookDtoRepository
import io.micronaut.data.tck.repositories.BookRepository
import io.micronaut.data.tck.repositories.CityRepository
import io.micronaut.data.tck.repositories.CompanyRepository
import io.micronaut.data.tck.repositories.CountryRegionCityRepository
import io.micronaut.data.tck.repositories.CountryRepository
import io.micronaut.data.tck.repositories.FaceRepository
import io.micronaut.data.tck.repositories.FoodRepository
import io.micronaut.data.tck.repositories.MealRepository
import io.micronaut.data.tck.repositories.NoseRepository
import io.micronaut.data.tck.repositories.PersonRepository
import io.micronaut.data.tck.repositories.RegionRepository
import io.micronaut.data.tck.repositories.RoleRepository
import io.micronaut.data.tck.repositories.UserRepository
import io.micronaut.data.tck.repositories.UserRoleRepository
import io.micronaut.data.tck.tests.AbstractRepositorySpec

class SqlServerRepositorySpec extends AbstractRepositorySpec implements MSSQLTestPropertyProvider {

    @Override
    PersonRepository getPersonRepository() {
        return context.getBean(MSSQLPersonRepository)
    }

    @Override
    BookRepository getBookRepository() {
        return context.getBean(MSBookRepository)
    }

    @Override
    AuthorRepository getAuthorRepository() {
        return context.getBean(MSAuthorRepository)
    }

    @Override
    CompanyRepository getCompanyRepository() {
        return context.getBean(MSCompanyRepository)
    }

    @Override
    BookDtoRepository getBookDtoRepository() {
        return context.getBean(MSBookDtoRepository)
    }

    @Override
    CountryRepository getCountryRepository() {
        return context.getBean(MSCountryRepository)
    }

    @Override
    CityRepository getCityRepository() {
        return context.getBean(MSCityRepository)
    }

    @Override
    RegionRepository getRegionRepository() {
        return context.getBean(MSRegionRepository)
    }

    @Override
    NoseRepository getNoseRepository() {
        return context.getBean(MSNoseRepository)
    }

    @Override
    FaceRepository getFaceRepository() {
        return context.getBean(MSFaceRepository)
    }

    @Override
    CountryRegionCityRepository getCountryRegionCityRepository() {
        return context.getBean(MSCountryRegionCityRepository)
    }

    @Override
    UserRoleRepository getUserRoleRepository() {
        return context.getBean(MSUserRoleRepository)
    }

    @Override
    RoleRepository getRoleRepository() {
        return context.getBean(MSRoleRepository)
    }

    @Override
    UserRepository getUserRepository() {
        return context.getBean(MSUserRepository)
    }

    @Override
    MealRepository getMealRepository() {
        return context.getBean(MSMealRepository)
    }

    @Override
    FoodRepository getFoodRepository() {
        return context.getBean(MSFoodRepository)
    }

    void "test save and retrieve basic types"() {
        when: "we save a new book"
        def basicTypesRepo = context.getBean(MSBasicTypesRepository)
        def book = basicTypesRepo.save(new BasicTypes())

        then: "The ID is assigned"
        book.myId != null

        when:"A book is found"
        def retrievedBook = basicTypesRepo.findById(book.myId).orElse(null)

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
//        retrievedBook.date == book.date

        cleanup:
        basicTypesRepo.deleteAll()

    }
}
