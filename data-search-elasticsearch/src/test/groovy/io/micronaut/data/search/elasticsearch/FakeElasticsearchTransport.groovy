package io.micronaut.data.search.elasticsearch

import co.elastic.clients.elasticsearch._types.ElasticsearchException
import co.elastic.clients.elasticsearch._types.ErrorResponse
import co.elastic.clients.json.JsonpMapper
import co.elastic.clients.json.jackson.JacksonJsonpMapper
import co.elastic.clients.transport.DefaultTransportOptions
import co.elastic.clients.transport.ElasticsearchTransport
import co.elastic.clients.transport.Endpoint
import co.elastic.clients.transport.JsonEndpoint
import co.elastic.clients.transport.TransportOptions
import co.elastic.clients.transport.endpoints.BooleanEndpoint
import co.elastic.clients.transport.endpoints.BooleanResponse

import java.util.concurrent.CompletableFuture

/**
 * A client transport that answers every endpoint from a canned response, so the Elasticsearch
 * search transports run without a server. The response is registered by endpoint id
 * ({@code es/search}, {@code es/bulk}, ...) as a JSON string, a boolean for the boolean endpoints,
 * a throwable to fail the request or a closure of the request computing one of those. Every request
 * the client sends is recorded.
 */
class FakeElasticsearchTransport implements ElasticsearchTransport {

    static class Call {
        final String endpoint
        final Object request

        Call(String endpoint, Object request) {
            this.endpoint = endpoint
            this.request = request
        }
    }

    final JsonpMapper mapper = new JacksonJsonpMapper()
    final Map<String, Object> responses = [:]
    final List<Call> calls = []
    /** When set the async futures fail through a dependent stage, so the error reaches the caller as a {@link java.util.concurrent.CompletionException}. */
    boolean wrapAsyncFailures = false
    boolean closed = false

    void reset() {
        responses.clear()
        calls.clear()
        wrapAsyncFailures = false
    }

    FakeElasticsearchTransport respond(String endpoint, Object response) {
        responses[endpoint] = response
        return this
    }

    FakeElasticsearchTransport fail(String endpoint, Throwable error) {
        return respond(endpoint, error)
    }

    /** An {@link ElasticsearchException} as the client raises it for an error status. */
    static ElasticsearchException error(String endpoint, int status, String type, String reason) {
        return new ElasticsearchException(endpoint, ErrorResponse.of { it.status(status).error { e -> e.type(type).reason(reason) } })
    }

    List<String> getEndpoints() {
        return calls*.endpoint
    }

    Object request(int index = 0) {
        return calls[index].request
    }

    @Override
    def <RequestT, ResponseT, ErrorT> ResponseT performRequest(RequestT request, Endpoint<RequestT, ResponseT, ErrorT> endpoint, TransportOptions options) throws IOException {
        calls.add(new Call(endpoint.id(), request))
        Object canned = responses[endpoint.id()]
        if (canned instanceof Closure) {
            canned = canned.call(request)
        }
        if (canned == null) {
            throw new IllegalStateException("No canned response for " + endpoint.id())
        }
        if (canned instanceof Throwable) {
            throw (Throwable) canned
        }
        if (endpoint instanceof BooleanEndpoint) {
            return (ResponseT) new BooleanResponse(canned as boolean)
        }
        if (endpoint instanceof JsonEndpoint) {
            JsonEndpoint<RequestT, ResponseT, ErrorT> jsonEndpoint = (JsonEndpoint<RequestT, ResponseT, ErrorT>) endpoint
            return mapper.jsonProvider().createParser(new StringReader(canned as String)).withCloseable { parser ->
                jsonEndpoint.responseDeserializer().deserialize(parser, mapper)
            }
        }
        throw new UnsupportedOperationException("Unsupported endpoint " + endpoint.id())
    }

    @Override
    def <RequestT, ResponseT, ErrorT> CompletableFuture<ResponseT> performRequestAsync(RequestT request, Endpoint<RequestT, ResponseT, ErrorT> endpoint, TransportOptions options) {
        CompletableFuture<ResponseT> future = new CompletableFuture<>()
        try {
            future.complete(performRequest(request, endpoint, options))
        } catch (Throwable e) {
            future.completeExceptionally(e)
        }
        return wrapAsyncFailures ? future.thenApply { it } : future
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
        closed = true
    }
}
