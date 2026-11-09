package io.micronaut.data.jdbc.postgres.search

import groovy.transform.Memoized
import io.micronaut.data.connection.jdbc.advice.DelegatingDataSource
import io.micronaut.data.jdbc.config.DataJdbcConfiguration
import io.micronaut.data.jdbc.search.JdbcSearchIndexOperations
import io.micronaut.data.model.runtime.RuntimeEntityRegistry
import io.micronaut.data.jdbc.postgres.PostgresTestPropertyProvider
import io.micronaut.data.model.search.SearchCapability
import io.micronaut.data.model.search.SearchQuery
import io.micronaut.data.model.search.UnsupportedSearchFeatureException
import io.micronaut.data.search.tck.AbstractTextSearchSpec
import io.micronaut.data.search.tck.repositories.BookSearchRepository
import io.micronaut.inject.qualifiers.Qualifiers

import javax.sql.DataSource

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
                SearchCapability.HIGHLIGHT, SearchCapability.VECTOR, SearchCapability.MIN_SCORE, SearchCapability.HYBRID)
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

    void "the index operations reach the indexes of an entity in a schema other than the current one"() {
        given:
        def operations = searchIndexOperations()

        expect:
        operations.exists(CatalogRecipe)

        when:
        operations.drop(CatalogRecipe)

        then:
        !operations.exists(CatalogRecipe)
        catalogIndexCount("search_catalog", "idx_catalog_recipe_search") == 0

        when:
        operations.create(CatalogRecipe)

        then:
        operations.exists(CatalogRecipe)
        catalogIndexCount("search_catalog", "idx_catalog_recipe_search") == 1
    }

    void "the index operations find an unquoted mixed-case index name in the lower case PostgreSQL stores"() {
        given:
        def operations = searchIndexOperations()

        expect:
        operations.exists(MixedCaseRecipe)

        when:
        operations.drop(MixedCaseRecipe)

        then:
        !operations.exists(MixedCaseRecipe)
        catalogIndexCount("public", "idxmixedcaserecipe") == 0

        when:
        operations.create(MixedCaseRecipe)

        then:
        operations.exists(MixedCaseRecipe)
    }

    void "FUZZY is reported only while the pg_trgm extension is installed"() {
        given: "the capabilities of a new bean, as a bean remembers an extension it found"
        def operations = new JdbcSearchIndexOperations(context.getBean(DataJdbcConfiguration, Qualifiers.byName("default")), context, context.getBean(RuntimeEntityRegistry))
        def trigramIndexes = sqlList("SELECT indexdef FROM pg_indexes WHERE indexdef LIKE '%gin_trgm_ops%'")
        def entityType = newBook("Title", "Description", "Author", 1).getClass()

        when: "the extension is dropped with the trigram indexes"
        sqlExecute("DROP EXTENSION pg_trgm CASCADE")

        then:
        !operations.supports(entityType, SearchCapability.FUZZY)
        operations.getSearchCapabilities(entityType) == capabilities() - SearchCapability.FUZZY

        when: "it is installed again, a missing extension having been checked again"
        sqlExecute("CREATE EXTENSION pg_trgm")

        then:
        operations.supports(entityType, SearchCapability.FUZZY)
        operations.getSearchCapabilities(entityType) == capabilities()

        cleanup:
        sqlExecute("CREATE EXTENSION IF NOT EXISTS pg_trgm")
        trigramIndexes.each { sqlExecute(it.replace("CREATE INDEX ", "CREATE INDEX IF NOT EXISTS ")) }
    }

    private void sqlExecute(String sql) {
        try (def connection = DelegatingDataSource.unwrapDataSource(context.getBean(DataSource)).getConnection()
             def statement = connection.prepareStatement(sql)) {
            statement.execute()
        }
    }

    private List<String> sqlList(String sql) {
        try (def connection = DelegatingDataSource.unwrapDataSource(context.getBean(DataSource)).getConnection()
             def statement = connection.prepareStatement(sql)
             def rs = statement.executeQuery()) {
            List<String> values = []
            while (rs.next()) {
                values << rs.getString(1)
            }
            return values
        }
    }

    private long catalogIndexCount(String schema, String index) {
        try (def connection = DelegatingDataSource.unwrapDataSource(context.getBean(DataSource)).getConnection()
             def statement = connection.prepareStatement("SELECT count(*) FROM pg_indexes WHERE schemaname = ? AND indexname = ?")) {
            statement.setString(1, schema)
            statement.setString(2, index)
            try (def rs = statement.executeQuery()) {
                rs.next()
                return rs.getLong(1)
            }
        }
    }
}
