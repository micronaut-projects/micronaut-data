package io.micronaut.data.jdbc.mariadb

import io.micronaut.context.ApplicationContext
import io.micronaut.data.jdbc.BasicTypes
import io.micronaut.data.jdbc.h2.H2CityRepository
import io.micronaut.data.jdbc.h2.H2CountryRepository
import io.micronaut.data.jdbc.h2.H2RegionRepository
import io.micronaut.data.jdbc.mysql.MySqlAuthorRepository
import io.micronaut.data.jdbc.mysql.MySqlBasicTypesRepository
import io.micronaut.data.jdbc.mysql.MySqlBookDtoRepository
import io.micronaut.data.jdbc.mysql.MySqlBookRepository
import io.micronaut.data.jdbc.mysql.MySqlCompanyRepository
import io.micronaut.data.jdbc.mysql.MySqlFaceRepository
import io.micronaut.data.jdbc.mysql.MySqlNoseRepository
import io.micronaut.data.jdbc.mysql.MySqlPersonRepository
import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.runtime.config.SchemaGenerate
import io.micronaut.data.tck.repositories.AuthorRepository
import io.micronaut.data.tck.repositories.BookDtoRepository
import io.micronaut.data.tck.repositories.BookRepository
import io.micronaut.data.tck.repositories.CityRepository
import io.micronaut.data.tck.repositories.CompanyRepository
import io.micronaut.data.tck.repositories.CountryRepository
import io.micronaut.data.tck.repositories.FaceRepository
import io.micronaut.data.tck.repositories.NoseRepository
import io.micronaut.data.tck.repositories.PersonRepository
import io.micronaut.data.tck.repositories.RegionRepository
import io.micronaut.data.tck.tests.AbstractRepositorySpec
import io.micronaut.test.annotation.MicronautTest
import org.testcontainers.containers.MariaDBContainer
import spock.lang.AutoCleanup
import spock.lang.Shared

@MicronautTest
class MariaRepositorySpec extends AbstractRepositorySpec {
    @Shared @AutoCleanup MariaDBContainer mariadb = new MariaDBContainer<>()
    @Shared @AutoCleanup ApplicationContext context

    @Override
    PersonRepository getPersonRepository() {
        return context.getBean(MySqlPersonRepository)
    }

    @Override
    BookRepository getBookRepository() {
        return context.getBean(MySqlBookRepository)
    }

    @Override
    AuthorRepository getAuthorRepository() {
        return context.getBean(MySqlAuthorRepository)
    }

    @Override
    CompanyRepository getCompanyRepository() {
        return context.getBean(MySqlCompanyRepository)
    }

    @Override
    BookDtoRepository getBookDtoRepository() {
        return context.getBean(MySqlBookDtoRepository)
    }

    @Override
    CountryRepository getCountryRepository() {
        return context.getBean(H2CountryRepository)
    }

    @Override
    CityRepository getCityRepository() {
        return context.getBean(H2CityRepository)
    }

    @Override
    RegionRepository getRegionRepository() {
        return context.getBean(H2RegionRepository)
    }

    @Override
    NoseRepository getNoseRepository() {
        return context.getBean(MySqlNoseRepository)
    }

    @Override
    FaceRepository getFaceRepository() {
        return context.getBean(MySqlFaceRepository)
    }

    @Override
    void init() {
        mariadb.start()
        context = ApplicationContext.run(
                "datasources.default.url":mariadb.getJdbcUrl(),
                "datasources.default.username":mariadb.getUsername(),
                "datasources.default.password":mariadb.getPassword(),
                "datasources.default.schema-generate": SchemaGenerate.CREATE,
                "datasources.default.dialect": Dialect.MYSQL
        )

    }

    void "test save and retrieve basic types"() {
        when: "we save a new book"
        def basicTypesRepo = context.getBean(MySqlBasicTypesRepository)
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
    
}

