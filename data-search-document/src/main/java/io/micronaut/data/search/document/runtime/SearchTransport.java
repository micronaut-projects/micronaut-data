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
     * Indexes a document with an external version: the store rejects the request with
     * {@link io.micronaut.data.exceptions.OptimisticLockException} when it already holds the document at
     * that version or a later one.
     *
     * @param index The index name
     * @param id The document id, or {@code null} to let the store assign one
     * @param source The document
     * @param version The external version, or {@code null} for an unconditional write
     * @param refresh The refresh policy
     * @return The document id
     */
    default String index(String index, @Nullable String id, Map<String, Object> source, @Nullable Long version, DocumentSearchConfiguration.Refresh refresh) {
        if (version != null) {
            throw new UnsupportedOperationException("Versioned writes are not supported by " + getClass().getName());
        }
        return index(index, id, source, refresh);
    }

    /**
     * Indexes several new documents in one request, each a create-only write like
     * {@link #create(String, String, Map, DocumentSearchConfiguration.Refresh)}, or, for a document with
     * a version, an externally versioned write like
     * {@link #index(String, String, Map, Long, DocumentSearchConfiguration.Refresh)}. The default sends one
     * request per document and stops at the first failure.
     *
     * @param index The index name
     * @param documents The documents, in order
     * @param refresh The refresh policy
     * @return The document ids, in the same order
     * @throws BulkIndexException When the store rejected some of the documents and wrote the others
     * @throws io.micronaut.data.exceptions.OptimisticLockException When a rejected document conflicted with
     * the stored version; its cause is the {@link BulkIndexException} holding the ids of the written documents
     */
    default List<String> bulkIndex(String index, List<BulkDocument> documents, DocumentSearchConfiguration.Refresh refresh) {
        List<String> ids = new java.util.ArrayList<>(documents.size());
        for (BulkDocument document : documents) {
            try {
                ids.add(document.version() == null
                    ? create(index, document.id(), document.source(), refresh)
                    : index(index, document.id(), document.source(), document.version(), refresh));
            } catch (RuntimeException e) {
                throw BulkResponses.stopped(ids, documents.size(), e);
            }
        }
        return ids;
    }

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
     * Reads a document with its version.
     *
     * @param index The index name
     * @param id The document id
     * @return The hit, or {@code null} when not found
     */
    @Nullable
    default SearchHit getHit(String index, String id) {
        Map<String, Object> source = get(index, id);
        return source == null ? null : new SearchHit(id, null, source);
    }

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
     * Deletes a document only if the store holds it at the given external version or an earlier one.
     *
     * @param index The index
     * @param id The id
     * @param version The version the entity was read at, or {@code null} for an unconditional delete
     * @param refresh The refresh policy
     * @return Whether a document was deleted
     * @throws io.micronaut.data.exceptions.OptimisticLockException When the stored document is newer
     */
    default boolean delete(String index, String id, @Nullable Long version, DocumentSearchConfiguration.Refresh refresh) {
        if (version != null) {
            throw new UnsupportedOperationException("Versioned deletes are not supported by " + getClass().getName());
        }
        return delete(index, id, refresh);
    }

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
     * The field mapping of a {@link io.micronaut.data.model.vector.Vector} property, or {@code null} to
     * leave the field to dynamic mapping.
     *
     * @param dimensions The vector dimension, or {@code -1} when the entity declares none
     * @return The mapping of the field
     */
    @Nullable
    default Map<String, Object> vectorFieldMapping(int dimensions) {
        return null;
    }

    /**
     * Index settings every index of this store needs, empty by default.
     *
     * @return The settings
     */
    default Map<String, Object> indexSettings() {
        return Map.of();
    }

    /**
     * Creates an index.
     *
     * @param index The index
     * @param body The request body JSON with {@code mappings} and optionally {@code settings}
     */
    void createIndex(String index, String body);

    /**
     * Deletes an index with its documents.
     *
     * @param index The index name
     * @since 5.3.0
     */
    void deleteIndex(String index);

    /**
     * Refreshes an index, so the documents indexed so far are searchable.
     *
     * @param index The index name
     * @since 5.3.0
     */
    void refresh(String index);

    /**
     * A document of a bulk request.
     *
     * @param id The document id, or {@code null} to let the store assign one
     * @param source The document
     * @param version The external version, or {@code null} for an unconditional write
     */
    record BulkDocument(@Nullable String id, Map<String, Object> source, @Nullable Long version) {

        /**
         * @param id The document id, or {@code null}
         * @param source The document
         */
        public BulkDocument(@Nullable String id, Map<String, Object> source) {
            this(id, source, null);
        }
    }

    /**
     * One search hit.
     *
     * @param id The document id
     * @param score The relevance score, or {@code null} when the search was not scored
     * @param source The document
     * @param highlights The highlighted fragments by field, empty when none were requested
     * @param version The document version, or {@code null} when it was not requested
     * @param sort The sort values of the hit, empty when the search was not sorted
     */
    record SearchHit(String id,
                     @Nullable Double score,
                     Map<String, Object> source,
                     Map<String, List<String>> highlights,
                     @Nullable Long version,
                     List<Object> sort) {

        public SearchHit {
            highlights = highlights == null ? Map.of() : highlights;
            sort = sort == null ? List.of() : sort;
        }

        public SearchHit(String id, @Nullable Double score, Map<String, Object> source) {
            this(id, score, source, Map.of(), null, List.of());
        }

        public SearchHit(String id, @Nullable Double score, Map<String, Object> source, Map<String, List<String>> highlights) {
            this(id, score, source, highlights, null, List.of());
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
