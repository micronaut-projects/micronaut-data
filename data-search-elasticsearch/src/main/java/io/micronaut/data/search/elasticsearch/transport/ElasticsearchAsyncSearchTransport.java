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
import co.elastic.clients.elasticsearch._types.OpType;
import co.elastic.clients.elasticsearch._types.Refresh;
import co.elastic.clients.elasticsearch._types.VersionType;
import co.elastic.clients.elasticsearch.core.IndexRequest;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.BulkResponse;
import co.elastic.clients.elasticsearch.core.DeleteRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.bulk.BulkOperation;
import co.elastic.clients.elasticsearch.core.bulk.BulkResponseItem;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.transport.TransportException;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Internal;
import io.micronaut.data.exceptions.DataAccessException;
import io.micronaut.data.exceptions.OptimisticLockException;
import io.micronaut.core.reflect.ClassUtils;
import io.micronaut.data.search.document.conf.DocumentSearchConfiguration;
import io.micronaut.data.search.document.runtime.AsyncSearchTransport;
import io.micronaut.data.search.document.runtime.BulkResponses;
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
     * Maps a version conflict (HTTP 409) to {@link OptimisticLockException} and the other errors of the
     * store and the I/O failures to {@link DataAccessException}; other runtime failures pass through.
     */
    static RuntimeException conflictOrFailure(Throwable error, @Nullable String id) {
        return conflictOrFailure(error, id, "request");
    }

    /**
     * Maps a version conflict (HTTP 409) to {@link OptimisticLockException} and the other errors of the
     * store and the I/O failures to {@link DataAccessException} naming the operation; other runtime
     * failures pass through. The status is read like {@link #status(Throwable)}, as the async client
     * reports a store response as the exception of its low-level HTTP client.
     */
    static RuntimeException conflictOrFailure(Throwable error, @Nullable String id, String operation) {
        Throwable cause = error instanceof CompletionException && error.getCause() != null ? error.getCause() : error;
        if (status(cause) == 409) {
            String reason = cause instanceof ElasticsearchException elasticsearchException ? elasticsearchException.error().reason() : cause.getMessage();
            return new OptimisticLockException("Document " + id + " was modified concurrently: " + reason, cause);
        }
        if (cause instanceof RuntimeException runtime && !(cause instanceof ElasticsearchException)) {
            return runtime;
        }
        return new DataAccessException("Elasticsearch " + operation + " failed: " + cause.getMessage(), cause);
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
        List<BulkOperation> operations = documents.stream().map(ElasticsearchAsyncSearchTransport::indexOperation).toList();
        return BulkRequest.of(b -> b.index(index).operations(operations).refresh(refresh(refresh)));
    }

    /** One index operation of a bulk request; a versioned document is written with external versioning. */
    private static BulkOperation indexOperation(SearchTransport.BulkDocument document) {
        Long version = document.version();
        if (version == null) {
            // a new document without a version is a create-only write
            return BulkOperation.of(op -> op.create(c -> c.id(document.id()).document(document.source())));
        }
        return BulkOperation.of(op -> op.index(i -> i.id(document.id()).document(document.source()).version(version).versionType(VersionType.External)));
    }

    static List<String> bulkIds(BulkResponse response) {
        return BulkResponses.ids("Elasticsearch", response.items(), item -> item.error() == null ? null : item.error().reason(), BulkResponseItem::id, BulkResponseItem::status);
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
    public CompletionStage<Boolean> delete(String index, String id, @Nullable Long version, DocumentSearchConfiguration.Refresh refresh) {
        return client.delete(d -> deleteRequest(d, index, id, version, refresh))
            .handle((response, error) -> {
                if (error != null) {
                    throw conflictOrFailure(error, id);
                }
                return response.result().jsonValue().equals("deleted");
            });
    }

    /**
     * A delete checked against the version the entity was read at: {@code external_gte} accepts that
     * version and rejects the delete when the stored document is newer.
     */
    static DeleteRequest.Builder deleteRequest(DeleteRequest.Builder builder, String index, String id, @Nullable Long version, DocumentSearchConfiguration.Refresh refresh) {
        builder.index(index).id(id).refresh(refresh(refresh));
        if (version != null) {
            builder.version(version).versionType(VersionType.ExternalGte);
        }
        return builder;
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
