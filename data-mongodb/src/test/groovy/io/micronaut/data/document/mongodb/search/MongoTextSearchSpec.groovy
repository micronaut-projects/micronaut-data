package io.micronaut.data.document.mongodb.search

import groovy.transform.Memoized
import io.micronaut.data.document.mongodb.MongoTestPropertyProvider
import io.micronaut.data.model.search.SearchCapability
import io.micronaut.data.search.tck.AbstractTextSearchSpec
import io.micronaut.data.search.tck.repositories.BookSearchRepository

class MongoTextSearchSpec extends AbstractTextSearchSpec implements MongoTestPropertyProvider {

    @Memoized
    @Override
    BookSearchRepository getBookSearchRepository() {
        return context.getBean(MongoBookSearchRepository)
    }

    @Override
    Set<SearchCapability> capabilities() {
        // $text searches the whole text index and has no prefix matching
        return EnumSet.of(SearchCapability.PHRASE, SearchCapability.LANGUAGE_OVERRIDE)
    }
}
