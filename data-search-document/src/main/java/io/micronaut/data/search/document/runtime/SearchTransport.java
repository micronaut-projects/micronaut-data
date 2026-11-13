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
 * appears in the shared runtime. The store transports report every failure of the store or of the
 * connection as a {@link io.micronaut.data.exceptions.DataAccessException}.
 *
 * @since 5.3.0
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
     * Indexes a new document, failing when a document with the same id exists. The default replaces
     * like {@link #index(String, String, Map, DocumentSearchConfiguration.Refresh)}; the store
     * transports override it with a create-only write.
     *
     * @param index The index
     * @param id The document id, or {@code null} to let the store generate one
     * @param source The document
     * @param refresh The refresh policy
     * @return The document id
     * @throws io.micronaut.data.exceptions.DataAccessException When a document with the id exists
     */
    default String create(String index, @Nullable String id, Map<String, Object> source, DocumentSearchConfiguration.Refresh refresh) {
        return index(index, id, source, refresh);
    }

    /**
     * The failure of a create-only write as a {@link io.micronaut.data.exceptions.DataAccessException}: one
     * saying the document exists when the store reports a conflict (HTTP 409), one wrapping the store's own
     * exception otherwise.
     *
     * @param index The index
     * @param id The document id
     * @param status The HTTP status the store answered with
     * @param failure The store's exception
     * @return The exception to throw
     */
    static RuntimeException createFailure(String index, @Nullable String id, int status, RuntimeException failure) {
        if (status == 409) {
            return new io.micronaut.data.exceptions.DataAccessException("A document with id " + id + " already exists in index " + index, failure);
        }
        return new io.micronaut.data.exceptions.DataAccessException("Cannot create the document with id " + id + " in index " + index + ": " + failure.getMessage(), failure);
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
