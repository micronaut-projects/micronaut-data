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
        dialect.buildSearchIndex("idx_book_search", "book", columns, "english", "lang").last()
            .contains("to_tsvector(mn_regconfig(coalesce(lang,'english')),")
    }

    void "a target without a language column"() {
        expect:
        new TextSearchDialect.Target("data", "b") == new TextSearchDialect.Target("data", "b", null)
        TextSearchDialect.Target.RELATIONAL.languageColumn() == null
    }

    void "a query language that is not a configuration name is rejected before it reaches the SQL"() {
        when:
        TextSearchDialect.resolveLanguage([], io.micronaut.data.model.search.TextMatchOptions.builder().language(language).build())

        then:
        thrown(IllegalArgumentException)

        where:
        language << ["english'", "english',title),'') OR 1=1 --", "en-US", "english;", "pg_catalog.", ".english", "a.b.c", "my-folding"]
    }

    void "a configuration name is accepted as the query language"() {
        expect:
        TextSearchDialect.resolveLanguage([], io.micronaut.data.model.search.TextMatchOptions.builder().language("german").build()) == "german"
        TextSearchDialect.resolveLanguage([], io.micronaut.data.model.search.TextMatchOptions.builder().language("pg_catalog.english").build()) == "pg_catalog.english"
        io.micronaut.data.model.search.SearchQuery.builder("x").language("simple").build().language == "simple"
    }

    void "the query builder leaves analyzer names to the document stores"() {
        expect:
        io.micronaut.data.model.search.SearchQuery.builder("x").language("my-folding").build().language == "my-folding"
    }
}
