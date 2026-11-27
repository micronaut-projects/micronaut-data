package io.micronaut.data.model.query.builder.sql

import io.micronaut.data.annotation.search.SearchWeight
import spock.lang.Specification

class TextSearchDialectSpec extends Specification {

    void "the PostgreSQL index uses the entity language without a language column"() {
        given:
        def dialect = TextSearchDialect.forDialect(Dialect.POSTGRES)
        def columns = [new TextSearchDialect.SearchColumn("title", SearchWeight.HIGH), new TextSearchDialect.SearchColumn("body", null)]

        expect:
        dialect.buildSearchIndex("idx_book_search", "book", columns, "english") ==
            dialect.buildSearchIndex("idx_book_search", "book", columns, "english", null)
        dialect.buildSearchIndex("idx_book_search", "book", columns, "english")[0].contains("to_tsvector('english',")
    }

    void "a language column selects the PostgreSQL configuration per row"() {
        given:
        def dialect = TextSearchDialect.forDialect(Dialect.POSTGRES)
        def columns = [new TextSearchDialect.SearchColumn("title", null)]

        expect:
        dialect.buildSearchIndex("idx_book_search", "book", columns, "english", "lang")[0]
            .contains("to_tsvector(coalesce(lang,'english')::regconfig,")
    }

    void "a target without a language column"() {
        expect:
        new TextSearchDialect.Target("data", "b") == new TextSearchDialect.Target("data", "b", null)
        TextSearchDialect.Target.RELATIONAL.languageColumn() == null
    }
}
