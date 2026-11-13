package io.micronaut.data.r2dbc.postgres.search

import groovy.transform.Memoized
import io.micronaut.data.r2dbc.postgres.vector.PostgresVectorTestPropertyProvider
import io.micronaut.data.search.tck.AbstractReactiveHybridSearchSpec
import io.micronaut.data.search.tck.repositories.ArticleHybridSearchRepository
import io.micronaut.data.search.tck.repositories.AsyncArticleHybridSearchRepository
import io.micronaut.data.search.tck.repositories.ReactorArticleHybridSearchRepository

class PostgresHybridSearchSpec extends AbstractReactiveHybridSearchSpec implements PostgresVectorTestPropertyProvider {

    @Memoized
    @Override
    ArticleHybridSearchRepository getArticleRepository() {
        return context.getBean(PostgresArticleHybridSearchRepository)
    }

    @Memoized
    @Override
    ReactorArticleHybridSearchRepository getReactiveArticleRepository() {
        return context.getBean(PostgresReactiveArticleHybridSearchRepository)
    }

    @Memoized
    @Override
    AsyncArticleHybridSearchRepository getAsyncArticleRepository() {
        return context.getBean(PostgresAsyncArticleHybridSearchRepository)
    }

    @Override
    List<String> packages() {
        return [getClass().package.name, "io.micronaut.data.search.tck.entities"]
    }
}
