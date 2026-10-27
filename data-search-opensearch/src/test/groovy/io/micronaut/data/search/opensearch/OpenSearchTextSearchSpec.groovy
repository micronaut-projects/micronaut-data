package io.micronaut.data.search.opensearch

import groovy.transform.Memoized
import io.micronaut.data.model.search.SearchCapability
import io.micronaut.data.search.tck.AbstractTextSearchSpec
import io.micronaut.data.search.tck.repositories.BookSearchRepository
import io.micronaut.test.support.TestPropertyProvider

class OpenSearchTextSearchSpec extends AbstractTextSearchSpec implements TestPropertyProvider {

    @Override
    Map<String, String> getProperties() {
        return OpenSearchTestContainer.properties()
    }

    @Memoized
    @Override
    BookSearchRepository getBookSearchRepository() {
        return context.getBean(OpenSearchBookSearchRepository)
    }

    @Override
    Set<SearchCapability> capabilities() {
        return EnumSet.of(SearchCapability.PHRASE, SearchCapability.PREFIX, SearchCapability.FUZZY, SearchCapability.FIELD_SCOPE, SearchCapability.BOOST)
    }
}
