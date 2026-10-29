package io.micronaut.data.r2dbc.postgres.search

import groovy.transform.Memoized
import io.micronaut.data.model.Pageable
import io.micronaut.data.model.Sort
import io.micronaut.data.model.search.SearchQuery
import io.micronaut.data.r2dbc.postgres.vector.PostgresVectorTestPropertyProvider
import io.micronaut.data.search.tck.AbstractHybridSearchSpec
import io.micronaut.data.search.tck.repositories.ArticleHybridSearchRepository

class PostgresHybridSearchSpec extends AbstractHybridSearchSpec implements PostgresVectorTestPropertyProvider {

    @Memoized
    @Override
    ArticleHybridSearchRepository getArticleRepository() {
        return context.getBean(PostgresArticleHybridSearchRepository)
    }

    void "a reactive hybrid search fuses the same ranking as the blocking one"() {
        given:
        def reactive = context.getBean(PostgresReactiveArticleHybridSearchRepository)
        def query = SearchQuery.builder("bread").vector("embedding", COFFEE_LIKE).highlight("title").minScore(0.016d).build()

        when:
        def blocking = articleRepository.search(query)
        def fused = reactive.search(query).block()

        then:
        fused.results()*.entity()*.title == blocking.results()*.entity()*.title
        fused.results()*.score()*.value() == blocking.results()*.score()*.value()
        fused.results()*.highlights() == blocking.results()*.highlights()
        fused.results().every { it.score().value() >= 0.016d }
    }

    void "an invalid reactive hybrid search is an error signal, not a throw"() {
        given:
        def reactive = context.getBean(PostgresReactiveArticleHybridSearchRepository)
        def query = SearchQuery.builder("bread").vector("embedding", COFFEE_LIKE).build()

        when:
        def mono = reactive.search(query, Pageable.from(0, 2, Sort.of(Sort.Order.asc("title"))))

        then:
        noExceptionThrown()

        when:
        mono.block()

        then:
        def e = thrown(IllegalArgumentException)
        e.message.contains("a sort cannot be applied")
    }

    @Override
    List<String> packages() {
        return [getClass().package.name, "io.micronaut.data.search.tck.entities"]
    }
}
