package io.micronaut.data.jdbc.postgres


import io.micronaut.context.ApplicationContext
import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.runtime.config.SchemaGenerate
import io.micronaut.data.tck.repositories.AuthorRepository
import io.micronaut.data.tck.repositories.BookRepository
import io.micronaut.data.tck.repositories.PersonRepository
import io.micronaut.data.tck.tests.AbstractRepositorySpec
import org.testcontainers.containers.PostgreSQLContainer
import spock.lang.AutoCleanup
import spock.lang.Shared

class PostgresRepositorySpec extends AbstractRepositorySpec {
    @Shared @AutoCleanup PostgreSQLContainer postgres = new PostgreSQLContainer<>("postgres:10")
        .withDatabaseName("test-database")
        .withUsername("test")
        .withPassword("test")
    @Shared @AutoCleanup ApplicationContext context

    @Override
    PersonRepository getPersonRepository() {
        return context.getBean(PostgresPersonRepository)
    }

    @Override
    BookRepository getBookRepository() {
        return context.getBean(PostgresBookRepository)
    }

    @Override
    AuthorRepository getAuthorRepository() {
        return context.getBean(PostgresAuthorRepository)
    }

    @Override
    void init() {
        postgres.start()
        context = ApplicationContext.run(
                "datasources.default.url":postgres.getJdbcUrl(),
                "datasources.default.username":postgres.getUsername(),
                "datasources.default.password":postgres.getPassword(),
                "datasources.default.schema-generate": SchemaGenerate.CREATE,
                "datasources.default.dialect": Dialect.POSTGRES
        )
    }
}