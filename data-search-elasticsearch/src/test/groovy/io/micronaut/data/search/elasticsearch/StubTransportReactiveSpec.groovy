package io.micronaut.data.search.elasticsearch

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.data.model.Pageable
import io.micronaut.data.runtime.query.internal.DefaultPagedQuery
import io.micronaut.data.search.document.conf.DocumentSearchConfiguration
import io.micronaut.data.search.document.runtime.AsyncSearchTransport
import io.micronaut.data.search.document.runtime.DefaultDocumentSearchReactiveRepositoryOperations
import io.micronaut.data.search.document.runtime.DefaultDocumentSearchRepositoryOperations
import io.micronaut.data.search.document.runtime.SearchTransport
import io.micronaut.data.search.document.runtime.SearchTransport.SearchHit
import io.micronaut.data.search.document.runtime.SearchTransport.SearchHits
import io.micronaut.data.search.tck.entities.Book
import io.micronaut.inject.qualifiers.Qualifiers
import jakarta.inject.Named
import jakarta.inject.Singleton
import org.jspecify.annotations.Nullable
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage

/**
 * Exercises the reactive repository operations against an in-memory transport, so the non-blocking
 * path is verified without a container; the transport ignores query bodies and returns every document.
 */
class StubTransportReactiveSpec extends Specification {

    @AutoCleanup
    @Shared
    ApplicationContext context = ApplicationContext.run(["spec.name": "StubTransportReactiveSpec"])

    @Shared
    StubBookRepository repository = context.getBean(StubBookRepository)

    void "the reactive repository of a store with an async transport does not block"() {
        expect:
        context.getBean(DefaultDocumentSearchRepositoryOperations, Qualifiers.byName("stub")).reactive() instanceof DefaultDocumentSearchReactiveRepositoryOperations
    }

    void "the reactive and async operations are created once and the async ones adapt the reactive ones"() {
        given:
        def operations = context.getBean(DefaultDocumentSearchRepositoryOperations, Qualifiers.byName("stub"))

        expect:
        operations.reactive().is(operations.reactive())
        operations.async().is(operations.async())
        operations.async().class.simpleName == "ReactorToAsyncOperationsAdaptor"
    }

    void "the reactive operations find by id, page, and update and delete in batches"() {
        given:
        def operations = (DefaultDocumentSearchReactiveRepositoryOperations) context.getBean(DefaultDocumentSearchRepositoryOperations, Qualifiers.byName("stub")).reactive()
        def saved = repository.saveAll([
                new Book("Baking Bread", "Sourdough", "Dough", 220),
                new Book("Cooking with Coffee", "Recipes", "Bean", 120),
        ]).collectList().block()
        def method = context.getBeanDefinition(StubBookRepository).getRequiredMethod("findAll")
        def query = new DefaultPagedQuery<Book>(method, Book, Pageable.from(0, 5))

        expect:
        operations.findOne(Book, saved[0].id).block().title == "Baking Bread"
        operations.findOptional(Book, saved[1].id).block().title == "Cooking with Coffee"
        operations.findOne(Book, UUID.randomUUID()).block() == null
        operations.findAll(query).collectList().block().size() == 2
        operations.count(query).block() == 2L
        operations.findPage(query).block().totalSize == 2

        when:
        saved.each { it.title = it.title + " (2nd edition)" }
        repository.updateAll(saved).collectList().block()

        then:
        repository.findAll().collectList().block()*.title.every { it.endsWith("(2nd edition)") }

        when:
        repository.deleteAll(saved).block()

        then:
        repository.count().block() == 0L
    }

    void "reactive CRUD and search run through the async transport"() {
        when:
        def saved = repository.save(new Book("Programming Kotlin", "Kotlin for the JVM", "Subramaniam", 460)).block()

        then:
        saved.id != null
        repository.findById(saved.id).block().title == "Programming Kotlin"
        repository.count().block() == 1L
        repository.findAll().collectList().block()*.title == ["Programming Kotlin"]
        repository.existsById(saved.id).block()

        when:
        def results = repository.searchBySearch("kotlin").block()

        then:
        results.results().size() == 1
        results.results()[0].score().value() == 1d
        results.totalHits() == 1
        context.getBean(StubSearchTransport).bodies.last().contains('"track_total_hits":true')
        repository.findByTitleSearch("kotlin").collectList().block()*.title == ["Programming Kotlin"]
        repository.countBySearch("kotlin").block() == 1L

        when:
        saved.title = "Programming Scala"
        repository.update(saved).block()

        then:
        repository.findById(saved.id).block().title == "Programming Scala"

        when:
        repository.delete(saved).block()

        then:
        repository.count().block() == 0L
        context.getBean(StubSearchTransport).calls.every { it.startsWith("async:") }
    }

    /**
     * The blocking transport: used for index creation only, every repository call must go async.
     */
    @Singleton
    @Named("stub")
    @Requires(property = "spec.name", value = "StubTransportReactiveSpec")
    static class StubSearchTransport implements SearchTransport {

        final Map<String, Map<String, Map<String, Object>>> indexes = [:]
        final List<String> calls = []
        final List<String> bodies = []

        Map<String, Map<String, Object>> docs(String index) {
            return indexes.computeIfAbsent(index, { new LinkedHashMap<>() })
        }

        SearchHits hits(String index) {
            def docs = docs(index).entrySet().collect { new SearchHit(it.key, 1d, it.value) }
            return new SearchHits(docs, docs.size(), docs.isEmpty() ? null : 1d)
        }

        String put(String index, @Nullable String id, Map<String, Object> source) {
            String documentId = id ?: UUID.randomUUID().toString()
            docs(index).put(documentId, source)
            return documentId
        }

        long clear(String index) {
            int size = docs(index).size()
            docs(index).clear()
            return size
        }

        @Override
        SearchHits search(String index, String body) {
            calls.add("sync:search")
            return hits(index)
        }

        @Override
        long count(String index, String body) {
            calls.add("sync:count")
            return docs(index).size()
        }

        @Override
        String index(String index, @Nullable String id, Map<String, Object> source, DocumentSearchConfiguration.Refresh refresh) {
            calls.add("sync:index")
            return put(index, id, source)
        }

        @Override
        Map<String, Object> get(String index, String id) {
            calls.add("sync:get")
            return docs(index).get(id)
        }

        @Override
        boolean delete(String index, String id, DocumentSearchConfiguration.Refresh refresh) {
            calls.add("sync:delete")
            return docs(index).remove(id) != null
        }

        @Override
        long deleteByQuery(String index, String body, DocumentSearchConfiguration.Refresh refresh) {
            calls.add("sync:deleteByQuery")
            return clear(index)
        }

        @Override
        boolean indexExists(String index) {
            return true
        }

        @Override
        void createIndex(String index, String body) {
            // the stub keeps documents only, so there is no mapping to create
        }
    }

    @Singleton
    @Named("stub")
    @Requires(property = "spec.name", value = "StubTransportReactiveSpec")
    static class StubAsyncSearchTransport implements AsyncSearchTransport {

        private final StubSearchTransport store

        StubAsyncSearchTransport(StubSearchTransport store) {
            this.store = store
        }

        private <T> CompletionStage<T> done(String call, T value) {
            store.calls.add("async:" + call)
            return CompletableFuture.completedFuture(value)
        }

        @Override
        CompletionStage<SearchTransport.SearchHits> search(String index, String body) {
            store.bodies.add(body)
            return done("search", store.hits(index))
        }

        @Override
        CompletionStage<Long> count(String index, String body) {
            return done("count", (long) store.docs(index).size())
        }

        @Override
        CompletionStage<String> index(String index, @Nullable String id, Map<String, Object> source, DocumentSearchConfiguration.Refresh refresh) {
            return done("index", store.put(index, id, source))
        }

        @Override
        CompletionStage<Optional<Map<String, Object>>> get(String index, String id) {
            return done("get", Optional.ofNullable(store.docs(index).get(id)))
        }

        @Override
        CompletionStage<Boolean> delete(String index, String id, DocumentSearchConfiguration.Refresh refresh) {
            return done("delete", store.docs(index).remove(id) != null)
        }

        @Override
        CompletionStage<Long> deleteByQuery(String index, String body, DocumentSearchConfiguration.Refresh refresh) {
            return done("deleteByQuery", store.clear(index))
        }
    }
}
