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

import java.util.List;
import java.util.Map;

/**
 * The small client-facing SPI the document search runtime needs. Elasticsearch and OpenSearch each
 * implement it on top of their own Java client; query bodies are passed as JSON so no client type
 * appears in the shared runtime.
 *
 * @since 5.4.0
 */
public interface SearchTransport {

    /**
     * Runs a search.
     *
     * @param index The index
     * @param body The request body JSON with {@code query}, {@code sort}, {@code from} and {@code size}
     * @return The hits
     */
    SearchHits search(String index, String body);

    /**
     * Counts the documents matching a query.
     *
     * @param index The index
     * @param body The request body JSON holding {@code query}
     * @return The count
     */
    long count(String index, String body);

    /**
     * Indexes a document, replacing an existing one with the same id.
     *
     * @param index The index
     * @param id The document id, or {@code null} to let the store generate one
     * @param source The document
     * @param refresh The refresh policy
     * @return The document id
     */
    String index(String index, @Nullable String id, Map<String, Object> source, DocumentSearchConfiguration.Refresh refresh);

    /**
     * Indexes several documents in one request. The default sends one request per document.
     *
     * @param index The index name
     * @param documents The documents, in order
     * @param refresh The refresh policy
     * @return The document ids, in the same order
     */
    default List<String> bulkIndex(String index, List<BulkDocument> documents, DocumentSearchConfiguration.Refresh refresh) {
        List<String> ids = new java.util.ArrayList<>(documents.size());
        for (BulkDocument document : documents) {
            ids.add(index(index, document.id(), document.source(), refresh));
        }
        return ids;
    }

    /**
     * Gets a document by id.
     *
     * @param index The index
     * @param id The id
     * @return The document, or {@code null} when absent
     */
    @Nullable
    Map<String, Object> get(String index, String id);

    /**
     * Deletes a document by id.
     *
     * @param index The index
     * @param id The id
     * @param refresh The refresh policy
     * @return Whether a document was deleted
     */
    boolean delete(String index, String id, DocumentSearchConfiguration.Refresh refresh);

    /**
     * Deletes the documents matching a query.
     *
     * @param index The index
     * @param body The request body JSON holding {@code query}
     * @param refresh The refresh policy
     * @return The number of deleted documents
     */
    long deleteByQuery(String index, String body, DocumentSearchConfiguration.Refresh refresh);

    /**
     * @param index The index
     * @return Whether the index exists
     */
    boolean indexExists(String index);

    /**
     * Creates an index.
     *
     * @param index The index
     * @param body The request body JSON with {@code mappings} and optionally {@code settings}
     */
    void createIndex(String index, String body);

    /**
     * One search hit.
     *
     * @param id The document id
     * @param score The relevance score, or {@code null} when the search was not scored
     * @param source The document
     * @param highlights The highlighted fragments by field, empty when none were requested
     */
    /**
     * A document of a bulk request.
     *
     * @param id The document id, or {@code null} to let the store assign one
     * @param source The document
     */
    record BulkDocument(@Nullable String id, Map<String, Object> source) {
    }

    record SearchHit(String id, @Nullable Double score, Map<String, Object> source, Map<String, List<String>> highlights) {

        public SearchHit {
            highlights = highlights == null ? Map.of() : highlights;
        }

        /**
         * @param id The document id
         * @param score The score
         * @param source The document
         */
        public SearchHit(String id, @Nullable Double score, Map<String, Object> source) {
            this(id, score, source, Map.of());
        }
    }

    /**
     * The hits of a search.
     *
     * @param hits The hits in result order
     * @param total The total number of matching documents
     * @param maxScore The highest score, or {@code null}
     */
    record SearchHits(List<SearchHit> hits, long total, @Nullable Double maxScore) {
    }
}
