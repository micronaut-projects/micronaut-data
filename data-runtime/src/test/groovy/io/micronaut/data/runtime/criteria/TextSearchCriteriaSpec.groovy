package io.micronaut.data.runtime.criteria

import io.micronaut.core.annotation.AnnotationMetadata
import io.micronaut.data.model.jpa.criteria.PersistentEntityCriteriaQueryBuilder
import io.micronaut.data.model.query.builder.QueryResult
import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.model.query.builder.sql.SqlQueryBuilder
import io.micronaut.data.model.runtime.RuntimeEntityRegistry
import io.micronaut.data.model.runtime.RuntimePersistentEntity
import io.micronaut.data.model.runtime.convert.OracleTextQueryConverter
import io.micronaut.data.model.runtime.convert.PostgresTsQueryConverter
import io.micronaut.data.model.Pageable
import io.micronaut.data.model.search.MatchMode
import io.micronaut.data.model.search.SearchQuery
import io.micronaut.data.model.search.TextMatchOptions
import io.micronaut.data.model.search.TextSearchSupport
import io.micronaut.data.runtime.intercept.criteria.SearchQuerySupport
import io.micronaut.data.model.vector.search.SearchResults
import jakarta.persistence.criteria.Expression
import io.micronaut.data.model.Pageable
import io.micronaut.data.model.search.SearchCapability
import io.micronaut.data.model.search.SearchQuery
import io.micronaut.data.model.search.UnsupportedSearchFeatureException
import io.micronaut.data.runtime.intercept.criteria.SearchQuerySupport
import spock.lang.Specification

/**
 * Renders the runtime criteria tree the {@code search(SearchQuery)} interceptor builds, so the
 * SQL of each dialect is checked without a database.
 */
class TextSearchCriteriaSpec extends Specification {

    RuntimeCriteriaBuilder cb = new RuntimeCriteriaBuilder(Stub(RuntimeEntityRegistry) {
        getEntity(_) >> { Class type -> new RuntimePersistentEntity<Object>(type) }
    })

    private QueryResult build(Dialect dialect) {
        return build(dialect, SearchArticle, AnnotationMetadata.EMPTY_METADATA)
    }

    private QueryResult build(Dialect dialect, Class<?> entityType, AnnotationMetadata annotationMetadata) {
        def query = cb.createQuery(SearchResults)
        def root = query.from(entityType)
        def parameter = cb.parameter(String, null, "coffee")
        List<Expression<?>> fields = [root.get("title"), root.get("body")]
        query.where(cb.textMatch(fields, parameter, MatchMode.ANY))
        def score = cb.searchScore(fields, parameter, MatchMode.ANY)
        query.multiselect(root, score.alias(TextSearchSupport.SCORE_ALIAS),
                cb.searchHighlight(root.get("title"), parameter, MatchMode.ANY).alias(TextSearchSupport.highlightAlias("title")))
        query.orderBy(cb.desc(score))
        return ((PersistentEntityCriteriaQueryBuilder) query).build(annotationMetadata, new SqlQueryBuilder(dialect))
    }

    void "a native match over several fields on Oracle takes its own labels when it is not the first match"() {
        when:
        def query = cb.createQuery(SearchResults)
        def root = query.from(SearchArticle)
        List<Expression<?>> fields = [root.get("title"), root.get("body")]
        query.where(cb.and(cb.textMatch(root.get("title"), cb.parameter(String, null, "coffee"), MatchMode.ANY),
                cb.textMatch(fields, cb.parameter(String, null, "tea NOT milk"), MatchMode.NATIVE)))
        query.select(root)
        def result = ((PersistentEntityCriteriaQueryBuilder) query).build(AnnotationMetadata.EMPTY_METADATA, new SqlQueryBuilder(Dialect.ORACLE))

        then: "the labels stay apart, as Oracle rejects a label used twice"
        result.query.contains('CONTAINS(search_article_."TITLE",?,1) > 0')
        result.query.contains('(CONTAINS(search_article_."TITLE",?,2) > 0 OR CONTAINS(search_article_."BODY",?,3) > 0)')
    }

    void "PostgreSQL highlights with ts_headline"() {
        when:
        def result = build(Dialect.POSTGRES)

        then:
        result.query == 'SELECT search_article_."id",search_article_."title",search_article_."body",' +
                'ts_rank_cd((setweight(to_tsvector(\'english\',coalesce(search_article_."title",\'\')),\'A\') || to_tsvector(\'english\',coalesce(search_article_."body",\'\'))),to_tsquery(\'english\',?)) AS mn_score,' +
                'ts_headline(\'english\',search_article_."title",to_tsquery(\'english\',?),\'StartSel=<em>,StopSel=</em>\') AS mn_hl_title' +
                ' FROM "article" search_article_' +
                ' WHERE ((setweight(to_tsvector(\'english\',coalesce(search_article_."title",\'\')),\'A\') || to_tsvector(\'english\',coalesce(search_article_."body",\'\'))) @@ to_tsquery(\'english\',?))' +
                ' ORDER BY ts_rank_cd((setweight(to_tsvector(\'english\',coalesce(search_article_."title",\'\')),\'A\') || to_tsvector(\'english\',coalesce(search_article_."body",\'\'))),to_tsquery(\'english\',?)) DESC'
        result.parameterBindings.size() == 4
        result.parameterBindings.every { it.converterClassName == PostgresTsQueryConverter.Any.name }
        TextSearchSupport.highlightedProperties(result.query, new RuntimePersistentEntity<Object>(SearchArticle)) == ["title"]
    }

    void "Oracle highlights with CTX_DOC.POLICY_SNIPPET over the field"() {
        when:
        def result = build(Dialect.ORACLE)

        then:
        result.query.contains('CTX_DOC.POLICY_SNIPPET(\'MN_SEARCH_POLICY\',search_article_."TITLE",?,starttag => \'<em>\',endtag => \'</em>\') AS mn_hl_title')
        result.query.contains('(SCORE(1) * 10 + SCORE(2) * 1) AS mn_score')
        result.parameterBindings.find { it.converterClassName == OracleTextQueryConverter.Any.name } != null
    }

    void "Oracle highlights a JSON entity over JSON_VALUE of the property"() {
        when: "the repository metadata carries the entity's JSON representation, as the processor copies it"
        def entity = new RuntimePersistentEntity<Object>(SearchJsonArticle)
        def result = build(Dialect.ORACLE, SearchJsonArticle, entity.annotationMetadata)

        then:
        result.query.contains('CTX_DOC.POLICY_SNIPPET(\'MN_SEARCH_POLICY\',JSON_VALUE(search_json_article_.DATA,\'$.title\' RETURNING CLOB),?,starttag => \'<em>\',endtag => \'</em>\') AS mn_hl_title')
    }

    void "a language override replaces the entity language of a PostgreSQL query and is rejected on Oracle"() {
        given:
        def searchQuery = SearchQuery.builder("coffee").language("simple").highlight("title").build()

        when:
        def postgres = ((PersistentEntityCriteriaQueryBuilder) SearchQuerySupport.textQuery(cb, SearchArticle as Class<Object>, searchQuery, Pageable.UNPAGED))
                .build(AnnotationMetadata.EMPTY_METADATA, new SqlQueryBuilder(Dialect.POSTGRES))

        then:
        postgres.query.contains("to_tsvector('simple',coalesce(search_article_.\"title\",''))")
        postgres.query.contains("to_tsquery('simple',?)")
        postgres.query.contains("ts_headline('simple',search_article_.\"title\",to_tsquery('simple',?)")
        !postgres.query.contains("'english'")

        when:
        ((PersistentEntityCriteriaQueryBuilder) SearchQuerySupport.textQuery(cb, SearchArticle as Class<Object>, searchQuery, Pageable.UNPAGED))
                .build(AnnotationMetadata.EMPTY_METADATA, new SqlQueryBuilder(Dialect.ORACLE))

        then:
        def e = thrown(UnsupportedSearchFeatureException)
        e.capability == SearchCapability.LANGUAGE_OVERRIDE
    }

    private String postgresQuery(SearchQuery searchQuery) {
        return ((PersistentEntityCriteriaQueryBuilder) SearchQuerySupport.textQuery(cb, SearchLanguageArticle as Class<Object>, searchQuery, Pageable.UNPAGED))
                .build(AnnotationMetadata.EMPTY_METADATA, new SqlQueryBuilder(Dialect.POSTGRES)).query
    }

    void "a PostgreSQL language override also replaces the @Language column of the rows"() {
        when: "the entity language is the fallback of the row's configuration"
        def entityLanguage = postgresQuery(SearchQuery.of("bakers"))

        then:
        entityLanguage.contains("WHERE (to_tsvector(coalesce(search_language_article_.\"lang\",'english')::regconfig,search_language_article_.\"title\") @@ to_tsquery('english',?))")

        when: "an override analyses the rows with the configuration of the query terms"
        def overridden = postgresQuery(SearchQuery.builder("bakers").language("simple").build())

        then:
        !overridden.contains("coalesce(search_language_article_.\"lang\"")
        overridden.contains("ts_rank_cd(to_tsvector('simple',search_language_article_.\"title\"),to_tsquery('simple',?))")
        overridden.contains("WHERE (to_tsvector('simple',search_language_article_.\"title\") @@ to_tsquery('simple',?))")
    }

    void "a PostgreSQL fuzzy match rejects a language override, as trigrams have no configuration"() {
        when:
        postgresQuery(SearchQuery.builder("bakrs").fuzzy().language("simple").build())

        then:
        def e = thrown(UnsupportedSearchFeatureException)
        e.capability == SearchCapability.LANGUAGE_OVERRIDE
        e.message.contains("pg_trgm")

        when: "the score alone, as a criteria query may project it"
        def query = cb.createQuery(SearchResults)
        def from = query.from(SearchLanguageArticle)
        query.multiselect(from, cb.searchScore([from.get("title")], cb.parameter(String, null, "bakrs"), MatchMode.FUZZY,
                TextMatchOptions.builder().language("simple").build()).alias(TextSearchSupport.SCORE_ALIAS))
        ((PersistentEntityCriteriaQueryBuilder) query).build(AnnotationMetadata.EMPTY_METADATA, new SqlQueryBuilder(Dialect.POSTGRES))

        then:
        def scoreError = thrown(UnsupportedSearchFeatureException)
        scoreError.capability == SearchCapability.LANGUAGE_OVERRIDE
    }

    void "unsupported dialects reject the highlight function"() {
        when:
        build(Dialect.H2)

        then:
        thrown(UnsupportedOperationException)
    }

    void "the highlight alias helper finds every projected search field once, in projection order"() {
        given:
        def entity = new RuntimePersistentEntity<Object>(SearchArticle)

        expect:
        TextSearchSupport.highlightedProperties("SELECT a AS mn_hl_body, b AS mn_hl_title, c AS mn_hl_body FROM t", entity) == ["body", "title"]
        TextSearchSupport.highlightedProperties("SELECT 1", entity).isEmpty()
    }

    void "the highlight alias helper ignores names that only start with the highlight prefix"() {
        given:
        def entity = new RuntimePersistentEntity<Object>(SearchArticle)

        expect: "an alias of no search field, a column, a longer alias and a literal are not highlights"
        TextSearchSupport.highlightedProperties(
                "SELECT x AS mn_hl_unknown, t.mn_hl_body, y AS mn_hl_titles FROM t WHERE z = 'mn_hl_body' OR w = 'mn_hl_title'", entity).isEmpty()

        and: "the projected fragment is still found next to them"
        TextSearchSupport.highlightedProperties(
                "SELECT x AS mn_hl_unknown, y AS mn_hl_titles, f AS mn_hl_title FROM t WHERE z = 'mn_hl_body'", entity) == ["title"]
    }

    private String oracleScore(TextMatchOptions options) {
        def query = cb.createQuery(SearchResults)
        def root = query.from(SearchPlace)
        def parameter = cb.parameter(String, null, "paris")
        List<Expression<?>> fields = [root.get("city"), root.get("address").get("city")]
        query.where(cb.textMatch(fields, parameter, MatchMode.ANY, options))
        query.multiselect(root, cb.searchScore(fields, parameter, MatchMode.ANY, options).alias(TextSearchSupport.SCORE_ALIAS))
        def result = ((PersistentEntityCriteriaQueryBuilder) query).build(AnnotationMetadata.EMPTY_METADATA, new SqlQueryBuilder(Dialect.ORACLE))
        return result.query
    }

    void "a criteria boost applies to the field with that property path only"() {
        expect: "a top-level boost leaves the embedded property of the same name alone, and the other way round"
        oracleScore(TextMatchOptions.builder().boost("city", 2d).build()).contains('(SCORE(1) * 2 + SCORE(2) * 1) AS mn_score')
        oracleScore(TextMatchOptions.builder().boost("address.city", 3d).build()).contains('(SCORE(1) * 1 + SCORE(2) * 3) AS mn_score')
    }

    void "a criteria boost of a property that is not matched is rejected"() {
        given:
        def root = cb.createQuery(SearchResults).from(SearchPlace)
        def parameter = cb.parameter(String, null, "paris")
        List<Expression<?>> fields = [root.get("city")]
        def options = TextMatchOptions.builder().boost(boosted, 2d).build()

        when:
        cb.textMatch(fields, parameter, MatchMode.ANY, options)

        then:
        def e = thrown(IllegalArgumentException)
        e.message == "The boosted property '" + boosted + "' is not one of the matched fields [city]"

        when:
        cb.searchScore(fields, parameter, MatchMode.ANY, options)

        then:
        thrown(IllegalArgumentException)

        where:
        boosted << ["citi", "address.city"]
    }

    void "a criteria edit distance needs the fuzzy mode"() {
        given:
        def root = cb.createQuery(SearchResults).from(SearchArticle)
        def parameter = cb.parameter(String, null, "coffe")
        List<Expression<?>> fields = [root.get("title")]
        def options = TextMatchOptions.builder().fuzziness(2).build()

        when:
        cb.textMatch(fields, parameter, MatchMode.ANY, options)

        then:
        def e = thrown(IllegalArgumentException)
        e.message.contains("An edit distance applies only to a FUZZY match, not to ANY")

        when:
        cb.searchScore(fields, parameter, MatchMode.PHRASE, options)

        then:
        thrown(IllegalArgumentException)

        expect:
        cb.textMatch(fields, parameter, MatchMode.FUZZY, options) != null
        cb.searchScore(fields, parameter, MatchMode.FUZZY, options) != null
    }

    void "a SearchQuery boost of a search field that is not searched is rejected"() {
        when:
        SearchQuerySupport.textQuery(cb, (Class<Object>) SearchArticle, SearchQuery.builder("rust").fields("title").boost("body", 2d).build(), Pageable.UNPAGED)

        then:
        def e = thrown(IllegalArgumentException)
        e.message == "Property 'body' of entity SearchArticle is boosted but not searched; the searched fields are [title]"

        when: "a boost of a searched field is accepted, with every search field searched by default"
        SearchQuerySupport.textQuery(cb, (Class<Object>) SearchArticle, SearchQuery.builder("rust").boost("body", 2d).build(), Pageable.UNPAGED)
        SearchQuerySupport.textQuery(cb, (Class<Object>) SearchArticle, SearchQuery.builder("rust").fields("title").boost("title", 2d).build(), Pageable.UNPAGED)

        then:
        noExceptionThrown()
    }
}
