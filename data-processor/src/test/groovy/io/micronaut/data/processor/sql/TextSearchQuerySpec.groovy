package io.micronaut.data.processor.sql

import io.micronaut.data.intercept.annotation.DataMethod
import io.micronaut.data.intercept.annotation.DataMethodQuery
import io.micronaut.data.intercept.annotation.DataMethodQueryParameter
import io.micronaut.data.model.Pageable
import io.micronaut.data.model.Sort
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

    void "SearchRepository search with a pageable orders by the score unless the pageable is sorted"() {
        given:
        def repository = articleRepository("""
    SearchResults<Article> searchByTitleSearch(String q, Sort sort);
    SearchResults<Article> searchByTitleSearchAll(String q, Pageable pageable);
    List<Article> findByTitleSearch(String q, Pageable pageable);
""")
        def search = repository.getRequiredMethod("search", String, Pageable)
        def sorted = repository.getRequiredMethod("searchByTitleSearch", String, Sort)
        def paged = repository.getRequiredMethod("searchByTitleSearchAll", String, Pageable)

        expect: 'the query leaves the order to the runtime, which falls back to the score for an unsorted pageable or sort'
        !getQuery(search).contains("ORDER BY")
        getQuery(search).contains(" AS mn_score FROM ")
        search.getAnnotation(DataMethod).isTrue(DataMethodQuery.META_MEMBER_SCORE_ORDER)
        !getQuery(sorted).contains("ORDER BY")
        sorted.getAnnotation(DataMethod).isTrue(DataMethodQuery.META_MEMBER_SCORE_ORDER)
        paged.getAnnotation(DataMethod).isTrue(DataMethodQuery.META_MEMBER_SCORE_ORDER)

        and: 'a query that orders itself, or has no score, is not marked'
        !repository.getRequiredMethod("search", String).getAnnotation(DataMethod).isTrue(DataMethodQuery.META_MEMBER_SCORE_ORDER)
        !repository.getRequiredMethod("findByTitleSearch", String, Pageable).getAnnotation(DataMethod).isTrue(DataMethodQuery.META_MEMBER_SCORE_ORDER)
    }

    void "a property whose name ends in Search keeps its equality meaning"() {
        given:
        def repository = buildRepository('test.QueryRepository', """
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;

@MappedEntity
class SavedQuery {
    @Id
    @GeneratedValue
    private Long id;
    private String savedSearch;
    private String search;
    private String searchAll;
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getSavedSearch() { return savedSearch; }
    public void setSavedSearch(String savedSearch) { this.savedSearch = savedSearch; }
    public String getSearch() { return search; }
    public void setSearch(String search) { this.search = search; }
    public String getSearchAll() { return searchAll; }
    public void setSearchAll(String searchAll) { this.searchAll = searchAll; }
}

@JdbcRepository(dialect = Dialect.POSTGRES)
interface QueryRepository extends CrudRepository<SavedQuery, Long> {
    List<SavedQuery> findBySavedSearch(String savedSearch);
    List<SavedQuery> findBySearch(String search);
    List<SavedQuery> findBySearchAll(String searchAll);
    long countBySavedSearchAndSearch(String savedSearch, String search);
}
""")

        expect:
        getQuery(repository.getRequiredMethod("findBySavedSearch", String)) == 'SELECT saved_query_."id",saved_query_."saved_search",saved_query_."search",saved_query_."search_all" FROM "saved_query" saved_query_ WHERE (saved_query_."saved_search" = ?)'
        getQuery(repository.getRequiredMethod("findBySearch", String)).endsWith('WHERE (saved_query_."search" = ?)')
        getQuery(repository.getRequiredMethod("findBySearchAll", String)).endsWith('WHERE (saved_query_."search_all" = ?)')
        getQuery(repository.getRequiredMethod("countBySavedSearchAndSearch", String, String)).endsWith('WHERE (saved_query_."saved_search" = ? AND saved_query_."search" = ?)')
    }

    void "the search keyword applies when the property before it exists, also next to a property ending in Search"() {
        given:
        def repository = buildRepository('test.NoteRepository', """
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.annotation.search.SearchField;
import io.micronaut.data.annotation.search.Searchable;
import io.micronaut.data.model.vector.search.SearchResults;

@MappedEntity
@Searchable
class Note {
    @Id
    @GeneratedValue
    private Long id;
    @SearchField
    private String text;
    private String textSearch;
    private String savedSearch;
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getText() { return text; }
    public void setText(String text) { this.text = text; }
    public String getTextSearch() { return textSearch; }
    public void setTextSearch(String textSearch) { this.textSearch = textSearch; }
    public String getSavedSearch() { return savedSearch; }
    public void setSavedSearch(String savedSearch) { this.savedSearch = savedSearch; }
}

@JdbcRepository(dialect = Dialect.POSTGRES)
interface NoteRepository extends CrudRepository<Note, Long> {
    List<Note> findByTextSearch(String q);
    SearchResults<Note> searchBySavedSearchAndTextSearchAll(String savedSearch, String q);
}
""")
        def scored = repository.getRequiredMethod("searchBySavedSearchAndTextSearchAll", String, String)

        expect: 'an existing property before the keyword still makes it a text search'
        getQuery(repository.getRequiredMethod("findByTextSearch", String)).endsWith("WHERE (to_tsvector('english',note_.\"text\") @@ to_tsquery('english',?))")
        getQuery(scored).endsWith("WHERE (note_.\"saved_search\" = ? AND to_tsvector('english',note_.\"text\") @@ to_tsquery('english',?)) ORDER BY ts_rank_cd(to_tsvector('english',note_.\"text\"),to_tsquery('english',?)) DESC")
        converters(scored) == [PostgresTsQueryConverter.All.name, null, PostgresTsQueryConverter.All.name, PostgresTsQueryConverter.All.name]
    }

    void "a SearchResults finder scores by its first search predicate that is not negated"() {
        given:
        def repository = articleRepository("""
    SearchResults<Article> searchByTitleNotSearchAndBodySearchAll(String excluded, String q);
    List<Article> findByTitleNotSearch(String q);
""")
        def method = repository.getRequiredMethod("searchByTitleNotSearchAndBodySearchAll", String, String)

        expect:
        getQuery(method).endsWith("WHERE (NOT(setweight(to_tsvector('english',article_.\"title\"),'A') @@ to_tsquery('english',?)) AND setweight(to_tsvector('english',article_.\"body\"),'D') @@ to_tsquery('english',?)) ORDER BY ts_rank_cd(setweight(to_tsvector('english',article_.\"body\"),'D'),to_tsquery('english',?)) DESC")
        getQuery(repository.getRequiredMethod("findByTitleNotSearch", String)).contains("NOT(")
    }

    void "a SearchResults finder whose only search predicate is negated is rejected with a clear message"() {
        when:
        articleRepository("""
    SearchResults<Article> searchByTitleNotSearch(String q);
""")

        then:
        def e = thrown(RuntimeException)
        e.message.contains("A SearchResults finder cannot score its results by a negated search predicate")
    }

    @Unroll
    void "@SearchField on #description is rejected at compile time"() {
        when:
        buildRepository('test.PostRepository', """
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.annotation.search.SearchField;
import io.micronaut.data.annotation.search.Searchable;

@MappedEntity
${searchable ? '@Searchable' : ''}
class Post {
    @Id
    @GeneratedValue
    private Long id;
    @SearchField
    private ${type} value;
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public ${type} getValue() { return value; }
    public void setValue(${type} value) { this.value = value; }
}

@MappedEntity
class Author {
    @Id
    @GeneratedValue
    private Long id;
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
}

@JdbcRepository(dialect = Dialect.POSTGRES)
interface PostRepository extends CrudRepository<Post, Long> {
}
""")

        then:
        def e = thrown(RuntimeException)
        e.message.contains(message)

        where:
        description                    | type      | searchable | message
        "a number"                     | "Integer" | true       | "@SearchField property [value] of [test.Post] must be a text property, such as a String"
        "an association"               | "Author"  | true       | "@SearchField property [value] of [test.Post] must be a text property, such as a String"
        "an entity without @Searchable" | "String"  | false      | "@SearchField property [value] of [test.Post] is never searched: annotate the entity with @Searchable"
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
