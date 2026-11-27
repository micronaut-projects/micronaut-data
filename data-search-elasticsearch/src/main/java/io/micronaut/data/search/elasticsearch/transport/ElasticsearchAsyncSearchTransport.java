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
package io.micronaut.data.search.elasticsearch.transport;

import co.elastic.clients.elasticsearch.ElasticsearchAsyncClient;
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.elasticsearch._types.OpType;
import co.elastic.clients.elasticsearch._types.Refresh;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.transport.TransportException;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.reflect.ClassUtils;
import io.micronaut.data.search.document.conf.DocumentSearchConfiguration;
import io.micronaut.data.search.document.runtime.AsyncSearchTransport;
import io.micronaut.data.search.document.runtime.SearchTransport;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletionStage;

/**
 * The non-blocking {@link AsyncSearchTransport} over the {@link ElasticsearchAsyncClient}, which
 * completes on the HTTP client's threads.
 *
 * @since 5.3.0
 */
@Internal
@Singleton
@Named("default")
@Requires(beans = ElasticsearchAsyncClient.class)
public final class ElasticsearchAsyncSearchTransport implements AsyncSearchTransport {

    private static final boolean REST_CLIENT_PRESENT = ClassUtils.isPresent("org.elasticsearch.client.ResponseException", ElasticsearchAsyncSearchTransport.class.getClassLoader());

    private final ElasticsearchAsyncClient client;

    /**
     * @param client The async client
     */
    public ElasticsearchAsyncSearchTransport(ElasticsearchAsyncClient client) {
        this.client = client;
    }

    @Override
    public CompletionStage<SearchTransport.SearchHits> search(String index, String body) {
        return client.search(s -> s.index(index).withJson(new StringReader(body)), Map.class).thenApply(ElasticsearchAsyncSearchTransport::toHits);
    }

    static SearchTransport.SearchHits toHits(SearchResponse<Map> response) {
        List<SearchTransport.SearchHit> hits = new ArrayList<>(response.hits().hits().size());
        for (Hit<Map> hit : response.hits().hits()) {
            hits.add(new SearchTransport.SearchHit(hit.id(), hit.score(), hit.source() == null ? Map.of() : (Map<String, Object>) hit.source(), hit.highlight()));
        }
        long total = response.hits().total() == null ? hits.size() : response.hits().total().value();
        return new SearchTransport.SearchHits(hits, total, response.hits().maxScore());
    }

    @Override
    public CompletionStage<Long> count(String index, String body) {
        return client.count(c -> c.index(index).withJson(new StringReader(body))).thenApply(response -> response.count());
    }

    @Override
    public CompletionStage<String> index(String index, @Nullable String id, Map<String, Object> source, DocumentSearchConfiguration.Refresh refresh) {
        return client.index(i -> i.index(index).id(id).document(source).refresh(refresh(refresh))).thenApply(response -> response.id());
    }

    @Override
    public CompletionStage<String> create(String index, @Nullable String id, Map<String, Object> source, DocumentSearchConfiguration.Refresh refresh) {
        return client.index(i -> i.index(index).id(id).opType(OpType.Create).document(source).refresh(refresh(refresh)))
            .handle((response, failure) -> {
                if (failure != null) {
                    throw AsyncSearchTransport.createFailure(index, id, failure, ElasticsearchAsyncSearchTransport::status);
                }
                return response.id();
            });
    }

    /**
     * The HTTP status of a failed request. The async client completes a request the store answered
     * with an error with the exception of the low-level HTTP client rather than an
     * {@link ElasticsearchException}, so both are read.
     *
     * @param failure The failure
     * @return The status, or {@code 0} when the failure is not a store response
     */
    static int status(Throwable failure) {
        if (failure instanceof ElasticsearchException e) {
            return e.status();
        }
        if (failure instanceof TransportException e) {
            return e.statusCode();
        }
        if (failure instanceof co.elastic.clients.transport.rest5_client.low_level.ResponseException e) {
            return e.getResponse().getStatusCode();
        }
        return REST_CLIENT_PRESENT ? RestClientStatus.status(failure) : 0;
    }

    @Override
    public CompletionStage<Optional<Map<String, Object>>> get(String index, String id) {
        return client.get(g -> g.index(index).id(id), Map.class)
            .thenApply(response -> response.found() ? Optional.of((Map<String, Object>) response.source()) : Optional.empty());
    }

    @Override
    public CompletionStage<Boolean> delete(String index, String id, DocumentSearchConfiguration.Refresh refresh) {
        return client.delete(d -> d.index(index).id(id).refresh(refresh(refresh))).thenApply(response -> response.result().jsonValue().equals("deleted"));
    }

    @Override
    public CompletionStage<Long> deleteByQuery(String index, String body, DocumentSearchConfiguration.Refresh refresh) {
        return client.deleteByQuery(d -> d.index(index).withJson(new StringReader(body)).refresh(refresh != DocumentSearchConfiguration.Refresh.NONE))
            .thenApply(response -> response.deleted() == null ? 0L : response.deleted());
    }

    static Refresh refresh(DocumentSearchConfiguration.Refresh refresh) {
        return switch (refresh) {
            case NONE -> Refresh.False;
            case WAIT_FOR -> Refresh.WaitFor;
            case TRUE -> Refresh.True;
        };
    }

    /**
     * Reads the low-level {@code RestClient} exception, in its own class so the optional client is
     * loaded only when present.
     */
    private static final class RestClientStatus {

        static int status(Throwable failure) {
            return failure instanceof org.elasticsearch.client.ResponseException e ? e.getResponse().getStatusLine().getStatusCode() : 0;
        }
    }
}
