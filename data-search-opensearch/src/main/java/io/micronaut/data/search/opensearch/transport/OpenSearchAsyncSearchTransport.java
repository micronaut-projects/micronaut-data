/*
 * Copyright 2017-2021 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.data.search.opensearch.transport;

import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Internal;
import io.micronaut.data.exceptions.DataAccessException;
import io.micronaut.data.search.document.conf.DocumentSearchConfiguration;
import io.micronaut.data.search.document.runtime.AsyncSearchTransport;
import io.micronaut.data.search.document.runtime.SearchTransport;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;
import org.opensearch.client.json.JsonpMapper;
import org.opensearch.client.opensearch.OpenSearchAsyncClient;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.opensearch.client.transport.OpenSearchTransport;

import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * The non-blocking {@link AsyncSearchTransport} over an {@link OpenSearchAsyncClient} created from
 * the configured {@link OpenSearchTransport}.
 *
 * @since 5.4.0
 */
@Internal
@Singleton
@Named("default")
@Requires(beans = OpenSearchTransport.class)
public final class OpenSearchAsyncSearchTransport implements AsyncSearchTransport {

    private final OpenSearchAsyncClient client;
    private final JsonpMapper mapper;

    /**
     * @param transport The OpenSearch transport
     */
    public OpenSearchAsyncSearchTransport(OpenSearchTransport transport) {
        this.client = new OpenSearchAsyncClient(transport);
        this.mapper = transport.jsonpMapper();
    }

    @Override
    public CompletionStage<SearchTransport.SearchHits> search(String index, String body) {
        return call(() -> client.search(OpenSearchRequests.searchRequest(mapper, index, body), Map.class)).thenApply(OpenSearchRequests::toHits);
    }

    @Override
    public CompletionStage<Long> count(String index, String body) {
        Query query = OpenSearchRequests.query(mapper, OpenSearchRequests.parse(mapper, body));
        return call(() -> client.count(c -> c.index(index).query(query))).thenApply(response -> response.count());
    }

    @Override
    public CompletionStage<String> index(String index, @Nullable String id, Map<String, Object> source, DocumentSearchConfiguration.Refresh refresh) {
        return call(() -> client.index(i -> i.index(index).id(id).document(source).refresh(OpenSearchRequests.refresh(refresh)))).thenApply(response -> response.id());
    }

    @Override
    public CompletionStage<Optional<Map<String, Object>>> get(String index, String id) {
        return call(() -> client.get(g -> g.index(index).id(id), Map.class))
            .thenApply(response -> response.found() ? Optional.of((Map<String, Object>) response.source()) : Optional.empty());
    }

    @Override
    public CompletionStage<Boolean> delete(String index, String id, DocumentSearchConfiguration.Refresh refresh) {
        return call(() -> client.delete(d -> d.index(index).id(id).refresh(OpenSearchRequests.refresh(refresh)))).thenApply(response -> response.result().jsonValue().equals("deleted"));
    }

    @Override
    public CompletionStage<Long> deleteByQuery(String index, String body, DocumentSearchConfiguration.Refresh refresh) {
        Query query = OpenSearchRequests.query(mapper, OpenSearchRequests.parse(mapper, body));
        return call(() -> client.deleteByQuery(d -> d.index(index).query(query).refresh(OpenSearchRequests.refresh(refresh))))
            .thenApply(response -> response.deleted() == null ? 0L : response.deleted());
    }

    /**
     * Turns the checked exception the client declares on request submission into a failed stage.
     */
    private static <T> CompletionStage<T> call(Request<T> request) {
        try {
            return request.submit();
        } catch (IOException e) {
            return CompletableFuture.failedStage(new DataAccessException("OpenSearch request failed: " + e.getMessage(), e));
        }
    }

    @FunctionalInterface
    private interface Request<T> {
        CompletionStage<T> submit() throws IOException;
    }
}
