package io.micronaut.data.document.processor

import io.micronaut.data.intercept.annotation.DataMethod
import io.micronaut.data.intercept.annotation.DataMethodQueryParameter
import io.micronaut.inject.BeanDefinition
import io.micronaut.inject.ExecutableMethod

/**
 * Entities annotated with {@code @MongoSearchIndex} render MongoDB Search ({@code $search}) stages.
 */
class BuildMongoSearchIndexQuerySpec extends AbstractDataSpec {

    private BeanDefinition<?> articleRepository(String methods, String searchIndex = 'name = "articles_search"') {
        buildRepository('test.ArticleRepository', """
import io.micronaut.data.annotation.search.SearchField;
import io.micronaut.data.annotation.search.SearchWeight;
import io.micronaut.data.annotation.search.Searchable;
import io.micronaut.data.model.vector.search.Score;
import io.micronaut.data.model.vector.search.SearchResults;
import io.micronaut.data.mongodb.annotation.MongoRepository;
import io.micronaut.data.mongodb.annotation.MongoSearchIndex;
import io.micronaut.data.repository.SearchRepository;

@MappedEntity
@Searchable(language = "german")
@MongoSearchIndex(${searchIndex})
class Article {
    @Id
    @GeneratedValue
    private String id;
    @SearchField(weight = SearchWeight.HIGH)
    private String title;
    @SearchField
    private String body;
    private String author;
    private io.micronaut.data.model.vector.FloatVector embedding;

    public io.micronaut.data.model.vector.FloatVector getEmbedding() { return embedding; }
    public void setEmbedding(io.micronaut.data.model.vector.FloatVector embedding) { this.embedding = embedding; }
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getBody() { return body; }
    public void setBody(String body) { this.body = body; }
    public String getAuthor() { return author; }
    public void setAuthor(String author) { this.author = author; }
}

@MongoRepository
interface ArticleRepository extends CrudRepository<Article, String>, SearchRepository<Article, String> {
${methods}
}
""")
    }

    private static List<String> converters(ExecutableMethod<?, ?> method) {
        method.getAnnotation(DataMethod).getAnnotations(DataMethod.META_MEMBER_PARAMETERS, DataMethodQueryParameter)
                .collect { it.stringValue(DataMethodQueryParameter.META_MEMBER_CONVERTER).orElse(null) }
    }

    void "a search keyword renders a \$search stage with an operator placeholder over the fields"() {
        given:
        def repository = articleRepository("""
    List<Article> findByTitleSearch(String q);
    List<Article> findBySearchAllAndAuthor(String q, String author);
    List<Article> findByBodySearchPrefix(String q);
    List<Article> findByTitleSearchFuzzyOrderByAuthor(String q);
""")

        expect: "the index weight becomes a boost and the search string is bound without a converter"
        TestUtils.getQuery(repository.getRequiredMethod("findByTitleSearch", String)) ==
                '[{$search:{index:\'articles_search\',compound:{must:[{$mn_qp:0,$mn_search:\'ANY\',$mn_paths:[\'title\'],$mn_boosts:{title:10}}]}}}]'
        converters(repository.getRequiredMethod("findByTitleSearch", String)) == [null]

        and: "the other predicates follow in a \$match without the empty search predicate"
        TestUtils.getQuery(repository.getRequiredMethod("findBySearchAllAndAuthor", String, String)) ==
                '[{$search:{index:\'articles_search\',compound:{must:[{$mn_qp:0,$mn_search:\'ALL\',$mn_paths:[\'title\',\'body\'],$mn_boosts:{title:10}}]}}},{$match:{author:{$eq:{$mn_qp:1}}}}]'

        and: "prefix and fuzzy modes are supported"
        TestUtils.getQuery(repository.getRequiredMethod("findByBodySearchPrefix", String)) ==
                '[{$search:{index:\'articles_search\',compound:{must:[{$mn_qp:0,$mn_search:\'PREFIX\',$mn_paths:[\'body\']}]}}}]'
        TestUtils.getQuery(repository.getRequiredMethod("findByTitleSearchFuzzyOrderByAuthor", String)) ==
                '[{$search:{index:\'articles_search\',compound:{must:[{$mn_qp:0,$mn_search:\'FUZZY\',$mn_paths:[\'title\'],$mn_boosts:{title:10}}]}}},{$sort:{author:1}}]'
    }

    void "SearchRepository search adds the search score and sorts by it"() {
        given:
        def repository = articleRepository("""
    SearchResults<Article> searchBySearch(String q, Score minScore);
""")
        def search = TestUtils.getQuery(repository.getRequiredMethod("search", String))

        expect:
        search.startsWith('[{$search:{index:\'articles_search\',compound:{must:[{$mn_qp:0,$mn_search:\'ANY\',$mn_paths:[\'title\',\'body\'],$mn_boosts:{title:10}}]}}},{$addFields:{mn_score:{$meta:\'searchScore\'}}},')
        search.contains('$sort')
        TestUtils.getQuery(repository.getRequiredMethod("countHits", String)) ==
                '[{$search:{index:\'articles_search\',compound:{must:[{$mn_qp:0,$mn_search:\'ANY\',$mn_paths:[\'title\',\'body\'],$mn_boosts:{title:10}}]}}},{$count:\'result\'}]'

        and: "a minimum score restricts on the added score field"
        TestUtils.getQuery(repository.getRequiredMethod("searchBySearch", String, io.micronaut.data.model.vector.search.Score)).startsWith(
                '[{$search:{index:\'articles_search\',compound:{must:[{$mn_qp:0,$mn_search:\'ANY\',$mn_paths:[\'title\',\'body\'],$mn_boosts:{title:10}}]}}},{$addFields:{mn_score:{$meta:\'searchScore\'}}},{$match:{mn_score:{$gte:{$mn_qp:1}}}},')
    }

    void "a vector predicate renders a \$vectorSearch stage with the distance as an added field"() {
        given:
        def repository = articleRepository("""
    SearchResults<Article> searchByEmbeddingNear(io.micronaut.data.model.vector.Vector vector, Score maxDistance);
    List<Article> findTop5ByEmbeddingNear(io.micronaut.data.model.vector.Vector vector, Double maxDistance);
""")
        def distance = '{$subtract:[2,{$multiply:[2,{$meta:\'vectorSearchScore\'}]}]}'

        expect:
        TestUtils.getQuery(repository.getRequiredMethod("searchByEmbeddingNear", io.micronaut.data.model.vector.Vector, io.micronaut.data.model.vector.search.Score)).startsWith(
                '[{$vectorSearch:{index:\'vector\',path:\'embedding\',queryVector:{$mn_qp:0},numCandidates:1000,limit:100}},{$addFields:{mn_score:' + distance + '}},{$match:{mn_score:{$lte:{$mn_qp:1}}}},')
        TestUtils.getQuery(repository.getRequiredMethod("findTop5ByEmbeddingNear", io.micronaut.data.model.vector.Vector, Double)).startsWith(
                '[{$vectorSearch:{index:\'vector\',path:\'embedding\',queryVector:{$mn_qp:0},numCandidates:100,limit:5}},{$addFields:{mn_score:' + distance + '}},{$match:{mn_score:{$lte:{$mn_qp:1}}}},')
    }

    void "a euclidean vector index maps the stage score back to the euclidean distance"() {
        given:
        def repository = articleRepository("""
    SearchResults<Article> searchByEmbeddingNear(io.micronaut.data.model.vector.Vector vector, Score maxDistance);
""", 'name = "articles_search", similarity = "euclidean"')
        def distance = '{$subtract:[{$divide:[1,{$meta:\'vectorSearchScore\'}]},1]}'

        expect:
        TestUtils.getQuery(repository.getRequiredMethod("searchByEmbeddingNear", io.micronaut.data.model.vector.Vector, io.micronaut.data.model.vector.search.Score)).startsWith(
                '[{$vectorSearch:{index:\'vector\',path:\'embedding\',queryVector:{$mn_qp:0},numCandidates:1000,limit:100}},{$addFields:{mn_score:' + distance + '}},{$match:{mn_score:{$lte:{$mn_qp:1}}}},')
    }

    void "a search predicate cannot be combined with OR"() {
        when:
        articleRepository("""
    List<Article> findByTitleSearchOrAuthor(String q, String author);
""")

        then:
        def e = thrown(RuntimeException)
        e.message.contains("cannot be combined with OR")
    }
}
