/*
 * Copyright 2017-2026 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.data.search.tck

import io.micronaut.data.model.Pageable
import io.micronaut.data.model.Sort
import io.micronaut.data.model.search.SearchQuery
import io.micronaut.data.model.vector.search.SearchResults
import io.micronaut.data.search.tck.entities.Article
import io.micronaut.data.search.tck.repositories.AsyncArticleHybridSearchRepository
import io.micronaut.data.search.tck.repositories.ReactorArticleHybridSearchRepository
import reactor.core.publisher.Mono

import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit

/**
 * The hybrid search test kit of a backend that also implements the reactive and async
 * {@code search(SearchQuery)}: both must fuse, filter, page and highlight like the blocking search,
 * and report an invalid query through the publisher or the future.
 */
abstract class AbstractReactiveHybridSearchSpec extends AbstractHybridSearchSpec {

    static final List<String> FUSED_TITLES = ["Baking Bread", "Cooking with Coffee", "Coffee Roasting", "Java Concurrency"]

    abstract ReactorArticleHybridSearchRepository getReactiveArticleRepository()

    abstract AsyncArticleHybridSearchRepository getAsyncArticleRepository()

    void "the #variant hybrid search fuses and highlights the same ranking as the blocking one"() {
        given:
        def query = SearchQuery.builder("bread").vector("embedding", COFFEE_LIKE).highlight("title").build()

        when:
        def blocking = articleRepository.search(query)
        def fused = search(variant, query, Pageable.UNPAGED)

        then:
        fused.results()*.entity()*.title == FUSED_TITLES
        fused.results()*.entity()*.title == blocking.results()*.entity()*.title
        fused.results()*.score()*.value() == blocking.results()*.score()*.value()
        fused.results()*.highlights() == blocking.results()*.highlights()
        fused.results()[0].highlights()["title"][0].contains("<em>Bread</em>")
        fused.results()[1].highlights().isEmpty()
        fused.totalHits() == 4

        where:
        variant << ["reactive", "async"]
    }

    void "the #variant hybrid search applies the minimum score and pages the fused ranking"() {
        when:
        def filtered = search(variant, SearchQuery.builder("bread").vector("embedding", COFFEE_LIKE).minScore(0.03).build(), Pageable.UNPAGED)
        def page = search(variant, SearchQuery.builder("bread").vector("embedding", COFFEE_LIKE).build(), Pageable.from(1, 2))

        then:
        filtered.results()*.entity()*.title == ["Baking Bread"]
        filtered.totalHits() == 1
        page.results()*.entity()*.title == FUSED_TITLES.subList(2, 4)
        page.totalHits() == 4

        where:
        variant << ["reactive", "async"]
    }

    void "an invalid #variant hybrid search fails through the #signal, not with a throw"() {
        when:
        def sorted = Pageable.from(0, 2, Sort.of(Sort.Order.asc("title")))
        def pending = call(variant, SearchQuery.builder("bread").vector("embedding", COFFEE_LIKE).build(), sorted)

        then:
        noExceptionThrown()

        when:
        await(pending)

        then:
        def unsorted = thrown(IllegalArgumentException)
        unsorted.message.contains("a sort cannot be applied")

        when:
        await(call(variant, SearchQuery.builder("bread").vector("title", COFFEE_LIKE).build(), Pageable.UNPAGED))

        then:
        def notVector = thrown(IllegalArgumentException)
        notVector.message.contains("is not a Vector property")

        where:
        variant    | signal
        "reactive" | "publisher"
        "async"    | "future"
    }

    private SearchResults<Article> search(String variant, SearchQuery query, Pageable pageable) {
        return await(call(variant, query, pageable))
    }

    private Object call(String variant, SearchQuery query, Pageable pageable) {
        if (variant == "reactive") {
            return reactiveArticleRepository.search(query, pageable)
        }
        return asyncArticleRepository.search(query, pageable)
    }

    private static SearchResults<Article> await(Object pending) {
        if (pending instanceof Mono) {
            return ((Mono<SearchResults<Article>>) pending).block()
        }
        try {
            return ((CompletableFuture<SearchResults<Article>>) pending).get(30, TimeUnit.SECONDS)
        } catch (ExecutionException e) {
            throw e.cause
        }
    }
}
