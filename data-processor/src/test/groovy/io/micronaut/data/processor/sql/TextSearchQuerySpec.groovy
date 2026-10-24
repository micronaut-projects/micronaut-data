package io.micronaut.data.processor.sql

import io.micronaut.data.intercept.annotation.DataMethod
import io.micronaut.data.intercept.annotation.DataMethodQueryParameter
import io.micronaut.data.model.runtime.convert.OracleTextQueryConverter
import io.micronaut.data.model.runtime.convert.PostgresTsQueryConverter
import io.micronaut.data.processor.visitors.AbstractDataSpec
import io.micronaut.inject.BeanDefinition
import io.micronaut.inject.ExecutableMethod
import spock.lang.Unroll

import static io.micronaut.data.processor.visitors.TestUtils.getQuery

class TextSearchQuerySpec extends AbstractDataSpec {

    private static final String ENTITY = """
import io.micronaut.data.annotation.search.SearchField;
import io.micronaut.data.annotation.search.SearchWeight;
import io.micronaut.data.annotation.search.Searchable;

@MappedEntity
@Searchable
class Article {
    @Id
    @GeneratedValue
    private Long id;
    @SearchField(weight = SearchWeight.HIGH)
    private String title;
    @SearchField(weight = SearchWeight.LOWEST)
    private String body;
    private String author;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getBody() { return body; }
    public void setBody(String body) { this.body = body; }
    public String getAuthor() { return author; }
    public void setAuthor(String author) { this.author = author; }
}
"""

    private BeanDefinition<?> articleRepository(String methods, String dialect = "POSTGRES") {
        buildRepository('test.ArticleRepository', """
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.model.vector.search.SearchResults;
import io.micronaut.data.repository.SearchRepository;
${ENTITY}
@JdbcRepository(dialect = Dialect.${dialect})
interface ArticleRepository extends CrudRepository<Article, Long>, SearchRepository<Article, Long> {
${methods}
}
""")
    }

    private static List<String> converters(ExecutableMethod<?, ?> method) {
        method.getAnnotation(DataMethod).getAnnotations(DataMethod.META_MEMBER_PARAMETERS, DataMethodQueryParameter)
                .collect { it.stringValue(DataMethodQueryParameter.META_MEMBER_CONVERTER).orElse(null) }
    }

    void "find by a property search keyword renders a tsvector match without a score"() {
        given:
        def repository = articleRepository("""
    List<Article> findByTitleSearch(String q);
""")
        def method = repository.getRequiredMethod("findByTitleSearch", String)

        expect:
        getQuery(method) == 'SELECT article_."id",article_."title",article_."body",article_."author" FROM "article" article_ WHERE (setweight(to_tsvector(\'english\',article_."title"),\'A\') @@ to_tsquery(\'english\',?))'
        converters(method) == [PostgresTsQueryConverter.Any.name]
    }

    @Unroll
    void "search keyword #keyword renders mode #converter"() {
        given:
        def repository = articleRepository("""
    List<Article> findByAuthor${keyword}(String q);
""")
        def method = repository.getRequiredMethod("findByAuthor${keyword}", String)

        expect:
        getQuery(method) == "SELECT article_.\"id\",article_.\"title\",article_.\"body\",article_.\"author\" FROM \"article\" article_ WHERE (to_tsvector('english',article_.\"author\") @@ to_tsquery('english',?))"
        converters(method) == [converter]

        where:
        keyword          | converter
        "Search"         | PostgresTsQueryConverter.Any.name
        "SearchAll"      | PostgresTsQueryConverter.All.name
        "SearchPhrase"   | PostgresTsQueryConverter.Phrase.name
        "SearchPrefix"   | PostgresTsQueryConverter.Prefix.name
    }

    void "search prefix with SearchResults projects and orders by the score"() {
        given:
        def repository = articleRepository("""
    SearchResults<Article> searchByTitleSearchAll(String q);
""")
        def method = repository.getRequiredMethod("searchByTitleSearchAll", String)
        def query = getQuery(method)

        expect:
        query == 'SELECT article_."id",article_."title",article_."body",article_."author",ts_rank_cd(setweight(to_tsvector(\'english\',article_."title"),\'A\'),to_tsquery(\'english\',?)) AS mn_score FROM "article" article_ WHERE (setweight(to_tsvector(\'english\',article_."title"),\'A\') @@ to_tsquery(\'english\',?)) ORDER BY ts_rank_cd(setweight(to_tsvector(\'english\',article_."title"),\'A\'),to_tsquery(\'english\',?)) DESC'
        converters(method) == [PostgresTsQueryConverter.All.name] * 3
    }

    void "SearchRepository search covers every search field with its weight"() {
        given:
        def repository = articleRepository("")
        def search = repository.getRequiredMethod("search", String)
        def countHits = repository.getRequiredMethod("countHits", String)

        expect:
        getQuery(search) == 'SELECT article_."id",article_."title",article_."body",article_."author",ts_rank_cd((setweight(to_tsvector(\'english\',coalesce(article_."title",\'\')),\'A\') || setweight(to_tsvector(\'english\',coalesce(article_."body",\'\')),\'D\')),to_tsquery(\'english\',?)) AS mn_score FROM "article" article_ WHERE ((setweight(to_tsvector(\'english\',coalesce(article_."title",\'\')),\'A\') || setweight(to_tsvector(\'english\',coalesce(article_."body",\'\')),\'D\')) @@ to_tsquery(\'english\',?)) ORDER BY ts_rank_cd((setweight(to_tsvector(\'english\',coalesce(article_."title",\'\')),\'A\') || setweight(to_tsvector(\'english\',coalesce(article_."body",\'\')),\'D\')),to_tsquery(\'english\',?)) DESC'
        getQuery(countHits) == 'SELECT COUNT(*) FROM "article" article_ WHERE ((setweight(to_tsvector(\'english\',coalesce(article_."title",\'\')),\'A\') || setweight(to_tsvector(\'english\',coalesce(article_."body",\'\')),\'D\')) @@ to_tsquery(\'english\',?))'
        converters(countHits) == [PostgresTsQueryConverter.Any.name]
    }

    void "SearchRepository search with a pageable leaves ordering to the pageable"() {
        given:
        def repository = articleRepository("")
        def search = repository.getRequiredMethod("search", String, io.micronaut.data.model.Pageable)

        expect:
        !getQuery(search).contains("ORDER BY")
        getQuery(search).contains(" AS mn_score FROM ")
    }

    void "no-property Search keyword and other predicates combine"() {
        given:
        def repository = articleRepository("""
    List<Article> findBySearchAndAuthor(String q, String author);
    long countBySearchAll(String q);
""")

        expect:
        getQuery(repository.getRequiredMethod("findBySearchAndAuthor", String, String)) == 'SELECT article_."id",article_."title",article_."body",article_."author" FROM "article" article_ WHERE ((setweight(to_tsvector(\'english\',coalesce(article_."title",\'\')),\'A\') || setweight(to_tsvector(\'english\',coalesce(article_."body",\'\')),\'D\')) @@ to_tsquery(\'english\',?) AND article_."author" = ?)'
        getQuery(repository.getRequiredMethod("countBySearchAll", String)) == 'SELECT COUNT(*) FROM "article" article_ WHERE ((setweight(to_tsvector(\'english\',coalesce(article_."title",\'\')),\'A\') || setweight(to_tsvector(\'english\',coalesce(article_."body",\'\')),\'D\')) @@ to_tsquery(\'english\',?))'
    }

    void "the entity language picks the text search configuration"() {
        given:
        def repository = buildRepository('test.NoteRepository', """
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.annotation.search.SearchField;
import io.micronaut.data.annotation.search.Searchable;

@MappedEntity
@Searchable(language = "german")
class Note {
    @Id
    @GeneratedValue
    private Long id;
    @SearchField
    private String text;
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getText() { return text; }
    public void setText(String text) { this.text = text; }
}

@JdbcRepository(dialect = Dialect.POSTGRES)
interface NoteRepository extends CrudRepository<Note, Long> {
    List<Note> findByTextSearch(String q);
}
""")

        expect:
        getQuery(repository.getRequiredMethod("findByTextSearch", String)) == 'SELECT note_."id",note_."text" FROM "note" note_ WHERE (to_tsvector(\'german\',note_."text") @@ to_tsquery(\'german\',?))'
    }

    void "fuzzy search renders pg_trgm word similarity on PostgreSQL"() {
        given:
        def repository = articleRepository("""
    List<Article> findByTitleSearchFuzzy(String q);
    SearchResults<Article> searchBySearchFuzzy(String q);
""")
        def finder = repository.getRequiredMethod("findByTitleSearchFuzzy", String)
        def search = repository.getRequiredMethod("searchBySearchFuzzy", String)

        expect:
        getQuery(finder) == 'SELECT article_."id",article_."title",article_."body",article_."author" FROM "article" article_ WHERE ((EXISTS (SELECT 1 FROM unnest(string_to_array(?,\' \')) AS mn_term(t) WHERE mn_term.t <% article_."title") AND NOT EXISTS (SELECT 1 FROM unnest(string_to_array(?,\' \')) AS mn_term(t) WHERE mn_term.t <% article_."title")))'
        converters(finder) == [PostgresTsQueryConverter.FuzzyPositive.name, PostgresTsQueryConverter.FuzzyNegated.name]
        getQuery(search).startsWith('SELECT article_."id",article_."title",article_."body",article_."author",(SELECT coalesce(max(greatest(word_similarity(mn_term.t,article_."title") * 10,word_similarity(mn_term.t,article_."body") * 1)),0) FROM unnest(string_to_array(?,\' \')) AS mn_term(t)) AS mn_score FROM "article" article_ WHERE ((EXISTS (SELECT 1 FROM unnest(string_to_array(?,\' \')) AS mn_term(t) WHERE mn_term.t <% article_."title" OR mn_term.t <% article_."body") AND NOT EXISTS')
        PostgresTsQueryConverter.renderFuzzy('kotln "fast rats" -cake OR tea', false) == "kotln fast rats tea"
        PostgresTsQueryConverter.renderFuzzy('kotln "fast rats" -cake OR tea', true) == "cake"
    }

    void "search is rejected on a dialect without text search"() {
        when:
        buildRepository('test.ArticleRepository', """
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
${ENTITY}
@JdbcRepository(dialect = Dialect.H2)
interface ArticleRepository extends CrudRepository<Article, Long> {
    List<Article> findByTitleSearch(String q);
}
""")

        then:
        def e = thrown(RuntimeException)
        e.message.contains("Full-text search is not supported by dialect: H2")
    }

    void "an entity without search fields cannot be searched"() {
        when:
        buildRepository('test.PlainRepository', """
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.model.vector.search.SearchResults;
import io.micronaut.data.repository.SearchRepository;

@MappedEntity
class Plain {
    @Id
    @GeneratedValue
    private Long id;
    private String name;
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
}

@JdbcRepository(dialect = Dialect.POSTGRES)
interface PlainRepository extends CrudRepository<Plain, Long>, SearchRepository<Plain, Long> {
}
""")

        then:
        def e = thrown(RuntimeException)
        e.message.contains("declares no @SearchField properties")
    }

    void "Oracle renders CONTAINS with a label per field and SCORE"() {
        given:
        def repository = articleRepository("""
    List<Article> findByTitleSearch(String q);
    List<Article> findByTitleSearchFuzzy(String q);
    SearchResults<Article> searchByTitleSearchPrefix(String q);
""", "ORACLE")

        expect:
        getQuery(repository.getRequiredMethod("findByTitleSearch", String)) == 'SELECT article_."ID",article_."TITLE",article_."BODY",article_."AUTHOR" FROM "ARTICLE" article_ WHERE (CONTAINS(article_."TITLE",?,1) > 0)'
        converters(repository.getRequiredMethod("findByTitleSearch", String)) == [OracleTextQueryConverter.Any.name]
        converters(repository.getRequiredMethod("findByTitleSearchFuzzy", String)) == [OracleTextQueryConverter.Fuzzy.name]
        getQuery(repository.getRequiredMethod("searchByTitleSearchPrefix", String)) == 'SELECT article_."ID",article_."TITLE",article_."BODY",article_."AUTHOR",SCORE(1) AS mn_score FROM "ARTICLE" article_ WHERE (CONTAINS(article_."TITLE",?,1) > 0) ORDER BY SCORE(1) DESC'
        converters(repository.getRequiredMethod("searchByTitleSearchPrefix", String)) == [OracleTextQueryConverter.Prefix.name]
    }

    void "Oracle searches several fields with positive and negated term sets"() {
        given:
        def repository = articleRepository("", "ORACLE")
        def search = repository.getRequiredMethod("search", String)
        def countHits = repository.getRequiredMethod("countHits", String)

        expect:
        getQuery(search) == 'SELECT article_."ID",article_."TITLE",article_."BODY",article_."AUTHOR",(SCORE(1) * 10 + SCORE(2) * 1) AS mn_score FROM "ARTICLE" article_ WHERE (((CONTAINS(article_."TITLE",?,1) > 0 OR CONTAINS(article_."BODY",?,2) > 0) AND NOT (CONTAINS(article_."TITLE",?,3) > 0 OR CONTAINS(article_."BODY",?,4) > 0))) ORDER BY (SCORE(1) * 10 + SCORE(2) * 1) DESC'
        converters(search) == [OracleTextQueryConverter.AnyPositive.name, OracleTextQueryConverter.AnyPositive.name, OracleTextQueryConverter.Negated.name, OracleTextQueryConverter.Negated.name]
        getQuery(countHits) == 'SELECT COUNT(*) FROM "ARTICLE" article_ WHERE (((CONTAINS(article_."TITLE",?,1) > 0 OR CONTAINS(article_."BODY",?,2) > 0) AND NOT (CONTAINS(article_."TITLE",?,3) > 0 OR CONTAINS(article_."BODY",?,4) > 0)))'
    }

    void "Oracle JSON entities use CONTAINS with INPATH on the JSON search index"() {
        given:
        def repository = buildRepository('test.DocRepository', """
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.annotation.search.SearchField;
import io.micronaut.data.annotation.search.Searchable;
import io.micronaut.data.annotation.EntityRepresentation;
import io.micronaut.data.model.vector.search.SearchResults;

@MappedEntity
@Searchable
@EntityRepresentation(type = EntityRepresentation.Type.COLUMN, columnType = EntityRepresentation.ColumnType.JSON, column = "DATA")
class Doc {
    @Id
    private Long id;
    @SearchField
    private String title;
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
}

@JdbcRepository(dialect = Dialect.ORACLE)
interface DocRepository extends CrudRepository<Doc, Long> {
    SearchResults<Doc> searchByTitleSearch(String q);
}
""")

        expect:
        getQuery(repository.getRequiredMethod("searchByTitleSearch", String)).contains('WHERE (CONTAINS(doc_.DATA,\'(\' || ? || \') INPATH (/title)\',1) > 0)')
        getQuery(repository.getRequiredMethod("searchByTitleSearch", String)).contains("SCORE(1) AS mn_score")
    }

    void "search(SearchQuery) is implemented by the runtime SearchQueryInterceptor"() {
        given:
        def repository = articleRepository("")

        when:
        def method = repository.getRequiredMethod("search", io.micronaut.data.model.search.SearchQuery)
        def paged = repository.getRequiredMethod("search", io.micronaut.data.model.search.SearchQuery, io.micronaut.data.model.Pageable)

        then:
        method.stringValue(DataMethod, DataMethod.META_MEMBER_INTERCEPTOR).get() == "io.micronaut.data.runtime.intercept.criteria.SearchQueryInterceptor"
        method.stringValue(DataMethod, DataMethod.META_MEMBER_RESULT_TYPE).get() == "io.micronaut.data.model.vector.search.SearchResults"
        !method.hasAnnotation(io.micronaut.data.annotation.Query)
        paged.stringValue(DataMethod, DataMethod.META_MEMBER_INTERCEPTOR).get() == "io.micronaut.data.runtime.intercept.criteria.SearchQueryInterceptor"
        paged.stringValue(DataMethod, io.micronaut.data.annotation.TypeRole.PAGEABLE).isPresent()
    }

    void "highlightable search fields are projected by search methods returning SearchResults"() {
        given:
        def repository = buildRepository('test.PostRepository', """
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.annotation.search.SearchField;
import io.micronaut.data.annotation.search.Searchable;
import io.micronaut.data.model.vector.search.SearchResults;

@MappedEntity
@Searchable
class Post {
    @Id
    @GeneratedValue
    private Long id;
    @SearchField(highlightable = true)
    private String title;
    @SearchField
    private String body;
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getBody() { return body; }
    public void setBody(String body) { this.body = body; }
}

@JdbcRepository(dialect = Dialect.POSTGRES)
interface PostRepository extends CrudRepository<Post, Long> {
    SearchResults<Post> searchBySearch(String q);
    SearchResults<Post> searchByBodySearch(String q);
    List<Post> findBySearch(String q);
}
""")

        expect:
        getQuery(repository.getRequiredMethod("searchBySearch", String)).contains("ts_headline('english',post_.\"title\",to_tsquery('english',?),'StartSel=<em>,StopSel=</em>') AS mn_hl_title")
        !getQuery(repository.getRequiredMethod("searchBySearch", String)).contains("mn_hl_body")
        // only the searched fields are highlighted
        !getQuery(repository.getRequiredMethod("searchByBodySearch", String)).contains("mn_hl_")
        !getQuery(repository.getRequiredMethod("findBySearch", String)).contains("ts_headline")
    }

    void "reactive and async search(SearchQuery) use the reactive and async interceptors"() {
        given:
        def reactive = buildRepository('test.ReactiveArticleRepository', """
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.repository.reactive.ReactorSearchRepository;
${ENTITY}
@JdbcRepository(dialect = Dialect.POSTGRES)
interface ReactiveArticleRepository extends ReactorSearchRepository<Article, Long> {
}
""")
        def async = buildRepository('test.AsyncArticleRepository', """
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.repository.async.AsyncSearchRepository;
${ENTITY}
@JdbcRepository(dialect = Dialect.POSTGRES)
interface AsyncArticleRepository extends AsyncSearchRepository<Article, Long> {
}
""")

        expect:
        reactive.getRequiredMethod("search", io.micronaut.data.model.search.SearchQuery).stringValue(DataMethod, DataMethod.META_MEMBER_INTERCEPTOR).get() == "io.micronaut.data.runtime.intercept.criteria.reactive.SearchQueryReactiveInterceptor"
        async.getRequiredMethod("search", io.micronaut.data.model.search.SearchQuery).stringValue(DataMethod, DataMethod.META_MEMBER_INTERCEPTOR).get() == "io.micronaut.data.runtime.intercept.criteria.async.SearchQueryAsyncInterceptor"
        reactive.getRequiredMethod("search", String).stringValue(DataMethod, DataMethod.META_MEMBER_INTERCEPTOR).get().contains("Reactive")
        async.getRequiredMethod("search", String).stringValue(DataMethod, DataMethod.META_MEMBER_INTERCEPTOR).get().contains("Async")
        reactive.getRequiredMethod("countHits", String).stringValue(DataMethod, DataMethod.META_MEMBER_INTERCEPTOR).get().contains("Reactive")
        async.getRequiredMethod("countHits", String).stringValue(DataMethod, DataMethod.META_MEMBER_INTERCEPTOR).get().contains("Async")
    }

    void "a Score parameter restricts the relevance score"() {
        given:
        def repository = articleRepository("""
    SearchResults<Article> searchByTitleSearch(String q, io.micronaut.data.model.vector.search.Score minScore);
    List<Article> findBySearch(String q, io.micronaut.data.model.vector.search.Score minScore);
""")
        def search = repository.getRequiredMethod("searchByTitleSearch", String, io.micronaut.data.model.vector.search.Score)
        def find = repository.getRequiredMethod("findBySearch", String, io.micronaut.data.model.vector.search.Score)

        expect:
        getQuery(search).contains("WHERE (setweight(to_tsvector('english',article_.\"title\"),'A') @@ to_tsquery('english',?) AND ts_rank_cd(setweight(to_tsvector('english',article_.\"title\"),'A'),to_tsquery('english',?)) >= ?)")
        getQuery(find).contains(" >= ?)")
        // the score projection, the match, the score restriction and the order bind the query; the Score binds plainly
        converters(search).size() == 5
        converters(search).count { it == null } == 1
        converters(search).count { it == PostgresTsQueryConverter.Any.name } == 4
    }

    void "a @Language property selects the text search configuration per row"() {
        given:
        def repository = buildRepository('test.PostRepository', """
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.annotation.search.Language;
import io.micronaut.data.annotation.search.SearchField;
import io.micronaut.data.annotation.search.Searchable;
import io.micronaut.data.model.vector.search.SearchResults;

@MappedEntity
@Searchable(language = "german")
class Post {
    @Id
    @GeneratedValue
    private Long id;
    @SearchField
    private String title;
    @Language
    private String lang;
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getLang() { return lang; }
    public void setLang(String lang) { this.lang = lang; }
}

@JdbcRepository(dialect = Dialect.POSTGRES)
interface PostRepository extends CrudRepository<Post, Long> {
    SearchResults<Post> searchBySearch(String q);
}
""")

        expect:
        getQuery(repository.getRequiredMethod("searchBySearch", String)).contains("to_tsvector(coalesce(post_.\"lang\",'german')::regconfig,post_.\"title\") @@ to_tsquery('german',?)")
    }
}
