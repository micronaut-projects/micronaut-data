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

import io.micronaut.data.search.document.conf.DocumentSearchConfiguration;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * The non-blocking counterpart of {@link SearchTransport}: the same requests, completing on the
 * client's own threads. A store that provides one, under the same bean name as its
 * {@link SearchTransport}, gets reactive and async repositories that never block; without it the
 * blocking transport is run on the IO executor.
 *
 * @since 5.4.0
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
     * Indexes several documents in one request. The default sends one request per document, in sequence.
     *
     * @param index The index name
     * @param documents The documents, in order
     * @param refresh The refresh policy
     * @return The document ids, in the same order
     */
    default CompletionStage<List<String>> bulkIndex(String index, List<SearchTransport.BulkDocument> documents, DocumentSearchConfiguration.Refresh refresh) {
        CompletionStage<List<String>> ids = CompletableFuture.completedFuture(new ArrayList<>(documents.size()));
        for (SearchTransport.BulkDocument document : documents) {
            ids = ids.thenCompose(list -> index(index, document.id(), document.source(), refresh).thenApply(id -> {
                list.add(id);
                return list;
            }));
        }
        return ids;
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
