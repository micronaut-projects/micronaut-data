package io.micronaut.data.runtime.criteria

import io.micronaut.data.model.Pageable
import io.micronaut.data.model.runtime.RuntimeEntityRegistry
import io.micronaut.data.model.runtime.RuntimePersistentEntity
import io.micronaut.data.model.search.SearchQuery
import io.micronaut.data.model.vector.Vector
import io.micronaut.data.runtime.intercept.criteria.NativeHybridSearch
import io.micronaut.data.runtime.intercept.criteria.SearchQuerySupport
import spock.lang.Specification

import java.util.concurrent.CompletionException

class NativeFusionFallbackSpec extends Specification {

    void "the operations decide whether a failed native fusion falls back to in-memory fusion"() {
        given:
        def unavailable = new IllegalStateException("no such stage")
        def operations = new NativeHybridSearch() {
            @Override
            boolean supportsNativeHybridSearch(SearchQuery searchQuery, RuntimePersistentEntity<?> persistentEntity) {
                return true
            }

            @Override
            boolean nativeHybridSearchUnavailable(Throwable failure) {
                return failure.is(unavailable)
            }
        }

        expect:
        SearchQuerySupport.nativeFusionUnavailable(operations, unavailable)
        SearchQuerySupport.nativeFusionUnavailable(operations, new CompletionException(unavailable))
        !SearchQuerySupport.nativeFusionUnavailable(operations, new IllegalStateException("other"))
        !SearchQuerySupport.nativeFusionUnavailable(new Object(), unavailable)
    }

    void "a natively fused query rejects a vector property that holds no vector before it runs"() {
        given:
        def registry = Stub(RuntimeEntityRegistry) {
            getEntity(_) >> { Class type -> new RuntimePersistentEntity<Object>(type) }
        }
        def query = SearchQuery.builder("coffee").vector("title", Vector.of([1f, 0f, 0f] as float[])).build()

        when:
        SearchQuerySupport.hybridQuery(new RuntimeCriteriaBuilder(registry), (Class<Object>) SearchVectorArticle, query, Pageable.UNPAGED)

        then:
        def e = thrown(IllegalArgumentException)
        e.message.contains("Property 'title' of entity SearchVectorArticle is not a Vector property")
    }
}
