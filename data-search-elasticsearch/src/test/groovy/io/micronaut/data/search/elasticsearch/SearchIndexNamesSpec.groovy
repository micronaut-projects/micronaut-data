package io.micronaut.data.search.elasticsearch

import io.micronaut.data.search.document.runtime.SearchIndexNames
import spock.lang.Specification

class SearchIndexNamesSpec extends Specification {

    void "a valid index name passes"() {
        expect:
        SearchIndexNames.validate("test_books-2026.v1") == "test_books-2026.v1"
    }

    void "an index name that would address other indices or break the path is rejected: #name"() {
        when:
        SearchIndexNames.validate(name)

        then:
        IllegalArgumentException e = thrown()
        e.message.startsWith("Invalid search index name")

        where:
        name << ["*", "books,users", "books/_doc", "Books", "_books", "-books", "+books", ".", "..", "", "my books", "books#1", "a" * 256]
    }
}
