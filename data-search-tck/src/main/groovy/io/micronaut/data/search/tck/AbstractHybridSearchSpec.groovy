package io.micronaut.data.search.tck

import io.micronaut.context.ApplicationContext
import io.micronaut.data.model.Pageable
import io.micronaut.data.model.Sort
import io.micronaut.data.model.search.SearchQuery
import io.micronaut.data.model.vector.Vector
import io.micronaut.data.search.tck.entities.Article
import io.micronaut.data.search.tck.repositories.ArticleHybridSearchRepository
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

/**
 * The hybrid text and vector search test kit: a backend supporting both {@code @SearchField}
 * matching and vector similarity passes it by implementing {@link ArticleHybridSearchRepository}.
 */
abstract class AbstractHybridSearchSpec extends Specification {

    static final Vector COFFEE_LIKE = Vector.of([1f, 0f, 0f] as float[])

    @AutoCleanup
    @Shared
    ApplicationContext context = ApplicationContext.run(properties)

    abstract ArticleHybridSearchRepository getArticleRepository()

    void setup() {
        articleRepository.deleteAll()
        articleRepository.saveAll([
                new Article("Cooking with Coffee", "Recipes and desserts made with coffee beans", Vector.of([1f, 0f, 0f] as float[])),
                new Article("Coffee Roasting", "Roasting green coffee beans at home", Vector.of([0.9f, 0.1f, 0f] as float[])),
                new Article("Baking Bread", "Sourdough and yeast loaves for the home baker", Vector.of([0.2f, 0.9f, 0f] as float[])),
                new Article("Java Concurrency", "Threads and locks for the working programmer", Vector.of([0f, 0.3f, 1f] as float[])),
        ])
    }

    void cleanup() {
        articleRepository.deleteAll()
    }

    void "a hybrid search ranks a hit of both the text and the vector ranking first"() {
        when:
        def results = articleRepository.search(SearchQuery.builder("bread").vector("embedding", COFFEE_LIKE).build())
        def titles = results.results()*.entity()*.title

        then:
        // 'bread' matches only the bread article, which is third by vector similarity; it collects both ranks
        titles == ["Baking Bread", "Cooking with Coffee", "Coffee Roasting", "Java Concurrency"]
        results.totalHits() == 4
        results.results()[0].score().value() > results.results()[1].score().value()
        results.results().every { it.score().value() > 0 }
        results.results().every { it.similarity() == null }
        // 1/(60+1) + 1/(60+3) for the bread article, 1/(60+1) for the closest vector
        Math.abs(results.results()[0].score().value() - (1d / 61 + 1d / 63)) < 1e-9
        Math.abs(results.results()[1].score().value() - 1d / 61) < 1e-9
    }

    void "the weights select the rankings"() {
        expect:
        articleRepository.search(SearchQuery.builder("bread").vector("embedding", COFFEE_LIKE).vectorWeight(0).build())
                .results()*.entity()*.title == ["Baking Bread"]
        articleRepository.search(SearchQuery.builder("bread").vector("embedding", COFFEE_LIKE).textWeight(0).build())
                .results()*.entity()*.title == ["Cooking with Coffee", "Coffee Roasting", "Baking Bread", "Java Concurrency"]
        articleRepository.search(SearchQuery.builder("bread").vector("embedding", COFFEE_LIKE).rankConstant(0).build())
                .results()[0].entity().title == "Baking Bread"
    }

    void "a hybrid search pages the fused ranking"() {
        given:
        def query = SearchQuery.builder("bread").vector("embedding", COFFEE_LIKE).build()

        when:
        def first = articleRepository.search(query, Pageable.from(0, 2))
        def second = articleRepository.search(query, Pageable.from(1, 2))

        then:
        first.results()*.entity()*.title == ["Baking Bread", "Cooking with Coffee"]
        first.totalHits() == 4
        second.results()*.entity()*.title == ["Coffee Roasting", "Java Concurrency"]
        second.totalHits() == 4

        when:
        articleRepository.search(query, Pageable.from(0, 2, Sort.of(Sort.Order.asc("title"))))

        then:
        thrown(IllegalArgumentException)
    }

    void "candidates and the minimum score limit the fused ranking"() {
        expect:
        // only the best vector hit joins the text hit
        articleRepository.search(SearchQuery.builder("bread").vector("embedding", COFFEE_LIKE).candidates(1).build())
                .results()*.entity()*.title == ["Baking Bread", "Cooking with Coffee"]
        articleRepository.search(SearchQuery.builder("bread").vector("embedding", COFFEE_LIKE).minScore(0.03).build())
                .results()*.entity()*.title == ["Baking Bread"]
    }

    void "a hybrid search highlights the text hits only"() {
        when:
        def results = articleRepository.search(SearchQuery.builder("bread").vector("embedding", COFFEE_LIKE).highlight("title").build())

        then:
        results.results()[0].entity().title == "Baking Bread"
        results.results()[0].highlights()["title"][0].contains("<em>Bread</em>")
        results.results()[1].highlights().isEmpty()
    }

    void "highlightable fields are highlighted by default"() {
        when:
        def derived = articleRepository.searchByTitleSearch("coffee")

        then:
        derived.results().size() == 2
        derived.results().every { it.highlights().keySet() == ["title"] as Set }
        derived.results().every { it.highlights()["title"][0].contains("<em>Coffee</em>") }

        when:
        def declared = articleRepository.search(SearchQuery.builder("coffee").highlight().build())

        then:
        declared.results().size() == 2
        declared.results().every { it.highlights().keySet() == ["title"] as Set }

        when:
        def plain = articleRepository.search(SearchQuery.of("coffee"))

        then:
        plain.results().every { it.highlights().isEmpty() }
    }
}
