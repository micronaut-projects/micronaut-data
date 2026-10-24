package io.micronaut.data.search.elasticsearch

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.data.annotation.Query
import io.micronaut.data.intercept.annotation.DataMethod
import io.micronaut.data.intercept.annotation.DataMethodQueryParameter
import io.micronaut.data.search.document.convert.SimpleQueryStringConverter
import io.micronaut.inject.BeanDefinition
import io.micronaut.inject.ExecutableMethod
import io.micronaut.inject.writer.BeanDefinitionVisitor

class BuildElasticsearchQuerySpec extends AbstractTypeElementSpec {

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
    private boolean published;

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
    public boolean isPublished() { return published; }
    public void setPublished(boolean published) { this.published = published; }
}

@ElasticsearchRepository
interface ArticleRepository extends CrudRepository<Article, String>, SearchRepository<Article, String> {
${methods}
}
""")
    }

    private static String query(ExecutableMethod<?, ?> method) {
        method.getAnnotation(Query).stringValue().orElse(null)
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
        query(repository.getRequiredMethod("findByAuthorStartsWith", String)) == '{"query":{"wildcard":{"author":{"value":{"$mn_qp":0,"$mn_wildcard":"starts_with"}}}}}'
        query(repository.getRequiredMethod("findByAuthorInList", List)) == '{"query":{"terms":{"author":[{"$mn_qp":0}]}}}'
        query(repository.getRequiredMethod("findByAuthorIsNull")) == '{"query":{"bool":{"must_not":{"exists":{"field":"author"}}}}}'
        query(repository.getRequiredMethod("findByPublishedTrue")) == '{"query":{"term":{"published":true}}}'
        query(repository.getRequiredMethod("findByAuthorOrPagesLessThan", String, int)) == '{"query":{"bool":{"should":[{"term":{"author":{"$mn_qp":0}}},{"range":{"pages":{"lt":{"$mn_qp":1}}}}],"minimum_should_match":1}}}'
        query(repository.getRequiredMethod("findByAuthorAndPublishedFalseOrderByPagesDesc", String)) == '{"query":{"bool":{"must":[{"term":{"author":{"$mn_qp":0}}},{"term":{"published":false}}]}},"sort":[{"pages":{"order":"desc"}}]}'
        query(repository.getRequiredMethod("findTop3ByAuthorNotEqual", String)) == '{"query":{"bool":{"must_not":{"term":{"author":{"$mn_qp":0}}}}},"size":3}'
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
        query(repository.getRequiredMethod("countHits", String)) == '{"query":{"simple_query_string":{"query":{"$mn_qp":0},"fields":["title^10","body","category"],"default_operator":"and"}}}'
    }

    void "the query string converter renders the portable grammar"() {
        expect:
        SimpleQueryStringConverter.render("bake coffee -cake", io.micronaut.data.model.search.MatchMode.ANY) == "(bake | coffee) -cake"
        SimpleQueryStringConverter.render("bake coffee -cake", io.micronaut.data.model.search.MatchMode.ALL) == "bake coffee -cake"
        SimpleQueryStringConverter.render("-cake", io.micronaut.data.model.search.MatchMode.ANY) == "-cake"
        SimpleQueryStringConverter.render('"ken burns" tea OR coffee', io.micronaut.data.model.search.MatchMode.ANY) == '("ken burns" | tea) | (coffee)'
        SimpleQueryStringConverter.render("mic dat", io.micronaut.data.model.search.MatchMode.PREFIX) == "mic* dat*"
        SimpleQueryStringConverter.render("coffe", io.micronaut.data.model.search.MatchMode.FUZZY) == "coffe~1"
        SimpleQueryStringConverter.render("fat rats", io.micronaut.data.model.search.MatchMode.PHRASE) == '"fat rats"'
        SimpleQueryStringConverter.render("a+b (c)", io.micronaut.data.model.search.MatchMode.ANY) == 'a\\+b | \\(c\\)'
    }
}
