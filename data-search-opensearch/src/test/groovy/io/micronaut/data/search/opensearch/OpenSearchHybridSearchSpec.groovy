package io.micronaut.data.search.opensearch

import groovy.transform.Memoized
import io.micronaut.data.search.tck.AbstractHybridSearchSpec
import io.micronaut.data.search.tck.repositories.ArticleHybridSearchRepository
import io.micronaut.test.support.TestPropertyProvider

class OpenSearchHybridSearchSpec extends AbstractHybridSearchSpec implements TestPropertyProvider {

    @Override
    Map<String, String> getProperties() {
        return OpenSearchTestContainer.properties()
    }

    @Memoized
    @Override
    ArticleHybridSearchRepository getArticleRepository() {
        return context.getBean(OpenSearchArticleHybridSearchRepository)
    }
}
