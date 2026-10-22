package io.micronaut.data.r2dbc.oraclexe.search

import groovy.transform.Memoized
import io.micronaut.data.model.search.SearchCapability
import io.micronaut.data.r2dbc.oraclexe.Oracle23TestPropertyProvider
import io.micronaut.data.search.tck.AbstractTextSearchSpec
import io.micronaut.data.search.tck.repositories.BookSearchRepository

class OracleTextSearchSpec extends AbstractTextSearchSpec implements Oracle23TestPropertyProvider {

    @Memoized
    @Override
    BookSearchRepository getBookSearchRepository() {
        return context.getBean(OracleBookSearchRepository)
    }

    @Override
    List<String> packages() {
        return [getClass().package.name, "io.micronaut.data.search.tck.entities"]
    }

    @Override
    Set<SearchCapability> capabilities() {
        return EnumSet.of(SearchCapability.PHRASE, SearchCapability.PREFIX, SearchCapability.FUZZY, SearchCapability.FIELD_SCOPE, SearchCapability.HIGHLIGHT)
    }

    @Override
    boolean supportsStemming() {
        return false
    }
}
