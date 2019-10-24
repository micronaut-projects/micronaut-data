package io.micronaut.data.jdbc.oraclexe

import io.micronaut.context.ApplicationContext
import io.micronaut.data.jdbc.BasicTypes
import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.runtime.config.SchemaGenerate
import io.micronaut.data.tck.entities.Author
import io.micronaut.data.tck.entities.Book
import io.micronaut.data.tck.entities.Person
import io.micronaut.data.tck.repositories.*
import io.micronaut.data.tck.tests.AbstractRepositorySpec
import org.testcontainers.containers.OracleContainer
import spock.lang.AutoCleanup
import spock.lang.Shared

class OracleXERepositorySpec extends AbstractRepositorySpec {

    @Shared
    @AutoCleanup
    OracleContainer oracleContainer = new OracleContainer("wnameless/oracle-xe-11g-r2")
    @Shared
    @AutoCleanup
    ApplicationContext context

    @Override
    boolean isOracle() {
        return true
    }

    @Override
    PersonRepository getPersonRepository() {
        return context.getBean(OracleXEPersonRepository)
    }

    @Override
    BookRepository getBookRepository() {
        return context.getBean(OracleXEBookRepository)
    }

    @Override
    AuthorRepository getAuthorRepository() {
        return context.getBean(OracleXEAuthorRepository)
    }

    @Override
    CompanyRepository getCompanyRepository() {
        return context.getBean(OracleXECompanyRepository)
    }

    @Override
    BookDtoRepository getBookDtoRepository() {
        return context.getBean(OracleXEBookDtoRepository)
    }

    @Override
    CountryRepository getCountryRepository() {
        return context.getBean(OracleXECountryRepository)
    }

    @Override
    CityRepository getCityRepository() {
        return context.getBean(OracleXECityRepository)
    }

    @Override
    RegionRepository getRegionRepository() {
        return context.getBean(OracleXERegionRepository)
    }

    @Override
    NoseRepository getNoseRepository() {
        return context.getBean(OracleXENoseRepository)
    }

    @Override
    FaceRepository getFaceRepository() {
        return context.getBean(OracleXEFaceRepository)
    }

    @Override
    void init() {
        oracleContainer.start()
        context = ApplicationContext.run(
                "datasources.default.url": oracleContainer.getJdbcUrl(),
                "datasources.default.username": oracleContainer.getUsername(),
                "datasources.default.password": oracleContainer.getPassword(),
                "datasources.default.schema-generate": SchemaGenerate.CREATE,
                "datasources.default.dialect": Dialect.ORACLE
        )

    }

    @Override
    protected void setupData() {
        personRepository.saveAll([
                new Person(name: "Jeff"),
                new Person(name: "James")
        ])
        bookRepository.save(new Book(title: "Anonymous", totalPages: 400))
        // book without an author
        bookRepository.setupData()
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
        def basicTypesRepo = context.getBean(OracleXEBasicTypesRepository)
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

    }

}