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
@Property(name = "datasources.default.schema-generate", value = "CREATE_DROP")
@Property(name = "datasources.default.dialect", value = "H2")
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
