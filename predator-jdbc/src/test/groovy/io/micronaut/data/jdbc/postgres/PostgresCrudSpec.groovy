package io.micronaut.data.jdbc.postgres

import groovy.sql.Sql
import io.micronaut.context.ApplicationContext
import io.micronaut.data.model.PersistentEntity
import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.model.query.builder.sql.SqlQueryBuilder
import io.micronaut.data.tck.entities.Person
import io.micronaut.data.tck.repositories.PersonRepository
import io.micronaut.data.tck.tests.AbstractCrudSpec
import org.testcontainers.containers.PostgreSQLContainer
import spock.lang.AutoCleanup
import spock.lang.Shared

import javax.sql.DataSource

class PostgresCrudSpec extends AbstractCrudSpec {
    @Shared @AutoCleanup PostgreSQLContainer postgres = new PostgreSQLContainer<>("postgres:10")
        .withDatabaseName("test-database")
        .withUsername("test")
        .withPassword("test")
    @Shared @AutoCleanup ApplicationContext context

    @Override
    PersonRepository getCrudRepository() {
        return context.getBean(PostgresPersonRepository)
    }

    @Override
    void init() {
        postgres.start()
        context = ApplicationContext.run(
                "datasources.default.url":postgres.getJdbcUrl(),
                "datasources.default.username":"test",
                "datasources.default.password":"test",
        )
        def dataSource = context.getBean(DataSource)
        def entity = PersistentEntity.of(Person)
        def sql = new SqlQueryBuilder(Dialect.POSTGRES).buildCreateTables(entity)
        new Sql(dataSource).executeUpdate(sql)
    }
}