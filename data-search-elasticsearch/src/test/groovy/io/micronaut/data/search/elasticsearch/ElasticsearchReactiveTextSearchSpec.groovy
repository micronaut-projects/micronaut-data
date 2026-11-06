package io.micronaut.data.search.elasticsearch

import groovy.transform.Memoized
import io.micronaut.data.search.tck.AbstractReactiveTextSearchSpec
import io.micronaut.data.search.tck.repositories.ReactorBookSearchRepository
import io.micronaut.test.support.TestPropertyProvider

class ElasticsearchReactiveTextSearchSpec extends AbstractReactiveTextSearchSpec implements TestPropertyProvider {

    @Override
    Map<String, String> getProperties() {
        return ElasticsearchTestContainer.properties()
    }

    @Memoized
    @Override
    ReactorBookSearchRepository getBookSearchRepository() {
        return context.getBean(ElasticsearchReactorBookSearchRepository)
    }
}
