package io.micronaut.data.jdbc.postgres.search

import groovy.transform.Memoized
import io.micronaut.data.jdbc.postgres.PostgresTestPropertyProvider
import io.micronaut.data.search.tck.AbstractTextSearchSpec
import io.micronaut.data.search.tck.repositories.BookSearchRepository

class PostgresTextSearchSpec extends AbstractTextSearchSpec implements PostgresTestPropertyProvider {

    @Memoized
    @Override
    BookSearchRepository getBookSearchRepository() {
        return context.getBean(PostgresBookSearchRepository)
    }

    @Override
    List<String> packages() {
        return [getClass().package.name, "io.micronaut.data.search.tck.entities"]
    }
}
