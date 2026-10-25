package io.micronaut.data.search.elasticsearch

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.data.model.Pageable
import io.micronaut.data.model.search.SearchQuery
import io.micronaut.data.search.document.conf.DocumentSearchConfiguration
import io.micronaut.data.search.document.runtime.AsyncSearchTransport
import io.micronaut.data.search.document.runtime.DefaultDocumentSearchReactiveRepositoryOperations
import io.micronaut.data.search.document.runtime.DefaultDocumentSearchRepositoryOperations
import io.micronaut.data.exceptions.OptimisticLockException
import io.micronaut.data.search.document.runtime.SearchTransport
import io.micronaut.data.search.document.runtime.SearchTransport.SearchHit
import io.micronaut.data.search.document.runtime.SearchTransport.SearchHits
import io.micronaut.data.model.vector.Vector
import io.micronaut.data.search.tck.entities.Article
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

    void "the index is named after @Searchable(name) when set"() {
        given:
        def operations = context.getBean(DefaultDocumentSearchRepositoryOperations, Qualifiers.byName("stub"))

        expect:
        operations.getIndexName(Book) == "search_book"
        operations.getIndexName(NamedBook) == "named_books"
    }

    void "a @Version property is the document version and stale updates fail"() {
        given:
        def versioned = context.getBean(StubVersionedBookRepository)

        when:
        def saved = versioned.save(new VersionedBook("v1", "Programming Kotlin"))

        then:
        saved.version == 0L
        versioned.findById("v1").get().version == 0L

        when:
        saved.title = "Programming Scala"
        def updated = versioned.update(saved)

        then:
        updated.version == 1L
        versioned.findById("v1").get().version == 1L
        versioned.findById("v1").get().title == "Programming Scala"

        when: "an instance loaded before the update is saved again"
        def stale = new VersionedBook("v1", "Programming Rust")
        stale.version = 0L
        versioned.update(stale)

        then:
        thrown(OptimisticLockException)
        versioned.findById("v1").get().title == "Programming Scala"

        cleanup:
        versioned.deleteAll()
    }

    void "vectors serialize as arrays and a hybrid query reaches the transport as a knn clause"() {
        given:
        def articles = context.getBean(StubArticleRepository)
        def transport = context.getBean(StubSearchTransport)

        when:
        def saved = articles.save(new Article("Cooking with Coffee", "Recipes", Vector.of([1f, 0f, 0f] as float[])))
        def loaded = articles.findById(saved.id).get()

        then:
        loaded.embedding.toFloatArray() == [1f, 0f, 0f] as float[]
        transport.docs(context.getBean(DefaultDocumentSearchRepositoryOperations, Qualifiers.byName("stub")).getIndexName(Article)).values()[0].embedding == [1.0d, 0.0d, 0.0d]

        when:
        transport.bodies.clear()
        def results = articles.search(SearchQuery.builder("coffee").vector("embedding", Vector.of([1f, 0f, 0f] as float[])).build())

        then:
        results.results()*.entity()*.title == ["Cooking with Coffee"]
        // the text query and the nearest-neighbour query both ran; the latter carries the knn clause with the bound vector
        transport.bodies.size() == 2
        transport.bodies.any { it.contains('"knn":{"field":"embedding","query_vector":[1.0,0.0,0.0],"k":100,"num_candidates":1000}') }

        cleanup:
        articles.deleteAll()
    }

    void "saveAll indexes in bulk through the async transport"() {
        when:
        def saved = repository.saveAll([
                new Book("Baking Bread", "Sourdough", "Dough", 220),
                new Book("Cooking with Coffee", "Recipes", "Bean", 120),
        ]).collectList().block()

        then:
        saved.size() == 2
        saved.every { it.id != null }
        repository.count().block() == 2L
        context.getBean(StubSearchTransport).calls.count { it == "async:bulkIndex" } == 1

        cleanup:
        repository.deleteAll().block()
    }

    void "reactive CRUD and search run through the async transport"() {
        given:
        context.getBean(StubSearchTransport).calls.clear()

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
        // the reactive and async SearchRepository variants run through the reactive criteria operations
        repository.search("kotlin").block().results().size() == 1
        repository.search(SearchQuery.of("kotlin")).block().results()*.entity()*.title == ["Programming Kotlin"]
        repository.search(SearchQuery.of("kotlin"), Pageable.from(0, 5)).block().totalHits() == 1
        repository.countHits("kotlin").block() == 1L
        context.getBean(StubAsyncBookRepository).search(SearchQuery.of("kotlin")).get().results().size() == 1
        context.getBean(StubAsyncBookRepository).countHits("kotlin").get() == 1L

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
        final Map<String, Long> versions = [:]
        final List<String> calls = []
        final List<String> bodies = []

        Map<String, Map<String, Object>> docs(String index) {
            return indexes.computeIfAbsent(index, { new LinkedHashMap<>() })
        }

        SearchHits hits(String index) {
            def docs = docs(index).entrySet().collect { new SearchHit(it.key, 1d, it.value, Map.of(), versions.get(index + "/" + it.key), List.of()) }
            return new SearchHits(docs, docs.size(), docs.isEmpty() ? null : 1d)
        }

        String put(String index, @Nullable String id, Map<String, Object> source) {
            return put(index, id, source, null)
        }

        String put(String index, @Nullable String id, Map<String, Object> source, @Nullable Long version) {
            String documentId = id ?: UUID.randomUUID().toString()
            String key = index + "/" + documentId
            if (version != null) {
                Long current = versions.get(key)
                if (current != null && current >= version) {
                    throw new OptimisticLockException("Document " + documentId + " is at version " + current + ", not below " + version)
                }
                versions.put(key, version)
            }
            docs(index).put(documentId, source)
            return documentId
        }

        @Nullable
        SearchHit hit(String index, String id) {
            def source = docs(index).get(id)
            return source == null ? null : new SearchHit(id, null, source, Map.of(), versions.get(index + "/" + id), List.of())
        }

        long clear(String index) {
            int size = docs(index).size()
            docs(index).clear()
            return size
        }

        @Override
        SearchHits search(String index, String body) {
            calls.add("sync:search")
            bodies.add(body)
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
        String index(String index, @Nullable String id, Map<String, Object> source, @Nullable Long version, DocumentSearchConfiguration.Refresh refresh) {
            calls.add("sync:index")
            return put(index, id, source, version)
        }

        @Override
        SearchHit getHit(String index, String id) {
            calls.add("sync:get")
            return hit(index, id)
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
        CompletionStage<String> index(String index, @Nullable String id, Map<String, Object> source, @Nullable Long version, DocumentSearchConfiguration.Refresh refresh) {
            try {
                return done("index", store.put(index, id, source, version))
            } catch (OptimisticLockException e) {
                return CompletableFuture.failedFuture(e)
            }
        }

        @Override
        CompletionStage<Optional<SearchTransport.SearchHit>> getHit(String index, String id) {
            return done("get", Optional.ofNullable(store.hit(index, id)))
        }

        @Override
        CompletionStage<List<String>> bulkIndex(String index, List<SearchTransport.BulkDocument> documents, DocumentSearchConfiguration.Refresh refresh) {
            return done("bulkIndex", documents.collect { store.put(index, it.id(), it.source(), it.version()) })
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
