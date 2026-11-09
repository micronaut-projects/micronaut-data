package io.micronaut.data.runtime.intercept.criteria

import io.micronaut.aop.MethodInvocationContext
import io.micronaut.context.ApplicationContext
import io.micronaut.core.annotation.AnnotationClassValue
import io.micronaut.core.convert.ConversionService
import io.micronaut.core.convert.value.MutableConvertibleValuesMap
import io.micronaut.core.type.ReturnType
import io.micronaut.data.annotation.Repository
import io.micronaut.data.annotation.RepositoryConfiguration
import io.micronaut.data.intercept.RepositoryMethodKey
import io.micronaut.data.intercept.annotation.DataMethod
import io.micronaut.data.model.query.builder.sql.SqlQueryBuilder
import io.micronaut.data.model.runtime.PreparedQuery
import io.micronaut.data.model.runtime.RuntimeEntityRegistry
import io.micronaut.data.model.runtime.RuntimePersistentEntity
import io.micronaut.data.model.runtime.StoredQuery
import io.micronaut.data.model.search.SearchQuery
import io.micronaut.data.model.search.UnsupportedSearchFeatureException
import io.micronaut.data.model.vector.Vector
import io.micronaut.data.model.vector.search.Score
import io.micronaut.data.model.vector.search.SearchResult
import io.micronaut.data.model.vector.search.SearchResults
import io.micronaut.data.operations.RepositoryOperations
import io.micronaut.data.operations.async.AsyncCapableRepository
import io.micronaut.data.operations.async.AsyncRepositoryOperations
import io.micronaut.data.operations.reactive.ReactiveCapableRepository
import io.micronaut.data.operations.reactive.ReactiveRepositoryOperations
import io.micronaut.data.runtime.criteria.RuntimeCriteriaBuilder
import io.micronaut.data.runtime.criteria.SearchVectorArticle
import io.micronaut.data.runtime.intercept.criteria.async.SearchQueryAsyncInterceptor
import io.micronaut.data.runtime.intercept.criteria.reactive.SearchQueryReactiveInterceptor
import io.micronaut.data.runtime.query.PreparedQueryResolver
import io.micronaut.inject.ExecutableMethod
import io.micronaut.inject.annotation.MutableAnnotationMetadata
import reactor.core.publisher.Mono
import spock.lang.Specification

import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionException
import java.util.concurrent.ExecutionException

/**
 * Drives the hybrid path of the blocking, reactive and async {@code search(SearchQuery)} interceptors
 * against a PostgreSQL query builder and stubbed operations, so the failure handling of the vector
 * ranking and the fusion are checked without a database.
 */
class SearchQueryInterceptorSpec extends Specification {

    static final Vector QUERY_VECTOR = Vector.of([1f, 0f, 0f] as float[])
    static final String VECTOR_OPERATOR = ' <=> '

    RuntimeEntityRegistry registry = Stub(RuntimeEntityRegistry) {
        getEntity(_) >> { Class type -> new RuntimePersistentEntity<Object>(type) }
    }
    List<String> renderedQueries = []
    List<String> executedQueries = []
    Closure<Object> onRender = { String query -> null }
    Closure<Object> onExecute = { String query -> SearchResults.of(List.of()) }

    TestOperations operations = Stub(TestOperations) {
        getApplicationContext() >> Stub(ApplicationContext) {
            getBean(RuntimeCriteriaBuilder) >> new RuntimeCriteriaBuilder(registry)
        }
        getConversionService() >> ConversionService.SHARED
        getEntity(_) >> { Class type -> new RuntimePersistentEntity<Object>(type) }
        resolveQuery(_, _, _) >> { MethodInvocationContext context, StoredQuery storedQuery, pageable ->
            renderedQueries << storedQuery.query
            onRender.call(storedQuery.query)
            return Stub(PreparedQuery) { getQuery() >> storedQuery.query }
        }
        findOne(_ as PreparedQuery) >> { PreparedQuery preparedQuery -> execute(preparedQuery) }
        reactive() >> Stub(ReactiveRepositoryOperations) {
            findOne(_ as PreparedQuery) >> { PreparedQuery preparedQuery -> Mono.fromCallable { execute(preparedQuery) } }
        }
        async() >> Stub(AsyncRepositoryOperations) {
            findOne(_ as PreparedQuery) >> { PreparedQuery preparedQuery -> executeAsync(preparedQuery) }
        }
    }

    void "a #variant hybrid search queries the text and the vector ranking"() {
        when:
        def results = search(variant, SearchQuery.builder("coffee").vector("embedding", QUERY_VECTOR).build())

        then:
        results.results().isEmpty()
        executedQueries.size() == 2
        executedQueries[1].contains(VECTOR_OPERATOR)

        where:
        variant << Variant.values()
    }

    void "a #variant search of a store that cannot render the vector score reports an unsupported hybrid search"() {
        given:
        def failure = new IllegalStateException("Vector similarity queries are not supported")
        onRender = { String query -> if (query.contains(VECTOR_OPERATOR)) { throw failure } }

        when:
        search(variant, SearchQuery.builder("coffee").vector("embedding", QUERY_VECTOR).build())

        then:
        def e = thrown(UnsupportedSearchFeatureException)
        e.cause.is(failure)
        executedQueries.size() == 1

        where:
        variant << Variant.values()
    }

    void "a failure while the #variant vector query runs is not relabelled as an unsupported hybrid search"() {
        given:
        onExecute = { String query ->
            if (query.contains(VECTOR_OPERATOR)) {
                throw new IllegalStateException("row mapping failed")
            }
            return SearchResults.of(List.of())
        }

        when:
        search(variant, SearchQuery.builder("coffee").vector("embedding", QUERY_VECTOR).build())

        then:
        def e = thrown(IllegalStateException)
        e.message == "row mapping failed"

        where:
        variant << Variant.values()
    }

    void "a #variant search rejects a vector property that holds no vector before the vector query runs"() {
        when:
        search(variant, SearchQuery.builder("coffee").vector("title", QUERY_VECTOR).build())

        then:
        def e = thrown(IllegalArgumentException)
        e.message.contains("Property 'title' of entity SearchVectorArticle is not a Vector property")
        executedQueries.size() == 1

        where:
        variant << Variant.values()
    }

    void "a #variant hybrid search fuses both rankings, keeps the text highlights and applies minScore"() {
        given:
        def a = article(1L, "a")
        def b = article(2L, "b")
        def c = article(3L, "c")
        def highlights = [title: ["<em>coffee</em>"]]
        onExecute = { String query ->
            if (query.contains(VECTOR_OPERATOR)) {
                return SearchResults.of([new SearchResult<Object>(b, new Score(0.1d)), new SearchResult<Object>(c, new Score(0.2d))])
            }
            return SearchResults.of([new SearchResult<Object>(a, new Score(2d), highlights), new SearchResult<Object>(b, new Score(1d), Map.of())])
        }

        when: 'a scores 1/61, b 1/62 + 1/61 and c 1/62, below the minimum score'
        def results = search(variant, SearchQuery.builder("coffee").vector("embedding", QUERY_VECTOR).minScore(1d / 61.5d).build())

        then:
        results.results()*.entity()*.title == ["b", "a"]
        Math.abs(results.results()[0].score().value() - (1d / 62 + 1d / 61)) < 1e-12
        Math.abs(results.results()[1].score().value() - 1d / 61) < 1e-12
        results.results()[1].highlights() == highlights
        results.totalHits() == 2

        where:
        variant << Variant.values()
    }

    private Object execute(PreparedQuery preparedQuery) {
        executedQueries << preparedQuery.query
        return onExecute.call(preparedQuery.query)
    }

    private CompletableFuture<Object> executeAsync(PreparedQuery preparedQuery) {
        try {
            return CompletableFuture.completedFuture(execute(preparedQuery))
        } catch (RuntimeException e) {
            return CompletableFuture.failedFuture(e)
        }
    }

    private static SearchVectorArticle article(Long id, String title) {
        def article = new SearchVectorArticle()
        article.id = id
        article.title = title
        return article
    }

    private SearchResults<Object> search(Variant variant, SearchQuery searchQuery) {
        def metadata = new MutableAnnotationMetadata()
        metadata.addDeclaredAnnotation(RepositoryConfiguration.name, [queryBuilder: new AnnotationClassValue<>(SqlQueryBuilder)])
        metadata.addDeclaredAnnotation(Repository.name, [dialect: "POSTGRES"])
        metadata.addDeclaredAnnotation(DataMethod.NAME, [(DataMethod.META_MEMBER_ROOT_ENTITY): new AnnotationClassValue<>(SearchVectorArticle)])
        Map<String, Object> attributes = [:]
        // a map proxy keeps the default annotation methods, which read the metadata above
        MethodInvocationContext<Object, Object> context = [
                getAnnotationMetadata: { -> metadata },
                getParameterValues   : { -> [searchQuery] as Object[] },
                getParameters        : { -> [:] },
                getExecutableMethod  : { -> Stub(ExecutableMethod) },
                getName              : { -> "search" },
                getReturnType        : { -> Stub(ReturnType) { getType() >> Mono } },
                getAttributes        : { -> new MutableConvertibleValuesMap<Object>(attributes) },
                setAttribute         : { CharSequence name, Object value -> attributes[name.toString()] = value; null },
        ] as MethodInvocationContext
        def key = new RepositoryMethodKey(this, Stub(ExecutableMethod))
        switch (variant) {
            case Variant.REACTIVE:
                return ((Mono<SearchResults<Object>>) new SearchQueryReactiveInterceptor(operations).intercept(key, context)).block()
            case Variant.ASYNC:
                return unwrap((CompletableFuture<SearchResults<Object>>) new SearchQueryAsyncInterceptor(operations).intercept(key, context))
            default:
                return (SearchResults<Object>) new SearchQueryInterceptor(operations).intercept(key, context)
        }
    }

    private static SearchResults<Object> unwrap(CompletableFuture<SearchResults<Object>> future) {
        try {
            return future.get()
        } catch (ExecutionException | CompletionException e) {
            throw e.cause
        }
    }

    enum Variant {
        BLOCKING, REACTIVE, ASYNC
    }

    interface TestOperations extends RepositoryOperations, PreparedQueryResolver, ReactiveCapableRepository, AsyncCapableRepository {
    }
}
