package io.micronaut.data.jdbc.oraclexe.search

import groovy.transform.Memoized
import io.micronaut.data.jdbc.oraclexe.Oracle23TestPropertyProvider
import io.micronaut.data.model.search.SearchCapability
import io.micronaut.data.search.tck.AbstractTextSearchSpec

/**
 * The text search TCK over an entity stored as one JSON column, searched through a JSON search index.
 */
class OracleJsonTextSearchSpec extends AbstractTextSearchSpec implements Oracle23TestPropertyProvider {

    @Memoized
    @Override
    def getBookSearchRepository() {
        return context.getBean(OracleJsonBookSearchRepository)
    }

    @Override
    def newBook(String title, String description, String author, int pages) {
        return new OracleJsonBook(title, description, author, pages)
    }

    @Override
    List<String> packages() {
        return [getClass().package.name]
    }

    @Override
    Set<SearchCapability> capabilities() {
        return EnumSet.of(SearchCapability.PHRASE, SearchCapability.PREFIX, SearchCapability.FUZZY, SearchCapability.FIELD_SCOPE, SearchCapability.BOOST, SearchCapability.HIGHLIGHT, SearchCapability.VECTOR, SearchCapability.MIN_SCORE)
    }

    @Override
    String noStemmingLanguage() {
        return null
    }

    @Override
    boolean supportsStemming() {
        return false
    }

    @Override
    boolean allTermsAcrossFields() {
        // each property is searched with its own CONTAINS ... INPATH, so SearchAll needs every term in one property
        return false
    }
}
