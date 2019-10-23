package io.micronaut.data.jdbc.oraclexe

import io.micronaut.context.ApplicationContext
import io.micronaut.data.jdbc.h2.H2CityRepository
import io.micronaut.data.jdbc.h2.H2CountryRepository
import io.micronaut.data.jdbc.h2.H2RegionRepository
import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.runtime.config.SchemaGenerate
import io.micronaut.data.tck.entities.Book
import io.micronaut.data.tck.entities.Person
import io.micronaut.data.tck.repositories.*
import io.micronaut.data.tck.tests.AbstractRepositorySpec
import org.testcontainers.containers.OracleContainer
import spock.lang.AutoCleanup
import spock.lang.Ignore
import spock.lang.Shared

@Ignore("Currently we support Oracle 12c syntax, but the newest version of Oracle XE is 11g")
// there is currently no containerized version of Oracle that can
// be used with test containers
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

}