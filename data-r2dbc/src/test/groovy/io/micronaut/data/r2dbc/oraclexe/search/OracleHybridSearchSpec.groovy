package io.micronaut.data.r2dbc.oraclexe.search

import groovy.transform.Memoized
import io.micronaut.data.r2dbc.oraclexe.Oracle23TestPropertyProvider
import io.micronaut.data.search.tck.AbstractHybridSearchSpec
import io.micronaut.data.search.tck.repositories.ArticleHybridSearchRepository

class OracleHybridSearchSpec extends AbstractHybridSearchSpec implements Oracle23TestPropertyProvider {

    @Memoized
    @Override
    ArticleHybridSearchRepository getArticleRepository() {
        return context.getBean(OracleArticleHybridSearchRepository)
    }

    @Override
    List<String> packages() {
        return [getClass().package.name, "io.micronaut.data.search.tck.entities"]
    }
}
