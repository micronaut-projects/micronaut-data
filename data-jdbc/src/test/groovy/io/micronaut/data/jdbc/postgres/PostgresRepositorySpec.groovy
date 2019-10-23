/*
 * Copyright 2017-2019 original authors
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

import io.micronaut.context.ApplicationContext
import io.micronaut.data.jdbc.BasicTypes
import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.runtime.config.SchemaGenerate
import io.micronaut.data.tck.entities.Author
import io.micronaut.data.tck.entities.Car
import io.micronaut.data.tck.repositories.*
import io.micronaut.data.tck.tests.AbstractRepositorySpec
import org.testcontainers.containers.PostgreSQLContainer
import spock.lang.AutoCleanup
import spock.lang.Shared

class PostgresRepositorySpec extends AbstractRepositorySpec {
    @Shared @AutoCleanup PostgreSQLContainer postgres = new PostgreSQLContainer<>("postgres:10")
        .withDatabaseName("test-database")
        .withUsername("test")
        .withPassword("test")
    @Shared @AutoCleanup ApplicationContext context

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
    void init() {
        postgres.start()
        context = ApplicationContext.run(
                "datasources.default.url":postgres.getJdbcUrl(),
                "datasources.default.username":postgres.getUsername(),
                "datasources.default.password":postgres.getPassword(),
                "datasources.default.schema-generate": SchemaGenerate.CREATE,
                "datasources.default.dialect": Dialect.POSTGRES
        )
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
        // stored as a DATE type without time
//        retrievedBook.date == book.date

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