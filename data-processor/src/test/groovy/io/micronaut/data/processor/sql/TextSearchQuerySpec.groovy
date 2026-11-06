package io.micronaut.data.processor.sql

import io.micronaut.data.intercept.annotation.DataMethod
import io.micronaut.data.intercept.annotation.DataMethodQueryParameter
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

    private BeanDefinition<?> articleRepository(String methods) {
        buildRepository('test.ArticleRepository', """
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.model.vector.search.SearchResults;
import io.micronaut.data.repository.SearchRepository;
${ENTITY}
@JdbcRepository(dialect = Dialect.POSTGRES)
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

    void "a language that is not a configuration name is rejected at compile time"() {
        when:
        buildRepository('test.NoteRepository', """
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.annotation.search.SearchField;
import io.micronaut.data.annotation.search.Searchable;

@MappedEntity
@Searchable(language = "english') OR 1=1 --")
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

        then:
        def e = thrown(RuntimeException)
        e.message.contains("A search language must be a text search configuration name")
    }

    void "fuzzy search is rejected on PostgreSQL at compile time"() {
        when:
        articleRepository("""
    List<Article> findByTitleSearchFuzzy(String q);
""")

        then:
        def e = thrown(RuntimeException)
        e.message.contains("FUZZY is not supported by PostgreSQL text search")
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

    void "a search(String) method that does not return SearchResults names the expected type"() {
        when:
        buildRepository('test.ArticleRepository', """
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
${ENTITY}
@JdbcRepository(dialect = Dialect.POSTGRES)
interface ArticleRepository extends CrudRepository<Article, Long> {
    List<Article> search(String q);
}
""")

        then:
        def e = thrown(RuntimeException)
        e.message.contains("A search(String) method must return SearchResults<Article>")
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
}
