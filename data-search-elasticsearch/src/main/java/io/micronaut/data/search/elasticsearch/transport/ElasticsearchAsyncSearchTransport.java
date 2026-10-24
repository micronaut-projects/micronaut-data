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
import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.Refresh;
import co.elastic.clients.elasticsearch._types.VersionType;
import co.elastic.clients.elasticsearch.core.IndexRequest;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.BulkResponse;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.bulk.BulkOperation;
import co.elastic.clients.elasticsearch.core.bulk.BulkResponseItem;
import co.elastic.clients.elasticsearch.core.search.Hit;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Internal;
import io.micronaut.data.exceptions.DataAccessException;
import io.micronaut.data.exceptions.OptimisticLockException;
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
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;

/**
 * The non-blocking {@link AsyncSearchTransport} over the {@link ElasticsearchAsyncClient}, which
 * completes on the HTTP client's threads.
 *
 * @since 5.4.0
 */
@Internal
@Singleton
@Named("default")
@Requires(beans = ElasticsearchAsyncClient.class)
public final class ElasticsearchAsyncSearchTransport implements AsyncSearchTransport {

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
            hits.add(new SearchTransport.SearchHit(hit.id(), hit.score(), hit.source() == null ? Map.of() : (Map<String, Object>) hit.source(),
                hit.highlight(), hit.version(), sortValues(hit.sort())));
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
        return index(index, id, source, null, refresh);
    }

    @Override
    public CompletionStage<String> index(String index, @Nullable String id, Map<String, Object> source, @Nullable Long version, DocumentSearchConfiguration.Refresh refresh) {
        return client.index(i -> indexRequest(i, index, id, source, version, refresh))
            .handle((response, error) -> {
                if (error != null) {
                    throw conflictOrFailure(error, id);
                }
                return response.id();
            });
    }

    static <T> IndexRequest.Builder<T> indexRequest(IndexRequest.Builder<T> builder, String index, @Nullable String id, T source, @Nullable Long version, DocumentSearchConfiguration.Refresh refresh) {
        builder.index(index).id(id).document(source).refresh(refresh(refresh));
        if (version != null) {
            builder.version(version).versionType(VersionType.External);
        }
        return builder;
    }

    /**
     * Maps a version conflict (HTTP 409) to {@link OptimisticLockException}; other failures pass through.
     */
    static RuntimeException conflictOrFailure(Throwable error, @Nullable String id) {
        Throwable cause = error instanceof CompletionException && error.getCause() != null ? error.getCause() : error;
        if (cause instanceof ElasticsearchException elasticsearchException && elasticsearchException.status() == 409) {
            return new OptimisticLockException("Document " + id + " was modified concurrently: " + elasticsearchException.error().reason(), cause);
        }
        if (cause instanceof RuntimeException runtime) {
            return runtime;
        }
        return new DataAccessException("Elasticsearch request failed: " + cause.getMessage(), cause);
    }

    static List<Object> sortValues(List<FieldValue> sort) {
        if (sort == null || sort.isEmpty()) {
            return List.of();
        }
        List<Object> values = new ArrayList<>(sort.size());
        for (FieldValue value : sort) {
            values.add(value._get());
        }
        return values;
    }

    @Override
    public CompletionStage<Optional<SearchTransport.SearchHit>> getHit(String index, String id) {
        return client.get(g -> g.index(index).id(id), Map.class)
            .thenApply(response -> response.found()
                ? Optional.of(new SearchTransport.SearchHit(id, null, (Map<String, Object>) response.source(), Map.of(), response.version(), List.of()))
                : Optional.empty());
    }

    @Override
    public CompletionStage<List<String>> bulkIndex(String index, List<SearchTransport.BulkDocument> documents, DocumentSearchConfiguration.Refresh refresh) {
        return client.bulk(bulkRequest(index, documents, refresh)).thenApply(ElasticsearchAsyncSearchTransport::bulkIds);
    }

    static BulkRequest bulkRequest(String index, List<SearchTransport.BulkDocument> documents, DocumentSearchConfiguration.Refresh refresh) {
        List<BulkOperation> operations = new ArrayList<>(documents.size());
        for (SearchTransport.BulkDocument document : documents) {
            operations.add(BulkOperation.of(op -> op.index(i -> {
                i.id(document.id()).document(document.source());
                if (document.version() != null) {
                    i.version(document.version()).versionType(VersionType.External);
                }
                return i;
            })));
        }
        return BulkRequest.of(b -> b.index(index).operations(operations).refresh(refresh(refresh)));
    }

    static List<String> bulkIds(BulkResponse response) {
        List<String> ids = new ArrayList<>(response.items().size());
        for (BulkResponseItem item : response.items()) {
            if (item.error() != null) {
                if (item.status() == 409) {
                    throw new OptimisticLockException("Document " + item.id() + " was modified concurrently: " + item.error().reason());
                }
                throw new DataAccessException("Elasticsearch bulk item failed: " + item.error().reason());
            }
            ids.add(item.id());
        }
        return ids;
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
}
