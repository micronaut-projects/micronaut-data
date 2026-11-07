package io.micronaut.data.r2dbc.postgres.search

import groovy.transform.Memoized
import io.micronaut.data.r2dbc.postgres.PostgresTestPropertyProvider
import io.micronaut.data.model.search.SearchCapability
import io.micronaut.data.search.tck.AbstractTextSearchSpec
import io.micronaut.data.search.tck.repositories.BookSearchRepository

class PostgresTextSearchSpec extends AbstractTextSearchSpec implements PostgresTestPropertyProvider {

    @Memoized
    @Override
    BookSearchRepository getBookSearchRepository() {
        return context.getBean(PostgresBookSearchRepository)
    }

    @Override
    Set<SearchCapability> capabilities() {
        // pg_trgm adds fuzzy matching, with a fixed similarity threshold rather than an edit distance
        return EnumSet.of(SearchCapability.LANGUAGE_OVERRIDE, SearchCapability.PHRASE, SearchCapability.PREFIX, SearchCapability.FUZZY, SearchCapability.FIELD_SCOPE,
                SearchCapability.HIGHLIGHT, SearchCapability.VECTOR, SearchCapability.MIN_SCORE)
    }

    @Override
    List<String> packages() {
        return [getClass().package.name, "io.micronaut.data.search.tck.entities"]
    }
}
