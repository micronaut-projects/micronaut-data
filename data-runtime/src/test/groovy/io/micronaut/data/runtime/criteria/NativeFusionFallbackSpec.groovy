package io.micronaut.data.runtime.criteria

import io.micronaut.data.model.runtime.RuntimePersistentEntity
import io.micronaut.data.model.search.SearchQuery
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
}
