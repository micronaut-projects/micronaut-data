package io.micronaut.data.jdbc.h2


import io.micronaut.context.annotation.Property
import io.micronaut.data.tck.entities.Author
import io.micronaut.data.tck.entities.Book
import io.micronaut.data.tck.tests.AbstractQuerySpec
import io.micronaut.test.annotation.MicronautTest
import spock.lang.Shared

import javax.inject.Inject
import javax.sql.DataSource

@MicronautTest
@Property(name = "datasources.default.name", value = "mydb")
class H2QuerySpec extends AbstractQuerySpec {
    @Shared
    @Inject
    H2BookRepository br
    @Shared
    @Inject
    H2AuthorRepository ar

    @Inject
    @Shared
    DataSource dataSource

    @Override
    void init() {
        H2Util.createTables(dataSource, Book, Author)
    }

    @Override
    H2BookRepository getBookRepository() {
        return br
    }

    @Override
    H2AuthorRepository getAuthorRepository() {
        return ar
    }
}
