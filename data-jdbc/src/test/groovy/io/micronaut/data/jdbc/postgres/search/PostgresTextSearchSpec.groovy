package io.micronaut.data.jdbc.postgres.search

import groovy.transform.Memoized
import io.micronaut.data.jdbc.postgres.PostgresTestPropertyProvider
import io.micronaut.data.model.search.SearchCapability
import io.micronaut.data.model.search.SearchQuery
import io.micronaut.data.model.search.UnsupportedSearchFeatureException
import io.micronaut.data.search.tck.AbstractTextSearchSpec
import io.micronaut.data.search.tck.repositories.BookSearchRepository

class PostgresTextSearchSpec extends AbstractTextSearchSpec implements PostgresTestPropertyProvider {

    @Memoized
    @Override
    BookSearchRepository getBookSearchRepository() {
        return context.getBean(PostgresBookSearchRepository)
    }

    @Override
    Set<SearchCapability> capabilities() {
        // pg_trgm adds fuzzy matching, with a fixed similarity threshold rather than an edit distance
        return EnumSet.of(SearchCapability.LANGUAGE_OVERRIDE, SearchCapability.PHRASE, SearchCapability.PREFIX, SearchCapability.FUZZY, SearchCapability.FIELD_SCOPE,
                SearchCapability.HIGHLIGHT, SearchCapability.VECTOR, SearchCapability.MIN_SCORE)
    }

    @Override
    List<String> packages() {
        return [getClass().package.name, "io.micronaut.data.search.tck.entities"]
    }

    @Override
    String nativeProgrammingWithoutKotlinQuery() {
        return "programming & !kotlin"
    }

    void "a language override also analyses rows that hold their own language"() {
        given:
        def stemmed = newBook("Bakers Dozen", "Twelve loaves", "Miller", 90)
        stemmed.language = "english"
        bookSearchRepository.save(stemmed)

        expect: "the row is analysed with the override, like the query terms, rather than with its english stemming"
        bookSearchRepository.search(SearchQuery.builder("bakers").language("simple").build()).results()*.entity()*.title == ["Bakers Dozen"]
        bookSearchRepository.search(SearchQuery.builder("baker").language("simple").build()).results()*.entity()*.title == ["Baking Bread"]
    }

    void "a fuzzy match rejects a language override"() {
        when:
        bookSearchRepository.search(SearchQuery.builder("kotln").fuzzy().language("simple").build())

        then:
        def e = thrown(UnsupportedSearchFeatureException)
        e.capability == SearchCapability.LANGUAGE_OVERRIDE
    }
}
