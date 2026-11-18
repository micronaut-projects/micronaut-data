package io.micronaut.data.search.elasticsearch

import groovy.transform.Memoized
import io.micronaut.data.model.search.SearchCapability
import io.micronaut.data.search.tck.AbstractTextSearchSpec
import io.micronaut.data.search.tck.repositories.BookSearchRepository
import io.micronaut.test.support.TestPropertyProvider

class ElasticsearchTextSearchSpec extends AbstractTextSearchSpec implements TestPropertyProvider {

    @Override
    Map<String, String> getProperties() {
        return ElasticsearchTestContainer.properties()
    }

    @Memoized
    @Override
    BookSearchRepository getBookSearchRepository() {
        return context.getBean(ElasticsearchBookSearchRepository)
    }

    @Override
    String noStemmingLanguage() {
        // the analyzer is fixed per field in the index mapping
        return null
    }

    @Override
    String noStemmingQueryLanguage() {
        // the query is analysed with the built-in simple analyzer instead of the fields' english one
        return "simple"
    }

    @Override
    Set<SearchCapability> capabilities() {
        return EnumSet.of(SearchCapability.LANGUAGE_OVERRIDE, SearchCapability.PHRASE, SearchCapability.PREFIX, SearchCapability.FUZZY, SearchCapability.FIELD_SCOPE, SearchCapability.BOOST, SearchCapability.HIGHLIGHT, SearchCapability.MIN_SCORE, SearchCapability.VECTOR, SearchCapability.HYBRID, SearchCapability.FUZZINESS)
    }
}
