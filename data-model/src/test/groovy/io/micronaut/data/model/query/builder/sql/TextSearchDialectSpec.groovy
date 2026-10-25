package io.micronaut.data.model.query.builder.sql

import io.micronaut.data.annotation.search.SearchWeight
import io.micronaut.data.model.search.SearchCapability
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

    void "each dialect reports the search features it honours"() {
        expect:
        TextSearchDialect.forDialect(Dialect.POSTGRES).capabilities() == EnumSet.of(SearchCapability.PHRASE, SearchCapability.PREFIX,
            SearchCapability.FUZZY, SearchCapability.FIELD_SCOPE, SearchCapability.HIGHLIGHT, SearchCapability.LANGUAGE_OVERRIDE,
            SearchCapability.MIN_SCORE, SearchCapability.VECTOR, SearchCapability.HYBRID)
        TextSearchDialect.forDialect(Dialect.ORACLE).capabilities() == EnumSet.of(SearchCapability.PHRASE, SearchCapability.PREFIX,
            SearchCapability.FUZZY, SearchCapability.FIELD_SCOPE, SearchCapability.BOOST, SearchCapability.HIGHLIGHT,
            SearchCapability.MIN_SCORE, SearchCapability.VECTOR, SearchCapability.HYBRID)
        new TextSearchDialect() {
            @Override
            void appendTextMatch(StringBuilder query, List fields, jakarta.persistence.criteria.Expression queryExpression, io.micronaut.data.model.search.MatchMode mode,
                                 io.micronaut.data.model.search.TextMatchOptions options, TextSearchDialect.Target target,
                                 java.util.function.Consumer appendExpression, java.util.function.Function appendParameter) {
            }

            @Override
            void appendSearchScore(StringBuilder query, List fields, jakarta.persistence.criteria.Expression queryExpression, io.micronaut.data.model.search.MatchMode mode,
                                   io.micronaut.data.model.search.TextMatchOptions options, TextSearchDialect.Target target,
                                   java.util.function.Consumer appendExpression, java.util.function.Function appendParameter) {
            }

            @Override
            void appendSearchHighlight(StringBuilder query, jakarta.persistence.criteria.Expression field, jakarta.persistence.criteria.Expression queryExpression,
                                       io.micronaut.data.model.search.MatchMode mode, io.micronaut.data.model.search.TextMatchOptions options, TextSearchDialect.Target target,
                                       java.util.function.Consumer appendExpression, java.util.function.Function appendParameter) {
            }
        }.capabilities().isEmpty()

    void "a query language that is not a configuration name is rejected before it reaches the SQL"() {
        when:
        TextSearchDialect.resolveLanguage([], new io.micronaut.data.model.search.TextMatchOptions(Map.of(), 0, language))

        then:
        thrown(IllegalArgumentException)

        where:
        language << ["english'", "english',title),'') OR 1=1 --", "en-US", "english;"]
    }

    void "a configuration name is accepted as the query language"() {
        expect:
        TextSearchDialect.resolveLanguage([], new io.micronaut.data.model.search.TextMatchOptions(Map.of(), 0, "german")) == "german"
        io.micronaut.data.model.search.SearchQuery.builder("x").language("simple").build().language == "simple"
    }
}
