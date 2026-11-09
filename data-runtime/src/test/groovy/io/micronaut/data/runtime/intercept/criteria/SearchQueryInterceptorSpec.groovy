package io.micronaut.data.runtime.intercept.criteria

import io.micronaut.aop.MethodInvocationContext
import io.micronaut.context.ApplicationContext
import io.micronaut.core.annotation.AnnotationClassValue
import io.micronaut.core.convert.ConversionService
import io.micronaut.core.convert.value.MutableConvertibleValuesMap
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
import io.micronaut.data.model.vector.search.SearchResults
import io.micronaut.data.operations.RepositoryOperations
import io.micronaut.data.runtime.criteria.RuntimeCriteriaBuilder
import io.micronaut.data.runtime.criteria.SearchVectorArticle
import io.micronaut.data.runtime.query.PreparedQueryResolver
import io.micronaut.inject.ExecutableMethod
import io.micronaut.inject.annotation.MutableAnnotationMetadata
import spock.lang.Specification

/**
 * Drives the hybrid path of {@link SearchQueryInterceptor} against a PostgreSQL query builder and
 * stubbed operations, so the failure handling of the vector ranking is checked without a database.
 */
class SearchQueryInterceptorSpec extends Specification {

    static final Vector QUERY_VECTOR = Vector.of([1f, 0f, 0f] as float[])

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
        findOne(_ as PreparedQuery) >> { PreparedQuery preparedQuery ->
            executedQueries << preparedQuery.query
            return onExecute.call(preparedQuery.query)
        }
    }

    void "a hybrid search queries the text and the vector ranking"() {
        when:
        def results = search(SearchQuery.builder("coffee").vector("embedding", QUERY_VECTOR).build())

        then:
        results.results().isEmpty()
        executedQueries.size() == 2
        executedQueries[1].contains(' <=> ')
    }

    void "a store that cannot render the vector score reports an unsupported hybrid search"() {
        given:
        def failure = new IllegalStateException("Vector similarity queries are not supported")
        onRender = { String query -> if (query.contains(' <=> ')) { throw failure } }

        when:
        search(SearchQuery.builder("coffee").vector("embedding", QUERY_VECTOR).build())

        then:
        def e = thrown(UnsupportedSearchFeatureException)
        e.cause.is(failure)
        executedQueries.size() == 1
    }

    void "a failure while the vector query runs is not relabelled as an unsupported hybrid search"() {
        given:
        onExecute = { String query ->
            if (query.contains(' <=> ')) {
                throw new IllegalStateException("row mapping failed")
            }
            return SearchResults.of(List.of())
        }

        when:
        search(SearchQuery.builder("coffee").vector("embedding", QUERY_VECTOR).build())

        then:
        def e = thrown(IllegalStateException)
        e.message == "row mapping failed"
    }

    void "a vector property that holds no vector is rejected before the vector query runs"() {
        when:
        search(SearchQuery.builder("coffee").vector("title", QUERY_VECTOR).build())

        then:
        def e = thrown(IllegalArgumentException)
        e.message.contains("Property 'title' of entity SearchVectorArticle is not a Vector property")
        executedQueries.size() == 1
    }

    private SearchResults<Object> search(SearchQuery searchQuery) {
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
                getAttributes        : { -> new MutableConvertibleValuesMap<Object>(attributes) },
                setAttribute         : { CharSequence name, Object value -> attributes[name.toString()] = value; null },
        ] as MethodInvocationContext
        def interceptor = new SearchQueryInterceptor(operations)
        return (SearchResults<Object>) interceptor.intercept(new RepositoryMethodKey(this, Stub(ExecutableMethod)), context)
    }

    interface TestOperations extends RepositoryOperations, PreparedQueryResolver {
    }
}
