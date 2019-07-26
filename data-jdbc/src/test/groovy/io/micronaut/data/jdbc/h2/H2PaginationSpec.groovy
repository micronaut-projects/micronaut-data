package io.micronaut.data.jdbc.h2

import io.micronaut.context.annotation.Property
import io.micronaut.data.tck.repositories.PersonRepository
import io.micronaut.data.tck.tests.AbstractPageSpec
import io.micronaut.test.annotation.MicronautTest
import spock.lang.Shared

import javax.inject.Inject

@MicronautTest
@Property(name = "datasources.default.name", value = "mydb")
@Property(name = "datasources.default.schema-generate", value = "CREATE_DROP")
@Property(name = "datasources.default.dialect", value = "H2")
class H2PaginationSpec extends AbstractPageSpec {
    @Inject
    @Shared
    H2PersonRepository pr

    @Override
    PersonRepository getPersonRepository() {
        return pr
    }

    @Override
    void init() {
    }
}
