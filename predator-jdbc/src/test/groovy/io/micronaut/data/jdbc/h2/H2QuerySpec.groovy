package io.micronaut.data.jdbc.h2

import groovy.sql.Sql
import io.micronaut.context.annotation.Property
import io.micronaut.data.tck.entities.Author
import io.micronaut.data.tck.entities.Book
import io.micronaut.data.tck.tests.QuerySpec
import io.micronaut.test.annotation.MicronautTest
import spock.lang.Shared

import javax.inject.Inject
import javax.sql.DataSource

@MicronautTest
@Property(name = "datasources.default.name", value = "mydb")
class H2QuerySpec extends QuerySpec {
    @Shared
    @Inject
    BookRepository br
    @Shared
    @Inject
    AuthorRepository ar

    @Inject
    @Shared
    DataSource dataSource

    @Override
    void init() {
        H2Util.createTables(dataSource, Book, Author)
    }

    @Override
    BookRepository getBookRepository() {
        return br
    }

    @Override
    AuthorRepository getAuthorRepository() {
        return ar
    }
}
