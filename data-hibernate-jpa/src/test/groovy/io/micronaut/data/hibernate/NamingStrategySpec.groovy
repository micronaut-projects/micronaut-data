package io.micronaut.data.hibernate

import groovy.sql.Sql
import io.micronaut.context.annotation.Property
import io.micronaut.test.annotation.MicronautTest
import spock.lang.Specification

import javax.inject.Inject
import javax.sql.DataSource

@MicronautTest(packages = "io.micronaut.data.tck.entities")
@Property(name = "datasources.default.name", value = "mydb")
@Property(name = 'jpa.default.properties.hibernate.hbm2ddl.auto', value = 'create-drop')
class NamingStrategySpec extends Specification {

    @Inject
    DataSource dataSource

    void "test the default naming strategy uses underscore lower case"() {
        given:
        Sql sql = new Sql(dataSource)

        expect:
        sql.execute('select zip_code from publisher')
    }
}
