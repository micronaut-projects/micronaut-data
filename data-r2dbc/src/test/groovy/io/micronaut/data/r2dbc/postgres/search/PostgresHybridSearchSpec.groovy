package io.micronaut.data.r2dbc.postgres.search

import groovy.transform.Memoized
import io.micronaut.data.r2dbc.postgres.vector.PostgresVectorTestPropertyProvider
import io.micronaut.data.search.tck.AbstractHybridSearchSpec
import io.micronaut.data.search.tck.repositories.ArticleHybridSearchRepository

class PostgresHybridSearchSpec extends AbstractHybridSearchSpec implements PostgresVectorTestPropertyProvider {

    @Memoized
    @Override
    ArticleHybridSearchRepository getArticleRepository() {
        return context.getBean(PostgresArticleHybridSearchRepository)
    }

    @Override
    List<String> packages() {
        return [getClass().package.name, "io.micronaut.data.search.tck.entities"]
    }
}
