package io.micronaut.data.search.elasticsearch

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.data.event.listeners.PostPersistEventListener
import io.micronaut.data.model.Pageable
import io.micronaut.data.model.search.SearchQuery
import io.micronaut.data.model.runtime.AttributeConverterRegistry
import io.micronaut.data.model.runtime.RuntimeEntityRegistry
import io.micronaut.data.runtime.convert.DataConversionService
import io.micronaut.data.runtime.date.DateTimeProvider
import io.micronaut.data.runtime.query.internal.DefaultPagedQuery
import io.micronaut.data.search.document.conf.DocumentSearchConfiguration
import io.micronaut.data.search.document.runtime.AsyncSearchTransport
import io.micronaut.data.search.document.runtime.BulkIndexException
import io.micronaut.data.search.document.runtime.DefaultDocumentSearchReactiveRepositoryOperations
import io.micronaut.data.search.document.runtime.DefaultDocumentSearchRepositoryOperations
import io.micronaut.data.exceptions.DataAccessException
import io.micronaut.data.exceptions.OptimisticLockException
import io.micronaut.data.search.document.runtime.SearchTransport
import io.micronaut.data.search.document.runtime.SearchTransport.SearchHit
import io.micronaut.data.search.document.runtime.SearchTransport.SearchHits
import io.micronaut.data.search.tck.entities.Book
import io.micronaut.inject.qualifiers.Qualifiers
import io.micronaut.json.JsonMapper
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
    ApplicationContext context = ApplicationContext.run(["spec.name": "StubTransportReactiveSpec", "micronaut.data.search.bulk-size": "2"])

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

    void "a versioned delete is checked against the version the entity was read at"() {
        given:
        def versioned = context.getBean(StubVersionedBookRepository)
        def transport = context.getBean(StubSearchTransport)
        def saved = versioned.save(new VersionedBook("d1", "Programming Kotlin"))
        def stale = versioned.findById("d1").get()
        saved.title = "Programming Scala"
        versioned.update(saved)

        when: "an instance read before the update is deleted"
        versioned.delete(stale)

        then: "it is deleted at the version it was read at, which the store rejects"
        thrown(OptimisticLockException)
        transport.deleteVersions.last() == 0L
        versioned.findById("d1").present

        when:
        versioned.delete(versioned.findById("d1").get())

        then:
        transport.deleteVersions.last() == 1L
        !versioned.findById("d1").present

        cleanup:
        versioned.deleteAll()
    }

    void "a reactive versioned delete is checked against the version the entity was read at"() {
        given:
        def versioned = context.getBean(StubReactiveVersionedBookRepository)
        def transport = context.getBean(StubSearchTransport)
        def saved = versioned.save(new VersionedBook("d2", "Programming Kotlin")).block()
        def stale = versioned.findById("d2").block()
        saved.title = "Programming Scala"
        versioned.update(saved).block()

        when: "an instance read before the update is deleted"
        versioned.delete(stale).block()

        then: "it is deleted at the version it was read at, which the store rejects"
        thrown(OptimisticLockException)
        transport.deleteVersions.last() == 0L
        versioned.existsById("d2").block()

        when:
        versioned.delete(versioned.findById("d2").block()).block()

        then:
        transport.deleteVersions.last() == 1L
        !versioned.existsById("d2").block()

        cleanup:
        versioned.deleteAll().block()
    }

    void "a versioned entity without an id is saved under a generated id at version 0"() {
        given:
        def versioned = context.getBean(StubVersionedBookRepository)
        def reactive = context.getBean(StubReactiveVersionedBookRepository)
        def transport = context.getBean(StubSearchTransport)

        when:
        def saved = versioned.save(new VersionedBook(null, "Programming Kotlin"))
        def savedAll = versioned.saveAll([new VersionedBook(null, "Baking Bread"), new VersionedBook(null, "Cooking with Coffee")])
        def savedReactive = reactive.save(new VersionedBook(null, "Programming Rust")).block()

        then: "the id is generated before the write and is kept in the document"
        ([saved] + savedAll + [savedReactive]).every { UUID.fromString(it.id) && it.version == 0L }
        transport.docs("versioned_book")[saved.id].id == saved.id
        transport.docs("versioned_book")[savedReactive.id].title == "Programming Rust"

        cleanup:
        versioned.deleteAll()
    }

    void "a versioned saveAll that meets an existing document fails with an OptimisticLockException and keeps the written documents"() {
        given:
        def versioned = context.getBean(StubVersionedBookRepository)
        def transport = context.getBean(StubSearchTransport)
        versioned.save(new VersionedBook("dup", "Existing"))
        def first = new VersionedBook(null, "First")
        def duplicate = new VersionedBook("dup", "Duplicate")
        def notSent = new VersionedBook(null, "Not sent")

        when:
        versioned.saveAll([first, duplicate, notSent])

        then:
        OptimisticLockException e = thrown()
        e.cause instanceof BulkIndexException
        (e.cause as BulkIndexException).ids == [first.id, null]
        transport.docs("versioned_book").keySet() == ["dup", first.id] as Set
        transport.docs("versioned_book")["dup"].title == "Existing"

        cleanup:
        versioned.deleteAll()
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

    void "saveAll sends bulk requests of the configured size in sequence"() {
        given:
        def transport = context.getBean(StubSearchTransport)
        def before = transport.calls.count { it == "async:bulkIndex" }

        when:
        def saved = repository.saveAll((1..5).collect { new Book("Book " + it, "Body", "Author", it) }).collectList().block()

        then:
        saved*.title == (1..5).collect { "Book " + it }
        transport.calls.count { it == "async:bulkIndex" } - before == 3
        repository.count().block() == 5L

        cleanup:
        repository.deleteAll().block()
    }

    void "a partly failed reactive saveAll fires the post-persist event of the written entities and sends no further request"() {
        given:
        def recorder = context.getBean(PostPersistRecorder)
        recorder.titles.clear()
        def transport = context.getBean(StubSearchTransport)
        def before = transport.calls.count { it == "async:bulkIndex" }

        when:
        repository.saveAll(["Written 1", "Written 2", "Written 3", "Rejected", "Not sent"].collect { new Book(it, "Body", "Author", 1) })
                .collectList().block()

        then:
        BulkIndexException e = thrown()
        e.ids.size() == 2
        e.ids[0] != null
        e.ids[1] == null
        recorder.titles == ["Written 1", "Written 2", "Written 3"]
        transport.calls.count { it == "async:bulkIndex" } - before == 2
        repository.count().block() == 3L

        cleanup:
        repository.deleteAll().block()
    }

    void "the reactive and async operations are created once and the async ones adapt the reactive ones"() {
        given:
        def operations = context.getBean(DefaultDocumentSearchRepositoryOperations, Qualifiers.byName("stub"))

        expect:
        operations.reactive().is(operations.reactive())
        operations.async().is(operations.async())
        operations.async().class.simpleName == "ReactorToAsyncOperationsAdaptor"
    }

    void "closing the operations shuts down the thread pool created when no IO executor is configured"() {
        given:
        def operations = new DefaultDocumentSearchRepositoryOperations("stub", context.getBean(StubSearchTransport),
                context.getBean(JsonMapper), context.getBean(DocumentSearchConfiguration), context.getBean(DateTimeProvider),
                context.getBean(RuntimeEntityRegistry), context.getBean(DataConversionService), context.getBean(AttributeConverterRegistry),
                context, null)
        def executor = operations.async().executor

        expect:
        !executor.isShutdown()

        when:
        operations.close()

        then:
        executor.isShutdown()
    }

    void "closing the operations leaves the configured IO executor running"() {
        given:
        def operations = context.getBean(DefaultDocumentSearchRepositoryOperations, Qualifiers.byName("stub"))
        def executor = operations.async().executor

        when:
        operations.close()

        then:
        !executor.isShutdown()
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

    void "a reactive write runs on subscription only, and again on each retry"() {
        given:
        def transport = context.getBean(StubSearchTransport)
        def before = transport.calls.size()

        when:
        def save = repository.save(new Book("Unsubscribed", "Never sent", "Nobody", 1))

        then:
        transport.calls.size() == before

        when:
        def saved = save.block()

        then:
        transport.calls.size() == before + 1
        transport.calls.last() == "async:create"

        cleanup:
        if (saved != null) {
            repository.delete(saved).block()
        }
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
        final List<Long> deleteVersions = []

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
            if (version != null && id == null) {
                // as the stores answer a versioned write without an id
                throw new DataAccessException("an id must be provided if version type or value are set")
            }
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

        /**
         * Deletes a document, checking a version like {@code external_gte}: the delete is accepted at the
         * stored version or a later one and rejected when the stored document is newer.
         */
        boolean remove(String index, String id, @Nullable Long version) {
            String key = index + "/" + id
            if (version != null) {
                deleteVersions.add(version)
                Long current = versions.get(key)
                if (current != null && current > version) {
                    throw new OptimisticLockException("Document " + id + " is at version " + current + ", above " + version)
                }
            }
            versions.remove(key)
            return docs(index).remove(id) != null
        }

        long clear(String index) {
            int size = docs(index).size()
            versions.keySet().removeIf { it.startsWith(index + "/") }
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
            return remove(index, id, null)
        }

        @Override
        boolean delete(String index, String id, @Nullable Long version, DocumentSearchConfiguration.Refresh refresh) {
            calls.add("sync:delete")
            return remove(index, id, version)
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
        CompletionStage<String> index(String index, @Nullable String id, Map<String, Object> source, @Nullable Long version, DocumentSearchConfiguration.Refresh refresh) {
            try {
                return done("index", store.put(index, id, source, version))
            } catch (DataAccessException e) {
                return CompletableFuture.failedFuture(e)
            }
        }

        @Override
        CompletionStage<Optional<SearchTransport.SearchHit>> getHit(String index, String id) {
            return done("get", Optional.ofNullable(store.hit(index, id)))
        }

        @Override
        CompletionStage<List<String>> bulkIndex(String index, List<SearchTransport.BulkDocument> documents, DocumentSearchConfiguration.Refresh refresh) {
            // a document titled "Rejected" is rejected, the others are written, as a store does with a bulk request
            List<String> ids = documents.collect { it.source().title == "Rejected" ? null : store.put(index, it.id(), it.source(), it.version()) }
            return done("bulkIndex", ids).thenApply { written ->
                if (written.contains(null)) {
                    throw new BulkIndexException("Stub bulk request rejected a document", written)
                }
                return written
            }
        }

        @Override
        CompletionStage<String> create(String index, @Nullable String id, Map<String, Object> source, DocumentSearchConfiguration.Refresh refresh) {
            return done("create", store.put(index, id, source))
        }

        @Override
        CompletionStage<Optional<Map<String, Object>>> get(String index, String id) {
            return done("get", Optional.ofNullable(store.docs(index).get(id)))
        }

        @Override
        CompletionStage<Boolean> delete(String index, String id, DocumentSearchConfiguration.Refresh refresh) {
            return done("delete", store.remove(index, id, null))
        }

        @Override
        CompletionStage<Boolean> delete(String index, String id, @Nullable Long version, DocumentSearchConfiguration.Refresh refresh) {
            try {
                return done("delete", store.remove(index, id, version))
            } catch (OptimisticLockException e) {
                return CompletableFuture.failedFuture(e)
            }
        }

        @Override
        CompletionStage<Long> deleteByQuery(String index, String body, DocumentSearchConfiguration.Refresh refresh) {
            return done("deleteByQuery", store.clear(index))
        }
    }

    @Singleton
    @Requires(property = "spec.name", value = "StubTransportReactiveSpec")
    static class PostPersistRecorder implements PostPersistEventListener<Book> {

        final List<String> titles = []

        @Override
        void postPersist(Book entity) {
            titles.add(entity.title)
        }
    }
}
