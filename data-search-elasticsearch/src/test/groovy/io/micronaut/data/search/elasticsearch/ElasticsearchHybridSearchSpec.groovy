package io.micronaut.data.search.elasticsearch

import groovy.transform.Memoized
import io.micronaut.data.search.tck.AbstractHybridSearchSpec
import io.micronaut.data.search.tck.repositories.ArticleHybridSearchRepository
import io.micronaut.test.support.TestPropertyProvider

class ElasticsearchHybridSearchSpec extends AbstractHybridSearchSpec implements TestPropertyProvider {

    @Override
    Map<String, String> getProperties() {
        return ElasticsearchTestContainer.properties()
    }

    @Memoized
    @Override
    ArticleHybridSearchRepository getArticleRepository() {
        return context.getBean(ElasticsearchArticleHybridSearchRepository)
    }
}
