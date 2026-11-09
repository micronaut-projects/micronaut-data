package io.micronaut.data.jdbc.oraclexe.search

import groovy.transform.Memoized
import io.micronaut.data.connection.jdbc.advice.DelegatingDataSource
import io.micronaut.data.jdbc.oraclexe.Oracle23TestPropertyProvider
import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.model.query.builder.sql.SqlQueryBuilder
import io.micronaut.data.model.runtime.RuntimePersistentEntity
import io.micronaut.data.model.search.MatchMode
import io.micronaut.data.model.search.SearchCapability
import io.micronaut.data.repository.jpa.criteria.PredicateSpecification
import io.micronaut.data.search.tck.entities.Book
import io.micronaut.data.search.tck.AbstractTextSearchSpec
import io.micronaut.data.search.tck.entities.Book
import io.micronaut.data.search.tck.repositories.BookSearchRepository

import javax.sql.DataSource
import java.sql.SQLException

class OracleTextSearchSpec extends AbstractTextSearchSpec implements Oracle23TestPropertyProvider {

    void "creating the highlight policy again is ignored and another Oracle Text failure is raised"() {
        given:
        def statement = new SqlQueryBuilder(Dialect.ORACLE).buildCreateTableStatements(new RuntimePersistentEntity<>(Book)).last()

        when: "schema generation has created the policy already"
        execute(statement)

        then:
        noExceptionThrown()

        when: "Oracle Text rejects the statement for another reason, under the same ORA-20000"
        execute(statement.replace('MN_SEARCH_POLICY', 'not a valid name'))

        then:
        def e = thrown(SQLException)
        e.message.contains('DRG-10550')
    }

    private void execute(String sql) {
        DelegatingDataSource.unwrapDataSource(context.getBean(DataSource)).connection.withCloseable { connection ->
            connection.createStatement().withCloseable { it.execute(sql) }
        }
    }

    @Memoized
    @Override
    BookSearchRepository getBookSearchRepository() {
        return context.getBean(OracleBookSearchRepository)
    }

    @Override
    List<String> packages() {
        return [getClass().package.name, "io.micronaut.data.search.tck.entities"]
    }

    @Override
    Set<SearchCapability> capabilities() {
        return EnumSet.of(SearchCapability.PHRASE, SearchCapability.PREFIX, SearchCapability.FUZZY, SearchCapability.FIELD_SCOPE, SearchCapability.HIGHLIGHT)
    }

    @Override
    boolean supportsStemming() {
        // The default Oracle Text lexer matches words literally
        return false
    }

    @Override
    boolean allTermsAcrossFields() {
        // each search field has its own CONTEXT index, so SearchAll needs every term in one field
        return false
    }

    void "a negated term only narrows its own OR alternative in a search over several fields"() {
        expect: "the Coffee book has coffee but matches the recipes alternative"
        bookSearchRepository.search("bread -coffee OR recipes").results()*.entity()*.title as Set == ["Baking Bread", "Cooking with Coffee"] as Set
        bookSearchRepository.countHits("bread -coffee OR recipes") == 2
        bookSearchRepository.search("coffee -recipes").results().isEmpty()
    }

    void "a phrase over several fields keeps a hyphenated word"() {
        expect:
        oracleRepository.findBySearchPhrase("coffee -beans")*.title == ["Cooking with Coffee"]
    }

    void "SearchPrefix matches every word of a phrase as a prefix"() {
        expect:
        oracleRepository.findByTitleSearchPrefix('"programming kot')*.title == ["Programming Kotlin"]
        oracleRepository.findByTitleSearchPrefix('"rust prog"')*.title == ["The Rust Programming Language"]
        oracleRepository.findByTitleSearchPrefix('"prog rust"').isEmpty()
    }

    void "two criteria text matches with unnamed parameters on one field take their own labels"() {
        when:
        def books = oracleRepository.findAll({ root, cb ->
            cb.and(
                    cb.textMatch(root.get("title"), cb.parameter(String, null, "programming"), MatchMode.ANY),
                    cb.textMatch(root.get("title"), cb.parameter(String, null, "kotlin"), MatchMode.ANY))
        } as PredicateSpecification<Book>)

        then:
        books*.title == ["Programming Kotlin"]
    }

    private OracleBookSearchRepository getOracleRepository() {
        return (OracleBookSearchRepository) bookSearchRepository
    }
}
