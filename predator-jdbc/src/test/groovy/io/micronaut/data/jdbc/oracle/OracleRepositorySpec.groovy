package io.micronaut.data.jdbc.oracle

import io.micronaut.context.ApplicationContext
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
import org.testcontainers.containers.OracleContainer
import spock.lang.AutoCleanup
import spock.lang.Shared

class OracleRepositorySpec extends AbstractRepositorySpec {
    @Shared @AutoCleanup OracleContainer oracleServer = new OracleContainer("???")
    @Shared @AutoCleanup ApplicationContext context

    @Override
    PersonRepository getPersonRepository() {
        context.getBean(OraclePersonRepository)
    }

    @Override
    BookRepository getBookRepository() {
        return context.getBean(OracleBookRepository)
    }

    @Override
    AuthorRepository getAuthorRepository() {
        return context.getBean(OracleAuthorRepository)
    }

    @Override
    CompanyRepository getCompanyRepository() {
        return context.getBean(OracleCompanyRepository)
    }

    @Override
    BookDtoRepository getBookDtoRepository() {
        return context.getBean(OracleBookDtoRepository)
    }

    @Override
    CountryRepository getCountryRepository() {
        return context.getBean(CountryRepository)
    }

    @Override
    CityRepository getCityRepository() {
        return context.getBean(CityRepository)
    }

    @Override
    RegionRepository getRegionRepository() {
        return context.getBean(RegionRepository)
    }

    @Override
    NoseRepository getNoseRepository() {
        return context.getBean(OracleNoseRepository)
    }

    @Override
    FaceRepository getFaceRepository() {
        return context.getBean(OracleFaceRepository)
    }

    @Override
    void init() {
        oracleServer.start()
        context = ApplicationContext.run(
                "datasources.default.url":oracleServer.getJdbcUrl(),
                "datasources.default.username":oracleServer.getUsername(),
                "datasources.default.password":oracleServer.getPassword(),
                "datasources.default.schema-generate": SchemaGenerate.CREATE,
                "datasources.default.dialect": Dialect.ORACLE
        )
    }
}
