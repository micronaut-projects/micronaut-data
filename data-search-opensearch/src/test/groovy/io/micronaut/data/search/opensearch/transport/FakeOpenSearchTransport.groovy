package io.micronaut.data.search.opensearch.transport

import org.opensearch.client.json.JsonpMapper
import org.opensearch.client.json.jackson.JacksonJsonpMapper
import org.opensearch.client.transport.Endpoint
import org.opensearch.client.transport.JsonEndpoint
import org.opensearch.client.transport.OpenSearchTransport
import org.opensearch.client.transport.TransportOptions
import org.opensearch.client.transport.endpoints.BooleanEndpoint
import org.opensearch.client.transport.endpoints.BooleanResponse

import java.util.concurrent.CompletableFuture

/**
 * An {@link OpenSearchTransport} that never talks to a server: it records every request the client
 * sends and answers it with a canned JSON response, a boolean, or a failure keyed by the request type.
 * A checked {@link IOException} is thrown from the submission itself, also on the async path, so the
 * transports' handling of the client's declared exception is exercised; any other failure of the async
 * path completes the returned future exceptionally, as the real transport does.
 */
class FakeOpenSearchTransport implements OpenSearchTransport {

    final JsonpMapper mapper = new JacksonJsonpMapper()

    /** The requests in submission order */
    final List<Object> requests = []
    /** The HTTP method and path of each request, in submission order */
    final List<String> urls = []
    /** The query parameters of each request, in submission order */
    final List<Map<String, String>> parameters = []

    private final Map<Class<?>, Object> answers = [:]
    private Throwable failure

    /**
     * @param requestType The request class
     * @param answer A JSON string, a {@link Boolean} for a boolean endpoint, or a {@link Throwable}
     * @return this
     */
    FakeOpenSearchTransport answer(Class<?> requestType, Object answer) {
        answers[requestType] = answer
        return this
    }

    /**
     * Fails every request with the given error.
     */
    FakeOpenSearchTransport failWith(Throwable error) {
        failure = error
        return this
    }

    @Override
    <RequestT, ResponseT, ErrorT> ResponseT performRequest(RequestT request, Endpoint<RequestT, ResponseT, ErrorT> endpoint, TransportOptions options) throws IOException {
        requests << request
        urls << endpoint.method(request) + ' ' + endpoint.requestUrl(request)
        parameters << endpoint.queryParameters(request)
        if (failure != null) {
            throw failure
        }
        Object answer = answers[request.getClass()]
        if (answer == null) {
            throw new IllegalStateException("No canned answer for " + request.getClass().simpleName)
        }
        if (answer instanceof Throwable) {
            throw answer
        }
        if (endpoint instanceof BooleanEndpoint) {
            return (ResponseT) new BooleanResponse(answer as boolean)
        }
        def parser = mapper.jsonProvider().createParser(new StringReader(answer as String))
        return ((JsonEndpoint<RequestT, ResponseT, ErrorT>) endpoint).responseDeserializer().deserialize(parser, mapper)
    }

    @Override
    <RequestT, ResponseT, ErrorT> CompletableFuture<ResponseT> performRequestAsync(RequestT request, Endpoint<RequestT, ResponseT, ErrorT> endpoint, TransportOptions options) {
        try {
            return CompletableFuture.completedFuture(performRequest(request, endpoint, options))
        } catch (IOException e) {
            // Groovy lets the checked exception out of the submission, as the client declares it
            throw e
        } catch (Throwable e) {
            return CompletableFuture.failedFuture(e)
        }
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
    }
}
