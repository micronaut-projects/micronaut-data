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


import io.micronaut.data.jdbc.BasicTypes
import io.micronaut.data.tck.repositories.FoodRepository
import io.micronaut.data.tck.repositories.MealRepository
import io.micronaut.data.tck.repositories.UserRepository
import io.micronaut.data.tck.entities.Author
import io.micronaut.data.tck.entities.Car
import io.micronaut.data.tck.repositories.BookDtoRepository
import io.micronaut.data.tck.repositories.BookRepository
import io.micronaut.data.tck.repositories.CityRepository
import io.micronaut.data.tck.repositories.CompanyRepository
import io.micronaut.data.tck.repositories.CountryRegionCityRepository
import io.micronaut.data.tck.repositories.CountryRepository
import io.micronaut.data.tck.repositories.FaceRepository
import io.micronaut.data.tck.repositories.NoseRepository
import io.micronaut.data.tck.repositories.PersonRepository
import io.micronaut.data.tck.repositories.RegionRepository
import io.micronaut.data.tck.repositories.RoleRepository
import io.micronaut.data.tck.repositories.UserRoleRepository
import io.micronaut.data.tck.tests.AbstractRepositorySpec
import spock.lang.IgnoreIf

class PostgresRepositorySpec extends AbstractRepositorySpec implements PostgresTestPropertyProvider {
    
    @Override
    PersonRepository getPersonRepository() {
        return context.getBean(PostgresPersonRepository)
    }

    @Override
    BookRepository getBookRepository() {
        return context.getBean(PostgresBookRepository)
    }

    @Override
    PostgresAuthorRepository getAuthorRepository() {
        return context.getBean(PostgresAuthorRepository)
    }

    @Override
    CompanyRepository getCompanyRepository() {
        return context.getBean(PostgresCompanyRepository)
    }

    @Override
    BookDtoRepository getBookDtoRepository() {
        return context.getBean(PostgresBookDtoRepository)
    }

    @Override
    CountryRepository getCountryRepository() {
        return context.getBean(PostgresCountryRepository)
    }

    @Override
    CityRepository getCityRepository() {
        return context.getBean(PostgresCityRepository)
    }

    @Override
    RegionRepository getRegionRepository() {
        return context.getBean(PostgresRegionRepository)
    }

    @Override
    NoseRepository getNoseRepository() {
        return context.getBean(PostgresNoseRepository)
    }

    @Override
    FaceRepository getFaceRepository() {
        return context.getBean(PostgresFaceRepository)
    }

    @Override
    CountryRegionCityRepository getCountryRegionCityRepository() {
        return context.getBean(PostgresCountryRegionCityRepository)
    }

    @Override
    UserRoleRepository getUserRoleRepository() {
        return context.getBean(PostgresUserRoleRepository)
    }

    @Override
    RoleRepository getRoleRepository() {
        return context.getBean(PostgresRoleRepository)
    }

    @Override
    UserRepository getUserRepository() {
        return context.getBean(PostgresUserRepository)
    }

    @Override
    MealRepository getMealRepository() {
        return context.getBean(PostgresMealRepository)
    }

    @Override
    FoodRepository getFoodRepository() {
        return context.getBean(PostgresFoodRepository)
    }

    void "test save and fetch author with no books"() {

        given:
        def author = new Author(name: "Some Dude")
        authorRepository.save(author)

        author = authorRepository.queryByName("Some Dude")

        expect:
        author.books.size() == 0

        cleanup:
        authorRepository.deleteById(author.id)
    }

    @IgnoreIf({ jvm.isJava15Compatible() })
    void "test save and retrieve basic types"() {
        when: "we save a new book"
        def basicTypesRepo = context.getBean(PostgresBasicTypesRepository)
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

        when: "we update the book"
        book = basicTypesRepo.update(retrievedBook)

        then: "The ID is assigned"
        book.myId != null

        when:"A book is found"
        retrievedBook = basicTypesRepo.findById(book.myId).orElse(null)

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

        cleanup:
        basicTypesRepo.deleteAll()

    }
    void "test CRUD with custom schema and catalog"() {
        given:
        PostgresCarRepository carRepo = context.getBean(PostgresCarRepository)
        when:
        def a5 = carRepo.save(new Car(name: "A5"))

        then:
        a5.id


        when:
        a5 = carRepo.findById(a5.id).orElse(null)
        carRepo.getById(a5.id).parts.size() == 0
        
        then:
        a5.id
        a5.name == 'A5'

        when:"an update happens"
        carRepo.update(a5.id, "A6")
        a5 = carRepo.findById(a5.id).orElse(null)

        then:"the updated worked"
        a5.name == 'A6'

        when:"A deleted"
        carRepo.deleteById(a5.id)

        then:"It was deleted"
        !carRepo.findById(a5.id).isPresent()
    }
}
