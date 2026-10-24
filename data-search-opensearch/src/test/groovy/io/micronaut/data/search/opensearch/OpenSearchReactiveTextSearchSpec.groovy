package io.micronaut.data.search.opensearch

import groovy.transform.Memoized
import io.micronaut.data.search.tck.AbstractReactiveTextSearchSpec
import io.micronaut.data.search.tck.repositories.AsyncBookSearchRepository
import io.micronaut.data.search.tck.repositories.ReactorBookSearchRepository
import io.micronaut.test.support.TestPropertyProvider

class OpenSearchReactiveTextSearchSpec extends AbstractReactiveTextSearchSpec implements TestPropertyProvider {

    @Override
    Map<String, String> getProperties() {
        return OpenSearchTestContainer.properties()
    }

    @Memoized
    @Override
    ReactorBookSearchRepository getBookSearchRepository() {
        return context.getBean(OpenSearchReactorBookSearchRepository)
    }

    @Memoized
    @Override
    AsyncBookSearchRepository getAsyncBookSearchRepository() {
        return context.getBean(OpenSearchAsyncBookSearchRepository)
    }
}
