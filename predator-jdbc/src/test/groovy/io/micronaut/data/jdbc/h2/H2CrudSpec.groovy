package io.micronaut.data.jdbc.h2


import io.micronaut.context.annotation.Property
import io.micronaut.data.tck.entities.Person
import io.micronaut.data.tck.tests.AbstractCrudSpec
import io.micronaut.test.annotation.MicronautTest
import spock.lang.Shared

import javax.inject.Inject
import javax.sql.DataSource

@MicronautTest(rollback = false)
@Property(name = "datasources.default.name", value = "mydb")
@Property(name = "datasources.default.schema-generate", value = "CREATE_DROP")
@Property(name = "datasources.default.dialect", value = "H2")
class H2CrudSpec extends AbstractCrudSpec {

    @Inject
    @Shared
    H2PersonRepository personRepository

    @Inject
    @Shared
    DataSource dataSource

    @Override
    H2PersonRepository getCrudRepository() {
        return personRepository
    }

    void init() {
    }

}
