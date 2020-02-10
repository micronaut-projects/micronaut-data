package io.micronaut.data.cql

import io.micronaut.context.ApplicationContext
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
import org.cassandraunit.utils.EmbeddedCassandraServerHelper
import spock.lang.AutoCleanup
import spock.lang.Shared

class CassandraRepositorySpec  extends AbstractRepositorySpec{
    @Shared @AutoCleanup ApplicationContext context

    @Override
    PersonRepository getPersonRepository() {
        return context.getBean(CqlPersonRepository)
    }

    @Override
    BookRepository getBookRepository() {
        return context.getBean(CqlBookRepository)
    }

    @Override
    AuthorRepository getAuthorRepository() {
        return context.getBean(CqlAuthorRepository)
    }

    @Override
    CompanyRepository getCompanyRepository() {
        return context.getBean(CqlCompanyRepository)
    }

    @Override
    BookDtoRepository getBookDtoRepository() {
        return context.getBean(CqlBookDtoRepository)
    }

    @Override
    CountryRepository getCountryRepository() {
        return context.getBean(CqlBookDtoRepository)
    }

    @Override
    CityRepository getCityRepository() {
        return context.getBean(CqlCityRepository)
    }

    @Override
    RegionRepository getRegionRepository() {
        return context.getBean(CqlRegionRepository)
    }

    @Override
    NoseRepository getNoseRepository() {
        return context.getBean(CqlNoseRepository)
    }

    @Override
    FaceRepository getFaceRepository() {
        return context.getBean(CqlFaceRepository)
    }

    @Override
    void init() {
        EmbeddedCassandraServerHelper.startEmbeddedCassandra();
        context = ApplicationContext.run(
            "cassandra.default.basic.contact-points"                        : ["localhost:9142"],
            "cassandra.default.advanced.metadata.schema.enabled"            : false,
            "cassandra.default.basic.load-balancing-policy.local-datacenter": "datacenter1"
        )
    }

}
