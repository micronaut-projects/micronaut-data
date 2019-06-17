package io.micronaut.data.jdbc.sqlserver

import groovy.sql.Sql
import io.micronaut.context.ApplicationContext
import io.micronaut.data.model.PersistentEntity
import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.model.query.builder.sql.SqlQueryBuilder
import io.micronaut.data.tck.entities.Person
import io.micronaut.data.tck.repositories.PersonRepository
import io.micronaut.data.tck.tests.AbstractCrudSpec
import org.testcontainers.containers.MSSQLServerContainer
import spock.lang.AutoCleanup
import spock.lang.Shared

import javax.sql.DataSource

class SqlServerCrudSpec extends AbstractCrudSpec {
    @Shared @AutoCleanup MSSQLServerContainer sqlServer = new MSSQLServerContainer<>()
    @Shared @AutoCleanup ApplicationContext context

    @Override
    PersonRepository getCrudRepository() {
        return context.getBean(MSSQLPersonRepository)
    }

    @Override
    void init() {
        sqlServer.start()
        context = ApplicationContext.run(
                "datasources.default.url":sqlServer.getJdbcUrl(),
                "datasources.default.username":sqlServer.getUsername(),
                "datasources.default.password":sqlServer.getPassword(),
        )
        def dataSource = context.getBean(DataSource)
        def entity = PersistentEntity.of(Person)
        def sql = new SqlQueryBuilder(Dialect.SQL_SERVER).buildCreateTables(entity)
        new Sql(dataSource).executeUpdate(sql)
    }
}
