package io.micronaut.data.search.elasticsearch

import co.elastic.clients.elasticsearch.ElasticsearchAsyncClient
import co.elastic.clients.elasticsearch.ElasticsearchClient
import co.elastic.clients.elasticsearch._types.ElasticsearchException
import co.elastic.clients.elasticsearch._types.ErrorResponse
import co.elastic.clients.json.JsonpMapper
import co.elastic.clients.json.jackson.JacksonJsonpMapper
import co.elastic.clients.transport.DefaultTransportOptions
import co.elastic.clients.transport.ElasticsearchTransport
import co.elastic.clients.transport.Endpoint
import co.elastic.clients.transport.TransportOptions
import co.elastic.clients.transport.TransportException
import co.elastic.clients.transport.http.TransportHttpClient
import io.micronaut.data.exceptions.DataAccessException
import io.micronaut.data.exceptions.OptimisticLockException
import io.micronaut.data.search.document.runtime.SearchTransport
import io.micronaut.data.search.elasticsearch.transport.ElasticsearchAsyncSearchTransport
import io.micronaut.data.search.elasticsearch.transport.ElasticsearchSearchTransport
import spock.lang.Specification

import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionException

import static io.micronaut.data.search.document.conf.DocumentSearchConfiguration.Refresh.NONE

/**
 * Checks that every method of the blocking Elasticsearch transport reports the failures of the
 * client, the errors the store answers with and the I/O errors, as a {@link DataAccessException}.
 */
class ElasticsearchTransportFailureSpec extends Specification {

    static final String BODY = '{"query":{"match_all":{}}}'

    FailingTransport fake = new FailingTransport()
    ElasticsearchSearchTransport transport = new ElasticsearchSearchTransport(new ElasticsearchClient(fake))
    ElasticsearchAsyncSearchTransport asyncTransport = new ElasticsearchAsyncSearchTransport(new ElasticsearchAsyncClient(fake))

    private static ElasticsearchException storeError(int status) {
        return new ElasticsearchException("es/test", ErrorResponse.of { it.status(status).error { e -> e.type("search_phase_execution_exception").reason("Result window is too large") } })
    }

    void "a store error of #operation is a DataAccessException"() {
        given:
        def failure = storeError(400)
        fake.failure = failure

        when:
        call.call(transport)

        then:
        DataAccessException e = thrown()
        e.message.startsWith("Elasticsearch $operation failed: ")
        e.message.contains("Result window is too large")
        e.cause.is(failure)

        where:
        operation         | call
        "search"          | { ElasticsearchSearchTransport t -> t.search("books", BODY) }
        "count"           | { ElasticsearchSearchTransport t -> t.count("books", BODY) }
        "index"           | { ElasticsearchSearchTransport t -> t.index("books", "1", [title: "Kotlin"], NONE) }
        "bulk"            | { ElasticsearchSearchTransport t -> t.bulkIndex("books", [new SearchTransport.BulkDocument("1", [title: "Kotlin"])], NONE) }
        "index"           | { ElasticsearchSearchTransport t -> t.index("books", "1", [title: "Kotlin"], 2L, NONE) }
        "get"             | { ElasticsearchSearchTransport t -> t.get("books", "1") }
        "get"             | { ElasticsearchSearchTransport t -> t.getHit("books", "1") }
        "delete"          | { ElasticsearchSearchTransport t -> t.delete("books", "1", NONE) }
        "delete"          | { ElasticsearchSearchTransport t -> t.delete("books", "1", 2L, NONE) }
        "delete by query" | { ElasticsearchSearchTransport t -> t.deleteByQuery("books", BODY, NONE) }
        "index exists"    | { ElasticsearchSearchTransport t -> t.indexExists("books") }
        "create index"    | { ElasticsearchSearchTransport t -> t.createIndex("books", '{"mappings":{}}') }
    }

    void "an I/O error of #operation is a DataAccessException"() {
        given:
        def failure = new IOException("connection reset")
        fake.failure = failure

        when:
        call.call(transport)

        then:
        DataAccessException e = thrown()
        e.message == "Elasticsearch $operation failed: connection reset"
        e.cause.is(failure)

        where:
        operation | call
        "search"  | { ElasticsearchSearchTransport t -> t.search("books", BODY) }
        "get"     | { ElasticsearchSearchTransport t -> t.get("books", "1") }
        "get"     | { ElasticsearchSearchTransport t -> t.getHit("books", "1") }
        "index"   | { ElasticsearchSearchTransport t -> t.index("books", "1", [title: "Kotlin"], 2L, NONE) }
        "delete"  | { ElasticsearchSearchTransport t -> t.delete("books", "1", 2L, NONE) }
        "bulk"    | { ElasticsearchSearchTransport t -> t.bulkIndex("books", [new SearchTransport.BulkDocument("1", [title: "Kotlin"])], NONE) }
        "index"   | { ElasticsearchSearchTransport t -> t.create("books", "1", [title: "Kotlin"], NONE) }
    }

    void "a version conflict of a versioned #operation is an OptimisticLockException"() {
        given:
        def failure = storeError(409)
        fake.failure = failure

        when:
        call.call(transport)

        then:
        OptimisticLockException e = thrown()
        e.message.startsWith("Document 1 was modified concurrently: ")
        e.cause.is(failure)

        where:
        operation | call
        "index"   | { ElasticsearchSearchTransport t -> t.index("books", "1", [title: "Kotlin"], 2L, NONE) }
        "delete"  | { ElasticsearchSearchTransport t -> t.delete("books", "1", 2L, NONE) }
    }

    void "a create of an existing id says the document exists and other create errors are wrapped"() {
        given:
        def failure = storeError(status)
        fake.failure = failure

        when:
        transport.create("books", "1", [title: "Kotlin"], NONE)

        then:
        DataAccessException e = thrown()
        e.message.startsWith(message)
        e.cause.is(failure)

        where:
        status | message
        409    | "A document with id 1 already exists in index books"
        400    | "Cannot create the document with id 1 in index books: "
    }

    void "an async create of an existing id says the document exists and other create errors are wrapped"() {
        given:
        def failure = storeError(status)
        fake.failure = failure

        when:
        asyncTransport.create("books", "1", [title: "Kotlin"], NONE).toCompletableFuture().join()

        then:
        CompletionException e = thrown()
        e.cause instanceof DataAccessException
        e.cause.message.startsWith(message)
        e.cause.cause.is(failure)

        where:
        status | message
        409    | "A document with id 1 already exists in index books"
        400    | "Cannot create the document with id 1 in index books: "
    }

    void "an async create failing for another reason passes the failure on"() {
        given:
        def failure = new IOException("connection reset")
        fake.failure = failure

        when:
        asyncTransport.create("books", "1", [title: "Kotlin"], NONE).toCompletableFuture().join()

        then:
        CompletionException e = thrown()
        e.cause.is(failure)
    }

    void "an async versioned #operation answered with HTTP 409 by the low-level client is an OptimisticLockException"() {
        given: "the async client reports a store response as the exception of its HTTP client"
        def failure = new TransportException(Stub(TransportHttpClient.Response) { statusCode() >> 409 }, "version conflict", "es/test")
        fake.failure = failure

        when:
        call.call(asyncTransport).toCompletableFuture().join()

        then:
        CompletionException e = thrown()
        e.cause instanceof OptimisticLockException
        e.cause.message.startsWith("Document 1 was modified concurrently: ")
        e.cause.message.endsWith("version conflict")
        e.cause.cause.is(failure)

        where:
        operation | call
        "index"   | { ElasticsearchAsyncSearchTransport t -> t.index("books", "1", [title: "Kotlin"], 2L, NONE) }
        "delete"  | { ElasticsearchAsyncSearchTransport t -> t.delete("books", "1", 2L, NONE) }
    }

    void "an async versioned index answered with another status by the low-level client is a DataAccessException"() {
        given:
        def failure = new TransportException(Stub(TransportHttpClient.Response) { statusCode() >> 500 }, "boom", "es/test")
        fake.failure = failure

        when:
        asyncTransport.index("books", "1", [title: "Kotlin"], 2L, NONE).toCompletableFuture().join()

        then:
        CompletionException e = thrown()
        e.cause instanceof DataAccessException
        !(e.cause instanceof OptimisticLockException)
        e.cause.cause.is(failure)
    }

    /**
     * A client transport failing every request with the configured exception.
     */
    static class FailingTransport implements ElasticsearchTransport {

        final JsonpMapper mapper = new JacksonJsonpMapper()
        Throwable failure

        @Override
        def <RequestT, ResponseT, ErrorT> ResponseT performRequest(RequestT request, Endpoint<RequestT, ResponseT, ErrorT> endpoint, TransportOptions options) throws IOException {
            throw failure
        }

        @Override
        def <RequestT, ResponseT, ErrorT> CompletableFuture<ResponseT> performRequestAsync(RequestT request, Endpoint<RequestT, ResponseT, ErrorT> endpoint, TransportOptions options) {
            return CompletableFuture.failedFuture(failure)
        }

        @Override
        JsonpMapper jsonpMapper() {
            return mapper
        }

        @Override
        TransportOptions options() {
            return new DefaultTransportOptions()
        }

        @Override
        void close() throws IOException {
            // nothing to release
        }
    }
}
