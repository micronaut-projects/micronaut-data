package io.micronaut.data.jdbc.h2


import io.micronaut.context.annotation.Property
import io.micronaut.data.tck.entities.Person
import io.micronaut.data.tck.repositories.PersonRepository
import io.micronaut.data.tck.tests.AbstractCrudSpec
import io.micronaut.test.annotation.MicronautTest
import spock.lang.Shared

import javax.inject.Inject
import javax.sql.DataSource

@MicronautTest(rollback = false)
@Property(name = "datasources.default.name", value = "mydb")
class H2CrudSpec extends AbstractCrudSpec {

    @Inject
    @Shared
    PersonRepository personRepository

    @Inject
    @Shared
    DataSource dataSource

    @Override
    PersonRepository getCrudRepository() {
        return personRepository
    }

    void init() {
        H2Util.createTables(dataSource, Person)
    }

}
