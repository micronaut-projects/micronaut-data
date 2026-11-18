package io.micronaut.data.search.elasticsearch

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.data.annotation.Query
import io.micronaut.data.intercept.annotation.DataMethod
import io.micronaut.data.intercept.annotation.DataMethodQuery
import io.micronaut.data.intercept.annotation.DataMethodQueryParameter
import io.micronaut.data.search.document.convert.SimpleQueryStringConverter
import io.micronaut.inject.BeanDefinition
import io.micronaut.inject.ExecutableMethod
import io.micronaut.inject.writer.BeanDefinitionVisitor

class BuildElasticsearchQuerySpec extends AbstractTypeElementSpec {

    private static jakarta.json.JsonObject json(String text) {
        jakarta.json.Json.createReader(new StringReader(text)).withCloseable { it.readObject() }
    }

    private BeanDefinition<?> articleRepository(String methods) {
        buildBeanDefinition('test.ArticleRepository' + BeanDefinitionVisitor.PROXY_SUFFIX, """
package test;

import io.micronaut.data.annotation.*;
import io.micronaut.data.annotation.search.SearchField;
import io.micronaut.data.annotation.search.SearchFieldType;
import io.micronaut.data.annotation.search.SearchWeight;
import io.micronaut.data.annotation.search.Searchable;
import io.micronaut.data.model.Pageable;
import io.micronaut.data.model.vector.search.SearchResults;
import io.micronaut.data.repository.CrudRepository;
import io.micronaut.data.repository.SearchRepository;
import io.micronaut.data.search.elasticsearch.annotation.ElasticsearchRepository;
import java.util.List;

@MappedEntity("articles")
@Searchable(language = "german")
class Article {
    @Id
    private String id;
    @SearchField(weight = SearchWeight.HIGH)
    private String title;
    @SearchField
    private String body;
    @SearchField(type = SearchFieldType.KEYWORD)
    private String category;
    private String author;
    private int pages;
    private int views;
    private boolean published;
    @jakarta.persistence.Column(length = 3)
    private io.micronaut.data.model.vector.FloatVector embedding;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getBody() { return body; }
    public void setBody(String body) { this.body = body; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public String getAuthor() { return author; }
    public void setAuthor(String author) { this.author = author; }
    public int getPages() { return pages; }
    public void setPages(int pages) { this.pages = pages; }
    public int getViews() { return views; }
    public void setViews(int views) { this.views = views; }
    public boolean isPublished() { return published; }
    public void setPublished(boolean published) { this.published = published; }
    public io.micronaut.data.model.vector.FloatVector getEmbedding() { return embedding; }
    public void setEmbedding(io.micronaut.data.model.vector.FloatVector embedding) { this.embedding = embedding; }
}

@ElasticsearchRepository
interface ArticleRepository extends CrudRepository<Article, String>, SearchRepository<Article, String> {
${methods}
}
""")
    }

    void "comparisons between properties, case-folded ranges and literal LIKE patterns render scripts and wildcards"() {
        given:
        def repository = articleRepository("""
    @jakarta.data.repository.Query("WHERE title = author")
    List<Article> sameTitleAndAuthor();
    @jakarta.data.repository.Query("WHERE title < author")
    List<Article> titleBeforeAuthor();
    @jakarta.data.repository.Query("WHERE pages > views")
    List<Article> readMoreThanViewed();
    @jakarta.data.repository.Query("WHERE LOWER(author) BETWEEN :low AND :high")
    List<Article> authorsBetweenIgnoringCase(String low, String high);
    @jakarta.data.repository.Query("WHERE author LIKE 'J!_%' ESCAPE '!'")
    List<Article> authorsStartingWithJUnderscore();
    List<Article> findByAuthorIlike(String pattern);
    List<Article> findByAuthorNotLike(String pattern);
""")

        expect:
        query(repository.getRequiredMethod("sameTitleAndAuthor")).contains(
                "doc['title.keyword'].size() != 0 && doc['author'].size() != 0 && doc['title.keyword'].value == doc['author'].value")
        query(repository.getRequiredMethod("titleBeforeAuthor")).contains(
                "doc['title.keyword'].value.compareTo(doc['author'].value) < 0")
        query(repository.getRequiredMethod("readMoreThanViewed")).contains("doc['pages'].value > doc['views'].value")
        query(repository.getRequiredMethod("readMoreThanViewed")).contains('"lang":"painless"')
        query(repository.getRequiredMethod("authorsBetweenIgnoringCase", String, String)).contains(".toLowerCase()")
        query(repository.getRequiredMethod("authorsBetweenIgnoringCase", String, String)).contains('"params":{"from":{"$mn_qp":0},"to":{"$mn_qp":1}}')
        query(repository.getRequiredMethod("authorsStartingWithJUnderscore")) ==
                '{"query":{"wildcard":{"author":{"value":{"$mn_qp":-1,"$mn_wildcard":"like:!","value":"J!_%"}}}}}'
        query(repository.getRequiredMethod("findByAuthorIlike", String)) ==
                '{"query":{"wildcard":{"author":{"value":{"$mn_qp":0,"$mn_wildcard":"like_ignore_case"},"case_insensitive":true}}}}'
        query(repository.getRequiredMethod("findByAuthorNotLike", String)) ==
                '{"query":{"bool":{"must_not":{"wildcard":{"author":{"value":{"$mn_qp":0,"$mn_wildcard":"like"}}}}}}}'
    }

    void "a negated script comparison renders the opposite comparison, and case functions compare in scripts"() {
        given:
        def repository = articleRepository("""
    @jakarta.data.repository.Query("WHERE NOT (pages = views)")
    List<Article> pagesNotEqualToViews();
    @jakarta.data.repository.Query("WHERE pages <> views")
    List<Article> pagesDifferentFromViews();
    @jakarta.data.repository.Query("WHERE NOT (title < author)")
    List<Article> titleNotBeforeAuthor();
    @jakarta.data.repository.Query("WHERE LOWER(author) > :name")
    List<Article> lowerAuthorAfter(String name);
    @jakarta.data.repository.Query("WHERE UPPER(author) = UPPER(:name)")
    List<Article> authorIgnoringCase(String name);
    @jakarta.data.repository.Query("WHERE LOWER(author) NOT BETWEEN :low AND :high")
    List<Article> authorsOutsideIgnoringCase(String low, String high);
""")

        expect: "the existence checks stay, so a document missing a property matches neither form"
        query(repository.getRequiredMethod("pagesNotEqualToViews")) ==
                '{"query":{"script":{"script":{"source":"doc[\'pages\'].size() != 0 && doc[\'views\'].size() != 0 && doc[\'pages\'].value != doc[\'views\'].value","lang":"painless"}}}}'
        query(repository.getRequiredMethod("pagesDifferentFromViews")) == query(repository.getRequiredMethod("pagesNotEqualToViews"))
        query(repository.getRequiredMethod("titleNotBeforeAuthor")).contains("doc['title.keyword'].value.compareTo(doc['author'].value) >= 0")
        !query(repository.getRequiredMethod("titleNotBeforeAuthor")).contains("must_not")

        and: "a value is folded only when the query wraps it in a case function"
        query(repository.getRequiredMethod("lowerAuthorAfter", String)).contains("doc['author'].value.toLowerCase().compareTo(params.value.toString()) > 0")
        query(repository.getRequiredMethod("authorIgnoringCase", String)).contains("doc['author'].value.toUpperCase().compareTo(params.value.toString().toUpperCase()) == 0")
        query(repository.getRequiredMethod("authorsOutsideIgnoringCase", String, String)).contains("return !(v.compareTo(params.from.toString()) >= 0")
        !query(repository.getRequiredMethod("authorsOutsideIgnoringCase", String, String)).contains("must_not")
    }

    private static String query(ExecutableMethod<?, ?> method) {
        // a Jakarta Data query keeps its text as the @Query value and its rendering in the queries of @DataMethod,
        // a finder renders into the @Query value
        def rendered = method.getAnnotation(DataMethod).getAnnotations(DataMethod.META_MEMBER_QUERIES, DataMethodQuery)
        rendered.isEmpty() ? method.getAnnotation(Query).stringValue().orElse(null) : rendered.first().stringValue().orElse(null)
    }

    private static List<String> converters(ExecutableMethod<?, ?> method) {
        method.getAnnotation(DataMethod).getAnnotations(DataMethod.META_MEMBER_PARAMETERS, DataMethodQueryParameter)
                .collect { it.stringValue(DataMethodQueryParameter.META_MEMBER_CONVERTER).orElse(null) }
    }

    void "predicates render term, range, wildcard and bool queries"() {
        given:
        def repository = articleRepository("""
    List<Article> findByAuthor(String author);
    List<Article> findByTitle(String title);
    List<Article> findByCategory(String category);
    List<Article> findByPagesGreaterThan(int pages);
    List<Article> findByPagesBetween(int from, int to);
    List<Article> findByTitleBetween(String from, String to);
    List<Article> findByTitleGreaterThan(String title);
    List<Article> findByAuthorStartsWith(String prefix);
    List<Article> findByAuthorInList(List<String> authors);
    List<Article> findByAuthorIsNull();
    List<Article> findByPublishedTrue();
    List<Article> findByAuthorOrPagesLessThan(String author, int pages);
    List<Article> findByAuthorAndPublishedFalseOrderByPagesDesc(String author);
    List<Article> findTop3ByAuthorNotEqual(String author);
""")

        expect:
        query(repository.getRequiredMethod("findByAuthor", String)) == '{"query":{"term":{"author":{"$mn_qp":0}}}}'
        query(repository.getRequiredMethod("findByTitle", String)) == '{"query":{"term":{"title.keyword":{"$mn_qp":0}}}}'
        query(repository.getRequiredMethod("findByCategory", String)) == '{"query":{"term":{"category":{"$mn_qp":0}}}}'
        query(repository.getRequiredMethod("findByPagesGreaterThan", int)) == '{"query":{"range":{"pages":{"gt":{"$mn_qp":0}}}}}'
        query(repository.getRequiredMethod("findByPagesBetween", int, int)) == '{"query":{"range":{"pages":{"gte":{"$mn_qp":0},"lte":{"$mn_qp":1}}}}}'
        query(repository.getRequiredMethod("findByTitleBetween", String, String)) == '{"query":{"range":{"title.keyword":{"gte":{"$mn_qp":0},"lte":{"$mn_qp":1}}}}}'
        query(repository.getRequiredMethod("findByTitleGreaterThan", String)) == '{"query":{"range":{"title.keyword":{"gt":{"$mn_qp":0}}}}}'
        query(repository.getRequiredMethod("findByAuthorStartsWith", String)) == '{"query":{"wildcard":{"author":{"value":{"$mn_qp":0,"$mn_wildcard":"starts_with"}}}}}'
        query(repository.getRequiredMethod("findByAuthorInList", List)) == '{"query":{"terms":{"author":[{"$mn_qp":0}]}}}'
        query(repository.getRequiredMethod("findByAuthorIsNull")) == '{"query":{"bool":{"must_not":{"exists":{"field":"author"}}}}}'
        query(repository.getRequiredMethod("findByPublishedTrue")) == '{"query":{"term":{"published":true}}}'
        query(repository.getRequiredMethod("findByAuthorOrPagesLessThan", String, int)) == '{"query":{"bool":{"should":[{"term":{"author":{"$mn_qp":0}}},{"range":{"pages":{"lt":{"$mn_qp":1}}}}],"minimum_should_match":1}}}'
        query(repository.getRequiredMethod("findByAuthorAndPublishedFalseOrderByPagesDesc", String)) == '{"query":{"bool":{"must":[{"term":{"author":{"$mn_qp":0}}},{"term":{"published":false}}]}},"sort":[{"pages":{"order":"desc"}}]}'
        query(repository.getRequiredMethod("findTop3ByAuthorNotEqual", String)) == '{"query":{"bool":{"must_not":{"term":{"author":{"$mn_qp":0}}}}},"size":3}'
    }

    void "the native syntax renders query_string without leading wildcards"() {
        given:
        def repository = articleRepository("""
    List<Article> findByTitleSearchNative(String q);
""")

        expect:
        query(repository.getRequiredMethod("findByTitleSearchNative", String)).contains('"query_string":{')
        query(repository.getRequiredMethod("findByTitleSearchNative", String)).contains('"allow_leading_wildcard":false')
    }

    void "search keywords render simple_query_string over the weighted fields"() {
        given:
        def repository = articleRepository("""
    List<Article> findByTitleSearch(String q);
    SearchResults<Article> searchByTitleSearchAll(String q);
    List<Article> findByBodySearchPhrase(String q);
    List<Article> findByTitleSearchPrefix(String q);
    List<Article> findByTitleSearchFuzzy(String q);
""")
        def search = repository.getRequiredMethod("search", String)

        expect:
        query(repository.getRequiredMethod("findByTitleSearch", String)) == '{"query":{"simple_query_string":{"query":{"$mn_qp":0},"fields":["title^10"],"default_operator":"and"}}}'
        converters(repository.getRequiredMethod("findByTitleSearch", String)) == [SimpleQueryStringConverter.Any.name]
        query(repository.getRequiredMethod("searchByTitleSearchAll", String)) == '{"query":{"simple_query_string":{"query":{"$mn_qp":0},"fields":["title^10"],"default_operator":"and"}},"sort":[{"_score":{"order":"desc"}}]}'
        converters(repository.getRequiredMethod("findByBodySearchPhrase", String)) == [SimpleQueryStringConverter.Phrase.name]
        query(repository.getRequiredMethod("findByTitleSearchPrefix", String)) == '{"query":{"simple_query_string":{"query":{"$mn_qp":0},"fields":["title^10"],"default_operator":"and","analyze_wildcard":true}}}'
        converters(repository.getRequiredMethod("findByTitleSearchFuzzy", String)) == [SimpleQueryStringConverter.Fuzzy.name]
        query(search) == '{"query":{"simple_query_string":{"query":{"$mn_qp":0},"fields":["title^10","body","category"],"default_operator":"and"}},"sort":[{"_score":{"order":"desc"}}]}'
        query(repository.getRequiredMethod("countHits", String)) == '{"query":{"simple_query_string":{"query":{"$mn_qp":0},"fields":["title^10","body","category"],"default_operator":"and"}},"$mn_count":true}'
    }

    void "a Score parameter becomes the min_score of the request"() {
        given:
        def repository = articleRepository("""
    SearchResults<Article> searchByTitleSearch(String q, io.micronaut.data.model.vector.search.Score minScore);
""")

        expect:
        query(repository.getRequiredMethod("searchByTitleSearch", String, io.micronaut.data.model.vector.search.Score)) == '{"query":{"simple_query_string":{"query":{"$mn_qp":0},"fields":["title^10"],"default_operator":"and"}},"min_score":{"$mn_qp":1},"sort":[{"_score":{"order":"desc"}}]}'
    }

    void "derived operators render wildcard, regexp, range, emptiness and negated queries"() {
        given:
        def repository = articleRepository("""
    List<Article> findByAuthorStartsWith(String prefix);
    List<Article> findByAuthorStartsWithIgnoreCase(String prefix);
    List<Article> findByAuthorEndsWith(String suffix);
    List<Article> findByAuthorContains(String part);
    List<Article> findByAuthorLike(String pattern);
    List<Article> findByAuthorIgnoreCase(String author);
    List<Article> findByAuthorRegex(String pattern);
    List<Article> findByPagesGreaterThanEqualsAndPagesLessThanEquals(int min, int max);
    List<Article> findByAuthorIsEmpty();
    List<Article> findByAuthorIsNotEmpty();
    List<Article> findByAuthorOrCategory(String author, String category);
    List<Article> findByAuthorNotInList(List<String> authors);
    List<Article> findByIdInList(List<String> ids);
    List<Article> findByPublishedTrue();
    List<Article> findByAuthorNotEqual(String author);
    List<Article> findByAuthorIsNull();
    List<Article> findByAuthorIsNotNull();
    List<Article> findByAuthorOrderByPagesAsc(String author);
""")

        expect:
        json(query(repository.getRequiredMethod(method, *types))) == json(expected)

        where:
        method                                               | types                  | expected
        "findByAuthorStartsWith"                             | [String]               | '{"query":{"wildcard":{"author":{"value":{"$mn_qp":0,"$mn_wildcard":"starts_with"}}}}}'
        "findByAuthorStartsWithIgnoreCase"                   | [String]               | '{"query":{"wildcard":{"author":{"value":{"$mn_qp":0,"$mn_wildcard":"starts_with_ignore_case"},"case_insensitive":true}}}}'
        "findByAuthorEndsWith"                               | [String]               | '{"query":{"wildcard":{"author":{"value":{"$mn_qp":0,"$mn_wildcard":"ends_with"}}}}}'
        "findByAuthorContains"                               | [String]               | '{"query":{"wildcard":{"author":{"value":{"$mn_qp":0,"$mn_wildcard":"contains"}}}}}'
        "findByAuthorLike"                                   | [String]               | '{"query":{"wildcard":{"author":{"value":{"$mn_qp":0,"$mn_wildcard":"like"}}}}}'
        "findByAuthorIgnoreCase"                             | [String]               | '{"query":{"term":{"author":{"value":{"$mn_qp":0},"case_insensitive":true}}}}'
        "findByAuthorRegex"                                  | [String]               | '{"query":{"regexp":{"author":{"$mn_qp":0}}}}'
        "findByPagesGreaterThanEqualsAndPagesLessThanEquals" | [int, int]             | '{"query":{"bool":{"must":[{"range":{"pages":{"gte":{"$mn_qp":0}}}},{"range":{"pages":{"lte":{"$mn_qp":1}}}}]}}}'
        "findByAuthorIsEmpty"                                | []                     | '{"query":{"bool":{"should":[{"bool":{"must_not":{"exists":{"field":"author"}}}},{"term":{"author":""}}],"minimum_should_match":1}}}'
        "findByAuthorIsNotEmpty"                             | []                     | '{"query":{"bool":{"must_not":{"bool":{"should":[{"bool":{"must_not":{"exists":{"field":"author"}}}},{"term":{"author":""}}],"minimum_should_match":1}}}}}'
        "findByAuthorOrCategory"                             | [String, String]       | '{"query":{"bool":{"should":[{"term":{"author":{"$mn_qp":0}}},{"term":{"category":{"$mn_qp":1}}}],"minimum_should_match":1}}}'
        "findByAuthorNotInList"                              | [List]                 | '{"query":{"bool":{"must_not":{"terms":{"author":[{"$mn_qp":0}]}}}}}'
        "findByIdInList"                                     | [List]                 | '{"query":{"ids":{"values":[{"$mn_qp":0}]}}}'
        "findByPublishedTrue"                                | []                     | '{"query":{"term":{"published":true}}}'
        "findByAuthorNotEqual"                               | [String]               | '{"query":{"bool":{"must_not":{"term":{"author":{"$mn_qp":0}}}}}}'
        "findByAuthorIsNull"                                 | []                     | '{"query":{"bool":{"must_not":{"exists":{"field":"author"}}}}}'
        "findByAuthorIsNotNull"                              | []                     | '{"query":{"exists":{"field":"author"}}}'
        "findByAuthorOrderByPagesAsc"                        | [String]               | '{"query":{"term":{"author":{"$mn_qp":0}}},"sort":[{"pages":{"order":"asc"}}]}'
    }

    void "the query string converter renders the portable grammar"() {
        expect:
        SimpleQueryStringConverter.render("bake coffee -cake", io.micronaut.data.model.search.MatchMode.ANY) == "(bake | coffee) -cake"
        SimpleQueryStringConverter.render("bake coffee -cake", io.micronaut.data.model.search.MatchMode.ALL) == "bake coffee -cake"
        SimpleQueryStringConverter.render("-cake", io.micronaut.data.model.search.MatchMode.ANY) == "-cake"
        SimpleQueryStringConverter.render('"ken burns" tea OR coffee', io.micronaut.data.model.search.MatchMode.ANY) == '("ken burns" | tea) | (coffee)'
        SimpleQueryStringConverter.render("mic dat", io.micronaut.data.model.search.MatchMode.PREFIX) == "mic* dat*"
        SimpleQueryStringConverter.render("coffe", io.micronaut.data.model.search.MatchMode.FUZZY) == "coffe~1"
        SimpleQueryStringConverter.render("coffe", io.micronaut.data.model.search.MatchMode.FUZZY, 2) == "coffe~2"
        SimpleQueryStringConverter.converterFor(io.micronaut.data.model.search.MatchMode.FUZZY, 2) == SimpleQueryStringConverter.Fuzzy2.name
        SimpleQueryStringConverter.converterFor(io.micronaut.data.model.search.MatchMode.FUZZY, 0) == SimpleQueryStringConverter.Fuzzy.name
        SimpleQueryStringConverter.render("fat rats", io.micronaut.data.model.search.MatchMode.PHRASE) == '"fat rats"'
        SimpleQueryStringConverter.render("a+b (c)", io.micronaut.data.model.search.MatchMode.ANY) == 'a\\+b | \\(c\\)'
    }

    void "a vector Near predicate renders a knn query with the distance as a similarity threshold"() {
        given:
        def repository = articleRepository("""
    SearchResults<Article> searchByEmbeddingNear(io.micronaut.data.model.vector.Vector vector, io.micronaut.data.model.vector.search.Score maxDistance);
    List<Article> findTop5ByEmbeddingNear(io.micronaut.data.model.vector.Vector vector, Double maxDistance);
""")

        expect:
        query(repository.getRequiredMethod("searchByEmbeddingNear", io.micronaut.data.model.vector.Vector, io.micronaut.data.model.vector.search.Score)) ==
            '{"query":{"knn":{"field":"embedding","query_vector":{"$mn_qp":0},"k":10,"num_candidates":100,"similarity":{"$mn_qp":1,"$mn_wildcard":"cosine_similarity"}}},"sort":[{"_score":{"order":"desc"}}]}'
        query(repository.getRequiredMethod("findTop5ByEmbeddingNear", io.micronaut.data.model.vector.Vector, Double)) ==
            '{"query":{"knn":{"field":"embedding","query_vector":{"$mn_qp":0},"k":5,"num_candidates":100,"similarity":{"$mn_qp":1,"$mn_wildcard":"cosine_similarity"}}},"sort":[{"_score":{"order":"desc"}}],"size":5}'
    }

    void "the other conditions of a vector search become the filter of the knn query"() {
        given:
        def repository = articleRepository("""
    List<Article> findTop5ByCategoryAndEmbeddingNear(String category, io.micronaut.data.model.vector.Vector vector, Double maxDistance);
""")

        expect:
        query(repository.getRequiredMethod("findTop5ByCategoryAndEmbeddingNear", String, io.micronaut.data.model.vector.Vector, Double)) ==
            '{"query":{"knn":{"field":"embedding","query_vector":{"$mn_qp":1},"k":5,"num_candidates":100,"similarity":{"$mn_qp":2,"$mn_wildcard":"cosine_similarity"},"filter":{"term":{"category":{"$mn_qp":0}}}}},"sort":[{"_score":{"order":"desc"}}],"size":5}'
    }

    void "a delete by a vector predicate is rejected at compile time"() {
        when:
        articleRepository("""
    long deleteByEmbeddingNear(io.micronaut.data.model.vector.Vector vector, Double maxDistance);
""")

        then:
        def e = thrown(RuntimeException)
        e.message.contains("A vector search cannot select the documents of a delete or an update")
    }

    void "a colon in a @Query string value is a parameter reference unless escaped with a backslash"() {
        when:
        articleRepository("""
    @Query("{\\"query\\": {\\"match\\": {\\"body\\": \\"at 10:30\\"}}}")
    List<Article> atHalfPastTen();
""")

        then:
        def e = thrown(RuntimeException)
        e.message.contains("No method parameter found for named Query parameter: 30")

        when:
        def method = articleRepository("""
    @Query("{\\"query\\": {\\"bool\\": {\\"must\\": {\\"match\\": {\\"body\\": \\"at 10\\\\:30\\"}}, \\"filter\\": {\\"term\\": {\\"author\\": \\":author\\"}}}}}")
    List<Article> atHalfPastTen(String author);
""").getRequiredMethod("atHalfPastTen", String)
        def body = json(method.getAnnotation(Query).stringValue(DataMethod.META_MEMBER_RAW_QUERY).get())

        then:
        body.getJsonObject("query").getJsonObject("bool").getJsonObject("must").getJsonObject("match").getString("body") == "at 10:30"
        body.getJsonObject("query").getJsonObject("bool").getJsonObject("filter").getJsonObject("term").getString("author") == ":author"
    }
}
