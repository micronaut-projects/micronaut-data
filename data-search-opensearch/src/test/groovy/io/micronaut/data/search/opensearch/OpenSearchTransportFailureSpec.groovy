package io.micronaut.data.search.opensearch

import io.micronaut.data.exceptions.DataAccessException
import io.micronaut.data.search.document.runtime.SearchTransport
import io.micronaut.data.search.opensearch.transport.OpenSearchSearchTransport
import org.opensearch.client.json.JsonpMapper
import org.opensearch.client.json.jackson.JacksonJsonpMapper
import org.opensearch.client.opensearch.OpenSearchClient
import org.opensearch.client.opensearch._types.ErrorCause
import org.opensearch.client.opensearch._types.ErrorResponse
import org.opensearch.client.opensearch._types.OpenSearchException
import org.opensearch.client.transport.Endpoint
import org.opensearch.client.transport.OpenSearchTransport
import org.opensearch.client.transport.TransportOptions
import spock.lang.Specification

import java.util.concurrent.CompletableFuture

import static io.micronaut.data.search.document.conf.DocumentSearchConfiguration.Refresh.NONE

/**
 * Checks that every method of the blocking OpenSearch transport reports the failures of the
 * client, the errors the store answers with and the I/O errors, as a {@link DataAccessException}.
 */
class OpenSearchTransportFailureSpec extends Specification {

    static final String BODY = '{"query":{"match_all":{}}}'

    FailingTransport fake = new FailingTransport()
    OpenSearchSearchTransport transport = new OpenSearchSearchTransport(new OpenSearchClient(fake))

    private static OpenSearchException storeError(int status) {
        def cause = ErrorCause.of { it.type("search_phase_execution_exception").reason("Result window is too large") }
        return new OpenSearchException(ErrorResponse.of { it.status(status).error(cause) })
    }

    void "a store error of #operation is a DataAccessException"() {
        given:
        def failure = storeError(400)
        fake.failure = failure

        when:
        call.call(transport)

        then:
        DataAccessException e = thrown()
        e.message.startsWith("OpenSearch $operation failed: ")
        e.message.contains("Result window is too large")
        e.cause.is(failure)

        where:
        operation         | call
        "search"          | { OpenSearchSearchTransport t -> t.search("books", BODY) }
        "count"           | { OpenSearchSearchTransport t -> t.count("books", BODY) }
        "index"           | { OpenSearchSearchTransport t -> t.index("books", "1", [title: "Kotlin"], NONE) }
        "bulk"            | { OpenSearchSearchTransport t -> t.bulkIndex("books", [new SearchTransport.BulkDocument("1", [title: "Kotlin"])], NONE) }
        "get"             | { OpenSearchSearchTransport t -> t.get("books", "1") }
        "delete"          | { OpenSearchSearchTransport t -> t.delete("books", "1", NONE) }
        "delete by query" | { OpenSearchSearchTransport t -> t.deleteByQuery("books", BODY, NONE) }
        "index exists"    | { OpenSearchSearchTransport t -> t.indexExists("books") }
        "create index"    | { OpenSearchSearchTransport t -> t.createIndex("books", '{"mappings":{}}') }
    }

    void "an I/O error of #operation is a DataAccessException"() {
        given:
        def failure = new IOException("connection reset")
        fake.failure = failure

        when:
        call.call(transport)

        then:
        DataAccessException e = thrown()
        e.message == "OpenSearch $operation failed: connection reset"
        e.cause.is(failure)

        where:
        operation | call
        "search"  | { OpenSearchSearchTransport t -> t.search("books", BODY) }
        "get"     | { OpenSearchSearchTransport t -> t.get("books", "1") }
        "bulk"    | { OpenSearchSearchTransport t -> t.bulkIndex("books", [new SearchTransport.BulkDocument("1", [title: "Kotlin"])], NONE) }
        "index"   | { OpenSearchSearchTransport t -> t.create("books", "1", [title: "Kotlin"], NONE) }
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

    /**
     * A client transport failing every request with the configured exception.
     */
    static class FailingTransport implements OpenSearchTransport {

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
            return TransportOptions.builder().build()
        }

        @Override
        void close() {
            // nothing to release
        }
    }
}
