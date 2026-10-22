package io.micronaut.data.document.processor

import io.micronaut.data.intercept.annotation.DataMethod
import io.micronaut.data.intercept.annotation.DataMethodQueryParameter
import io.micronaut.data.model.runtime.convert.MongoTextSearchConverter
import io.micronaut.inject.BeanDefinition
import io.micronaut.inject.ExecutableMethod

class BuildMongoTextSearchQuerySpec extends AbstractDataSpec {

    private BeanDefinition<?> articleRepository(String methods) {
        buildRepository('test.ArticleRepository', """
import io.micronaut.data.annotation.search.SearchField;
import io.micronaut.data.annotation.search.SearchWeight;
import io.micronaut.data.annotation.search.Searchable;
import io.micronaut.data.model.vector.search.SearchResults;
import io.micronaut.data.mongodb.annotation.MongoRepository;
import io.micronaut.data.repository.SearchRepository;

@MappedEntity
@Searchable(language = "german")
class Article {
    @Id
    @GeneratedValue
    private String id;
    @SearchField(weight = SearchWeight.HIGH)
    private String title;
    @SearchField
    private String body;
    private String author;

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

    void "a search keyword renders a \$text filter with the entity language"() {
        given:
        def repository = articleRepository("""
    List<Article> findByTitleSearch(String q);
    List<Article> findByTitleSearchAll(String q);
    List<Article> findBySearchPhraseAndAuthor(String q, String author);
""")

        expect:
        TestUtils.getQuery(repository.getRequiredMethod("findByTitleSearch", String)) == '{$text:{$search:{$mn_qp:0},$language:\'german\'}}'
        converters(repository.getRequiredMethod("findByTitleSearch", String)) == [MongoTextSearchConverter.Any.name]
        converters(repository.getRequiredMethod("findByTitleSearchAll", String)) == [MongoTextSearchConverter.All.name]
        TestUtils.getQuery(repository.getRequiredMethod("findBySearchPhraseAndAuthor", String, String)) == '{$and:[{$text:{$search:{$mn_qp:0},$language:\'german\'}},{author:{$eq:{$mn_qp:1}}}]}'
        converters(repository.getRequiredMethod("findBySearchPhraseAndAuthor", String, String)) == [MongoTextSearchConverter.Phrase.name, null]
    }

    void "SearchRepository search adds the text score and sorts by it"() {
        given:
        def repository = articleRepository("")
        def query = TestUtils.getQuery(repository.getRequiredMethod("search", String))

        expect:
        query.startsWith('[{$match:{$text:{$search:{$mn_qp:0},$language:\'german\'}}},{$addFields:{mn_score:{$meta:\'textScore\'}}},')
        query.contains('{$meta:\'textScore\'}')
        query.contains('$sort')
        TestUtils.getQuery(repository.getRequiredMethod("countHits", String)) == '[{$match:{$text:{$search:{$mn_qp:0},$language:\'german\'}}},{$count:\'result\'}]'
    }

    void "prefix and fuzzy searches are rejected at compile time"() {
        when:
        articleRepository("""
    List<Article> findByTitleSearchPrefix(String q);
""")

        then:
        def e = thrown(RuntimeException)
        e.message.contains("PREFIX is not supported by MongoDB")
    }
}
