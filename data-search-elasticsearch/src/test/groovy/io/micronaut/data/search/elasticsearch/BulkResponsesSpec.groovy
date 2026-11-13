package io.micronaut.data.search.elasticsearch

import io.micronaut.data.exceptions.DataAccessException
import io.micronaut.data.exceptions.OptimisticLockException
import io.micronaut.data.search.document.conf.DocumentSearchConfiguration
import io.micronaut.data.search.document.runtime.AsyncSearchTransport
import io.micronaut.data.search.document.runtime.BulkIndexException
import io.micronaut.data.search.document.runtime.BulkResponses
import io.micronaut.data.search.document.runtime.SearchTransport
import org.jspecify.annotations.Nullable
import spock.lang.Specification

import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionException
import java.util.concurrent.CompletionStage

import static io.micronaut.data.search.document.conf.DocumentSearchConfiguration.Refresh.NONE

class BulkResponsesSpec extends Specification {

    void "the ids of an all-success bulk response keep the request order"() {
        expect:
        BulkResponses.ids("Store", [[id: "a"], [id: "b"]], { it.error as String }, { it.id as String }, { 201 }) == ["a", "b"]
    }

    void "a partly rejected bulk request names every rejected item and how many were written"() {
        when:
        BulkResponses.ids("Store", [[id: "a"], [id: "b", error: "version conflict"], [id: "c"], [id: "d", error: "mapping"]],
                { it.error as String }, { it.id as String }, { it.error ? 400 : 201 })

        then:
        def e = thrown(BulkIndexException)
        e.message == "Store bulk request rejected 2 of 4 documents, the other 2 were written: item 1 (id b): version conflict; item 3 (id d): mapping"
        e.ids == ["a", null, "c", null]
    }

    void "a version conflict in a partly rejected bulk request is an OptimisticLockException caused by the BulkIndexException"() {
        when:
        BulkResponses.ids("Store", [[id: "a"], [id: "b", error: "version conflict", status: 409], [id: "c", error: "mapping", status: 400]],
                { it.error as String }, { it.id as String }, { (it.status ?: 201) as int })

        then:
        OptimisticLockException e = thrown()
        e.message == "Store bulk request rejected 2 of 3 documents, the other 1 were written: item 1 (id b): version conflict; item 2 (id c): mapping"
        e.cause instanceof BulkIndexException
        e.cause.message == e.message
        BulkResponses.partialFailure(e).is(e.cause)
        BulkResponses.partialFailure(e).ids == ["a", null, null]
    }

    void "partialFailure finds the BulkIndexException of a bulk failure only"() {
        given:
        def bulk = new BulkIndexException("partial", ["a", null])

        expect:
        BulkResponses.partialFailure(bulk).is(bulk)
        BulkResponses.partialFailure(new OptimisticLockException("conflict", bulk)).is(bulk)
        BulkResponses.partialFailure(new OptimisticLockException("conflict")) == null
        BulkResponses.partialFailure(new DataAccessException("other", bulk)) == null
    }

    void "the default bulk index stops at a versioned document whose version is stale and keeps it an OptimisticLockException"() {
        given:
        def stale = new OptimisticLockException("Document b is at version 3")
        SearchTransport transport = new VersionedTransport(stale)

        when:
        transport.bulkIndex("books", [new SearchTransport.BulkDocument("a", [:], 1L), new SearchTransport.BulkDocument("b", [:], 2L),
                                      new SearchTransport.BulkDocument("c", [:])], NONE)

        then:
        OptimisticLockException e = thrown()
        e.message == "Indexing document 1 of 3 failed, the 1 before it were written and the rest were not sent: Document b is at version 3"
        e.cause instanceof BulkIndexException
        e.cause.cause.is(stale)
        (e.cause as BulkIndexException).ids == ["a", null, null]
    }

    void "the default async bulk index stops at a versioned document whose version is stale and keeps it an OptimisticLockException"() {
        given:
        def stale = new OptimisticLockException("Document b is at version 3")
        AsyncSearchTransport transport = new AsyncVersionedTransport(new VersionedTransport(stale))

        when:
        transport.bulkIndex("books", [new SearchTransport.BulkDocument("a", [:], 1L), new SearchTransport.BulkDocument("b", [:], 2L)], NONE)
                .toCompletableFuture().join()

        then:
        CompletionException completion = thrown()
        OptimisticLockException e = completion.cause as OptimisticLockException
        (e.cause as BulkIndexException).ids == ["a", null]
    }

    /**
     * Writes versioned documents through the versioned index, failing for id {@code b}, and the others through create.
     */
    static class VersionedTransport implements SearchTransport {

        final RuntimeException failure
        final List<String> calls = []

        VersionedTransport(RuntimeException failure) {
            this.failure = failure
        }

        @Override
        String index(String index, @Nullable String id, Map<String, Object> source, @Nullable Long version, DocumentSearchConfiguration.Refresh refresh) {
            calls << "index " + id + " v" + version
            if (id == "b") {
                throw failure
            }
            return id
        }

        @Override
        String create(String index, @Nullable String id, Map<String, Object> source, DocumentSearchConfiguration.Refresh refresh) {
            calls << "create " + id
            return id
        }

        @Override
        SearchTransport.SearchHits search(String index, String body) { throw new UnsupportedOperationException() }

        @Override
        long count(String index, String body) { throw new UnsupportedOperationException() }

        @Override
        String index(String index, @Nullable String id, Map<String, Object> source, DocumentSearchConfiguration.Refresh refresh) { throw new UnsupportedOperationException() }

        @Override
        Map<String, Object> get(String index, String id) { throw new UnsupportedOperationException() }

        @Override
        boolean delete(String index, String id, DocumentSearchConfiguration.Refresh refresh) { throw new UnsupportedOperationException() }

        @Override
        long deleteByQuery(String index, String body, DocumentSearchConfiguration.Refresh refresh) { throw new UnsupportedOperationException() }

        @Override
        boolean indexExists(String index) { throw new UnsupportedOperationException() }

        @Override
        void createIndex(String index, String body) { throw new UnsupportedOperationException() }
    }

    /**
     * Completes each call of the blocking {@link VersionedTransport}.
     */
    static class AsyncVersionedTransport implements AsyncSearchTransport {

        final VersionedTransport delegate

        AsyncVersionedTransport(VersionedTransport delegate) {
            this.delegate = delegate
        }

        @Override
        CompletionStage<String> index(String index, @Nullable String id, Map<String, Object> source, @Nullable Long version, DocumentSearchConfiguration.Refresh refresh) {
            try {
                return CompletableFuture.completedFuture(delegate.index(index, id, source, version, refresh))
            } catch (RuntimeException e) {
                return CompletableFuture.failedFuture(e)
            }
        }

        @Override
        CompletionStage<SearchTransport.SearchHits> search(String index, String body) { throw new UnsupportedOperationException() }

        @Override
        CompletionStage<Long> count(String index, String body) { throw new UnsupportedOperationException() }

        @Override
        CompletionStage<String> index(String index, @Nullable String id, Map<String, Object> source, DocumentSearchConfiguration.Refresh refresh) { throw new UnsupportedOperationException() }

        @Override
        CompletionStage<Optional<Map<String, Object>>> get(String index, String id) { throw new UnsupportedOperationException() }

        @Override
        CompletionStage<Boolean> delete(String index, String id, DocumentSearchConfiguration.Refresh refresh) { throw new UnsupportedOperationException() }

        @Override
        CompletionStage<Long> deleteByQuery(String index, String body, DocumentSearchConfiguration.Refresh refresh) { throw new UnsupportedOperationException() }
    }
}
