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
package io.micronaut.data.search.document.runtime;

import io.micronaut.data.exceptions.DataAccessException;
import io.micronaut.data.search.document.conf.DocumentSearchConfiguration;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.function.ToIntFunction;

/**
 * The non-blocking counterpart of {@link SearchTransport}: the same requests, completing on the
 * client's own threads. A store that provides one, under the same bean name as its
 * {@link SearchTransport}, gets reactive and async repositories that never block; without it the
 * blocking transport is run on the IO executor. The returned stages complete on the client's I/O
 * threads, so the reactive and async repositories emit their results there.
 *
 * @since 5.3.0
 */
public interface AsyncSearchTransport {

    /**
     * Runs a search.
     *
     * @param index The index name
     * @param body The request body as JSON
     * @return The hits
     */
    CompletionStage<SearchTransport.SearchHits> search(String index, String body);

    /**
     * Counts the documents matching a query.
     *
     * @param index The index name
     * @param body The request body as JSON, holding the {@code query}
     * @return The count
     */
    CompletionStage<Long> count(String index, String body);

    /**
     * Indexes a document, creating or replacing it.
     *
     * @param index The index name
     * @param id The document id, or {@code null} to let the store assign one
     * @param source The document
     * @param refresh The refresh policy
     * @return The document id
     */
    CompletionStage<String> index(String index, @Nullable String id, Map<String, Object> source, DocumentSearchConfiguration.Refresh refresh);

    /**
     * Indexes several new documents in one request, each a create-only write like
     * {@link #create(String, String, Map, DocumentSearchConfiguration.Refresh)}. The default sends one
     * request per document, in sequence.
     *
     * @param index The index name
     * @param documents The documents, in order
     * @param refresh The refresh policy
     * @return The document ids, in the same order
     */
    default CompletionStage<List<String>> bulkIndex(String index, List<SearchTransport.BulkDocument> documents, DocumentSearchConfiguration.Refresh refresh) {
        CompletionStage<List<String>> ids = CompletableFuture.completedFuture(new ArrayList<>(documents.size()));
        for (SearchTransport.BulkDocument document : documents) {
            ids = ids.thenCompose(list -> create(index, document.id(), document.source(), refresh).thenApply(id -> {
                list.add(id);
                return list;
            }));
        }
        return ids;
    }

    /**
     * Indexes a new document, failing when a document with the same id exists. The default replaces
     * like {@link #index(String, String, Map, DocumentSearchConfiguration.Refresh)}; the store
     * transports override it with a create-only write, which fails with the exception of
     * {@link SearchTransport#createFailure(String, String, int, RuntimeException)}, so a duplicate id is
     * reported as on the blocking transport.
     *
     * @param index The index name
     * @param id The document id, or {@code null} to let the store assign one
     * @param source The document
     * @param refresh The refresh policy
     * @return The document id
     */
    default CompletionStage<String> create(String index, @Nullable String id, Map<String, Object> source, DocumentSearchConfiguration.Refresh refresh) {
        return index(index, id, source, refresh);
    }

    /**
     * The failure of an async create-only write, to complete its stage with: when the store answered
     * with an HTTP status, the exception of {@link SearchTransport#createFailure(String, String, int, RuntimeException)},
     * so a duplicate id (HTTP 409) is reported as on the blocking transport; any other failure as it is.
     * The async clients report a store response either as their store exception or as the exception
     * of their low-level HTTP client, so the transport supplies the status of both.
     *
     * @param index The index
     * @param id The document id
     * @param failure The failure of the request, possibly a {@link CompletionException}
     * @param status The HTTP status of a failure, or {@code 0} when it is not a store response
     * @return The exception to complete the stage with
     */
    static CompletionException createFailure(String index, @Nullable String id, Throwable failure, ToIntFunction<Throwable> status) {
        Throwable cause = failure instanceof CompletionException && failure.getCause() != null ? failure.getCause() : failure;
        int code = status.applyAsInt(cause);
        if (code > 0) {
            // the low-level clients report a response as a checked IOException
            RuntimeException storeFailure = cause instanceof RuntimeException runtimeException
                ? runtimeException
                : new DataAccessException(String.valueOf(cause.getMessage()), cause);
            return new CompletionException(SearchTransport.createFailure(index, id, code, storeFailure));
        }
        return failure instanceof CompletionException completionException ? completionException : new CompletionException(failure);
    }

    /**
     * Reads a document.
     *
     * @param index The index name
     * @param id The document id
     * @return The document, empty when not found
     */
    CompletionStage<Optional<Map<String, Object>>> get(String index, String id);

    /**
     * Deletes a document.
     *
     * @param index The index name
     * @param id The document id
     * @param refresh The refresh policy
     * @return Whether a document was deleted
     */
    CompletionStage<Boolean> delete(String index, String id, DocumentSearchConfiguration.Refresh refresh);

    /**
     * Deletes the documents matching a query.
     *
     * @param index The index name
     * @param body The request body as JSON, holding the {@code query}
     * @param refresh The refresh policy
     * @return The number of deleted documents
     */
    CompletionStage<Long> deleteByQuery(String index, String body, DocumentSearchConfiguration.Refresh refresh);
}
