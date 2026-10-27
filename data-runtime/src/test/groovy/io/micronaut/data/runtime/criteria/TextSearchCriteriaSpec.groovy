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
import io.micronaut.data.model.search.MatchMode
import io.micronaut.data.model.search.TextSearchSupport
import io.micronaut.data.model.vector.search.SearchResults
import jakarta.persistence.criteria.Expression
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
        TextSearchSupport.highlightedProperties(result.query) == ["title"]
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
        result.query.contains('CTX_DOC.POLICY_SNIPPET(\'MN_SEARCH_POLICY\',JSON_VALUE(search_json_article_.DATA,\'$.title\'),?,starttag => \'<em>\',endtag => \'</em>\') AS mn_hl_title')
    }

    void "unsupported dialects reject the highlight function"() {
        when:
        build(Dialect.H2)

        then:
        thrown(UnsupportedOperationException)
    }

    void "the highlight alias helper finds every projected property once"() {
        expect:
        TextSearchSupport.highlightedProperties("SELECT a AS mn_hl_title, b AS mn_hl_body, c AS mn_hl_title FROM t") == ["title", "body"]
        TextSearchSupport.highlightedProperties("SELECT 1").isEmpty()
    }
}
