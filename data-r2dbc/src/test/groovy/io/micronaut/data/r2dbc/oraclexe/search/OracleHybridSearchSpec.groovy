package io.micronaut.data.r2dbc.oraclexe.search

import groovy.transform.Memoized
import io.micronaut.data.r2dbc.oraclexe.Oracle23TestPropertyProvider
import io.micronaut.data.search.tck.AbstractReactiveHybridSearchSpec
import io.micronaut.data.search.tck.repositories.ArticleHybridSearchRepository
import io.micronaut.data.search.tck.repositories.AsyncArticleHybridSearchRepository
import io.micronaut.data.search.tck.repositories.ReactorArticleHybridSearchRepository

class OracleHybridSearchSpec extends AbstractReactiveHybridSearchSpec implements Oracle23TestPropertyProvider {

    @Memoized
    @Override
    ArticleHybridSearchRepository getArticleRepository() {
        return context.getBean(OracleArticleHybridSearchRepository)
    }

    @Memoized
    @Override
    ReactorArticleHybridSearchRepository getReactiveArticleRepository() {
        return context.getBean(OracleReactiveArticleHybridSearchRepository)
    }

    @Memoized
    @Override
    AsyncArticleHybridSearchRepository getAsyncArticleRepository() {
        return context.getBean(OracleAsyncArticleHybridSearchRepository)
    }

    @Override
    List<String> packages() {
        return [getClass().package.name, "io.micronaut.data.search.tck.entities"]
    }
}
