package io.micronaut.data.jdbc.h2


import io.micronaut.context.annotation.Property
import io.micronaut.data.tck.repositories.AuthorRepository
import io.micronaut.data.tck.repositories.BookDtoRepository
import io.micronaut.data.tck.repositories.BookRepository
import io.micronaut.data.tck.repositories.CityRepository
import io.micronaut.data.tck.repositories.CompanyRepository
import io.micronaut.data.tck.repositories.CountryRepository
import io.micronaut.data.tck.repositories.RegionRepository
import io.micronaut.data.tck.tests.AbstractRepositorySpec
import io.micronaut.test.annotation.MicronautTest
import spock.lang.Shared

import javax.inject.Inject
import javax.sql.DataSource

@MicronautTest(rollback = false)
@Property(name = "datasources.default.name", value = "mydb")
@Property(name = "datasources.default.schema-generate", value = "CREATE_DROP")
@Property(name = "datasources.default.dialect", value = "H2")
class H2RepositorySpec extends AbstractRepositorySpec {

    @Inject
    @Shared
    H2PersonRepository pr

    @Inject
    @Shared
    H2BookRepository br

    @Inject
    @Shared
    H2AuthorRepository ar

    @Inject
    @Shared
    H2CompanyRepository cr

    @Inject
    @Shared
    H2BookDtoRepository dto

    @Inject
    @Shared
    DataSource dataSource

    @Inject
    @Shared
    H2CountryRepository countryr

    @Inject
    @Shared
    H2CityRepository cityr

    @Inject
    @Shared
    H2RegionRepository regr

    @Override
    H2PersonRepository getPersonRepository() {
        return pr
    }

    @Override
    BookRepository getBookRepository() {
        return br
    }

    @Override
    AuthorRepository getAuthorRepository() {
        return ar
    }

    @Override
    CompanyRepository getCompanyRepository() {
        return cr
    }

    @Override
    BookDtoRepository getBookDtoRepository() {
        return dto
    }

    @Override
    CountryRepository getCountryRepository() {
        return countryr
    }

    @Override
    CityRepository getCityRepository() {
        return cityr
    }

    @Override
    RegionRepository getRegionRepository() {
        return regr
    }

    void init() {
    }

    void "test SQL mapping function"() {
        when:"using a function that maps a single value"
        def book = ar.testReadSingleProperty("The Stand", 700)

        then:"The result is correct"
        book != null
        book.author.name == 'Stephen King'

        when:"using a function that maps an associated entity value"
        book = ar.testReadAssociatedEntity("The Stand", 700)

        then:"The result is correct"
        book != null
        book.author.name == 'Stephen King'
        book.author.id

        when:"using a function that maps a DTO"
        book = ar.testReadDTO("The Stand", 700)

        then:"The result is correct"
        book != null
        book.author.name == 'Stephen King'
    }
}
