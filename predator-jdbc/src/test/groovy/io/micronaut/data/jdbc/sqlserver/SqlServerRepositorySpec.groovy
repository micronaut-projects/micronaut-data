package io.micronaut.data.jdbc.sqlserver


import io.micronaut.context.ApplicationContext
import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.runtime.config.SchemaGenerate
import io.micronaut.data.tck.repositories.AuthorRepository
import io.micronaut.data.tck.repositories.BookRepository
import io.micronaut.data.tck.repositories.PersonRepository
import io.micronaut.data.tck.tests.AbstractRepositorySpec
import org.testcontainers.containers.MSSQLServerContainer
import spock.lang.AutoCleanup
import spock.lang.Shared

class SqlServerRepositorySpec extends AbstractRepositorySpec {
    @Shared @AutoCleanup MSSQLServerContainer sqlServer = new MSSQLServerContainer<>()
    @Shared @AutoCleanup ApplicationContext context

    @Override
    PersonRepository getPersonRepository() {
        return context.getBean(MSSQLPersonRepository)
    }

    @Override
    BookRepository getBookRepository() {
        return context.getBean(MSBookRepository)
    }

    @Override
    AuthorRepository getAuthorRepository() {
        return context.getBean(MSAuthorRepository)
    }

    @Override
    void init() {
        sqlServer.start()
        context = ApplicationContext.run(
                "datasources.default.url":sqlServer.getJdbcUrl(),
                "datasources.default.username":sqlServer.getUsername(),
                "datasources.default.password":sqlServer.getPassword(),
                "datasources.default.schema-generate": SchemaGenerate.CREATE,
                "datasources.default.dialect": Dialect.SQL_SERVER
        )
    }
}
