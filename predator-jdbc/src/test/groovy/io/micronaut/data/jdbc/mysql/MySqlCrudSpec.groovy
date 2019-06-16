package io.micronaut.data.jdbc.mysql

import groovy.sql.Sql
import io.micronaut.context.ApplicationContext
import io.micronaut.data.jdbc.h2.H2Util
import io.micronaut.data.model.PersistentEntity
import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.model.query.builder.sql.SqlQueryBuilder
import io.micronaut.data.model.runtime.RuntimePersistentEntity
import io.micronaut.data.tck.entities.Author
import io.micronaut.data.tck.entities.Book
import io.micronaut.data.tck.entities.Person
import io.micronaut.data.tck.repositories.PersonRepository
import io.micronaut.data.tck.tests.AbstractCrudSpec
import org.testcontainers.containers.MySQLContainer
import spock.lang.AutoCleanup
import spock.lang.Shared

import javax.sql.DataSource

class MySqlCrudSpec extends AbstractCrudSpec {
    @Shared public MySQLContainer mysql = new MySQLContainer<>()
    @Shared @AutoCleanup ApplicationContext context

    @Override
    PersonRepository getCrudRepository() {
        return context.getBean(MySqlPersonRepository)
    }

    @Override
    void init() {
        mysql.start()
        context = ApplicationContext.run(
                "datasources.default.url":mysql.getJdbcUrl(),
                "datasources.default.username":"root",
                "datasources.default.password":"test",
        )
        def dataSource = context.getBean(DataSource)
        def entity = PersistentEntity.of(Person)
        def sql = new SqlQueryBuilder(Dialect.MYSQL).buildCreateTables(entity)
        new Sql(dataSource).executeUpdate(sql)
    }
}
