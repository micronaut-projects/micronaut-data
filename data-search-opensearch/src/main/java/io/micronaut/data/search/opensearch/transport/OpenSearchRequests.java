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

import io.micronaut.core.annotation.Internal;
import io.micronaut.data.exceptions.DataAccessException;
import io.micronaut.data.exceptions.OptimisticLockException;
import io.micronaut.data.search.document.conf.DocumentSearchConfiguration;
import io.micronaut.data.search.document.runtime.SearchTransport;
import org.jspecify.annotations.Nullable;
import jakarta.json.JsonNumber;
import jakarta.json.JsonString;
import jakarta.json.JsonValue;
import jakarta.json.stream.JsonParser;
import org.opensearch.client.json.JsonData;
import org.opensearch.client.json.JsonpMapper;
import org.opensearch.client.opensearch._types.FieldValue;
import org.opensearch.client.opensearch._types.OpenSearchException;
import org.opensearch.client.opensearch._types.Refresh;
import org.opensearch.client.opensearch._types.VersionType;
import org.opensearch.client.opensearch.core.IndexRequest;
import org.opensearch.client.opensearch._types.SortOptions;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.opensearch.client.opensearch.core.BulkRequest;
import org.opensearch.client.opensearch.core.BulkResponse;
import org.opensearch.client.opensearch.core.SearchRequest;
import org.opensearch.client.opensearch.core.bulk.BulkOperation;
import org.opensearch.client.opensearch.core.bulk.BulkResponseItem;
import org.opensearch.client.opensearch.core.SearchResponse;
import org.opensearch.client.opensearch.core.search.Highlight;
import org.opensearch.client.opensearch.core.search.Hit;
import org.opensearch.client.opensearch.core.search.TrackHits;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletionException;

/**
 * Translates the JSON request bodies of the document search store into OpenSearch client requests,
 * shared by the blocking and the async transport.
 */
@Internal
final class OpenSearchRequests {

    private static final String QUERY = "query";

    private OpenSearchRequests() {
    }

    static SearchRequest searchRequest(JsonpMapper mapper, String index, String body) {
        Map<String, Object> parsed = parse(mapper, body);
        SearchRequest.Builder request = new SearchRequest.Builder().index(index).query(query(mapper, parsed));
        Object sort = parsed.get("sort");
        if (sort instanceof List<?> entries) {
            List<SortOptions> options = new ArrayList<>(entries.size());
            for (Object entry : entries) {
                options.add(JsonData.of(entry, mapper).to(SortOptions.class, mapper));
            }
            request.sort(options);
        }
        // The parsed values are JSON-P values, not Java numbers and booleans
        Integer from = scalar(mapper, parsed.get("from"), Integer.class);
        if (from != null) {
            request.from(from);
        }
        Integer size = scalar(mapper, parsed.get("size"), Integer.class);
        if (size != null) {
            request.size(size);
        }
        if (Boolean.TRUE.equals(scalar(mapper, parsed.get("track_total_hits"), Boolean.class))) {
            request.trackTotalHits(TrackHits.of(t -> t.enabled(true)));
        }
        Object highlight = parsed.get("highlight");
        if (highlight != null) {
            request.highlight(JsonData.of(highlightWithFieldMap(highlight), mapper).to(Highlight.class, mapper));
        }
        if (Boolean.TRUE.equals(parsed.get("version"))) {
            request.version(true);
        }
        Double minScore = scalar(mapper, parsed.get("min_score"), Double.class);
        if (minScore != null) {
            request.minScore(minScore);
        }
        if (parsed.get("search_after") instanceof List<?> after) {
            List<FieldValue> values = new ArrayList<>(after.size());
            for (Object value : after) {
                values.add(fieldValue(value));
            }
            request.searchAfter(values);
        }
        return request.build();
    }

    static FieldValue fieldValue(Object value) {
        if (value instanceof Long || value instanceof Integer || value instanceof Short || value instanceof Byte) {
            return FieldValue.of(((Number) value).longValue());
        }
        if (value instanceof Number number) {
            return FieldValue.of(number.doubleValue());
        }
        if (value instanceof Boolean bool) {
            return FieldValue.of(bool);
        }
        return FieldValue.of(String.valueOf(value));
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
        if (cause instanceof OpenSearchException openSearchException && openSearchException.status() == 409) {
            return new OptimisticLockException("Document " + id + " was modified concurrently: " + openSearchException.error().reason(), cause);
        }
        if (cause instanceof RuntimeException runtime) {
            return runtime;
        }
        return new DataAccessException("OpenSearch request failed: " + cause.getMessage(), cause);
    }

    static SearchTransport.SearchHits toHits(SearchResponse<Map> response) {
        List<SearchTransport.SearchHit> hits = new ArrayList<>(response.hits().hits().size());
        for (Hit<Map> hit : response.hits().hits()) {
            hits.add(new SearchTransport.SearchHit(hit.id(), hit.score(), hit.source() == null ? Map.of() : (Map<String, Object>) hit.source(),
                hit.highlight(), hit.version(), sortValues(hit.sort())));
        }
        long total = response.hits().total() == null ? hits.size() : response.hits().total().value();
        Float maxScore = response.hits().maxScore();
        return new SearchTransport.SearchHits(hits, total, maxScore == null ? null : maxScore.doubleValue());
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
                throw new DataAccessException("OpenSearch bulk item failed: " + item.error().reason());
            }
            ids.add(item.id());
        }
        return ids;
    }

    static Map<String, Object> parse(JsonpMapper mapper, String body) {
        try (JsonParser parser = mapper.jsonProvider().createParser(new StringReader(body))) {
            return (Map<String, Object>) Objects.requireNonNull(plain(JsonData.from(parser, mapper).to(Map.class, mapper)));
        }
    }

    /**
     * Turns the JSON-P values the mapper leaves in a parsed body into plain Java values, so the
     * rewritten clauses serialize as JSON again instead of as beans.
     *
     * @param value The parsed value
     * @return The plain value
     */
    @Nullable
    static Object plain(@Nullable Object value) {
        if (value instanceof JsonValue json) {
            return switch (json.getValueType()) {
                case OBJECT -> {
                    Map<String, Object> map = new LinkedHashMap<>();
                    json.asJsonObject().forEach((key, member) -> map.put(key, plain(member)));
                    yield map;
                }
                case ARRAY -> {
                    List<Object> list = new ArrayList<>();
                    for (JsonValue item : json.asJsonArray()) {
                        list.add(plain(item));
                    }
                    yield list;
                }
                case STRING -> ((JsonString) json).getString();
                case NUMBER -> {
                    JsonNumber number = (JsonNumber) json;
                    yield number.isIntegral() ? (Object) number.longValue() : (Object) number.doubleValue();
                }
                case TRUE -> Boolean.TRUE;
                case FALSE -> Boolean.FALSE;
                case NULL -> null;
            };
        }
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> copy = new LinkedHashMap<>();
            map.forEach((key, member) -> copy.put(String.valueOf(key), plain(member)));
            return copy;
        }
        if (value instanceof List<?> list) {
            List<Object> copy = new ArrayList<>(list.size());
            for (Object item : list) {
                copy.add(plain(item));
            }
            return copy;
        }
        return value;
    }

    static Query query(JsonpMapper mapper, Map<String, Object> body) {
        Object query = body.get(QUERY);
        if (query == null) {
            return Query.of(q -> q.matchAll(m -> m));
        }
        return JsonData.of(query, mapper).to(Query.class, mapper);
    }

    /**
     * The query builder lists the highlighted fields as one object per field; the OpenSearch client
     * only reads them as a map.
     *
     * @param highlight The parsed highlight clause
     * @return The clause with the fields as a map
     */
    @SuppressWarnings("unchecked")
    static Object highlightWithFieldMap(Object highlight) {
        if (highlight instanceof Map<?, ?> clause && clause.get("fields") instanceof List<?> fields) {
            Map<String, Object> merged = new LinkedHashMap<>();
            for (Object field : fields) {
                if (field instanceof Map<?, ?> entry) {
                    merged.putAll((Map<String, Object>) entry);
                }
            }
            Map<String, Object> rewritten = new LinkedHashMap<>((Map<String, Object>) clause);
            rewritten.put("fields", merged);
            return rewritten;
        }
        return highlight;
    }

    /**
     * Reads a scalar of a parsed body, which the JSON-P mapper leaves as a JSON value.
     *
     * @param mapper The mapper
     * @param value The parsed value
     * @param type The scalar type
     * @param <T> The scalar type
     * @return The value, or {@code null} when absent
     */
    @Nullable
    static <T> T scalar(JsonpMapper mapper, @Nullable Object value, Class<T> type) {
        if (value == null) {
            return null;
        }
        if (type.isInstance(value)) {
            return type.cast(value);
        }
        if (value instanceof Number number && type == Integer.class) {
            return type.cast(number.intValue());
        }
        if (value instanceof Number number && type == Double.class) {
            return type.cast(number.doubleValue());
        }
        return JsonData.of(value, mapper).to(type, mapper);
    }

    /**
     * @param refresh The configured refresh
     * @return The refresh of a delete_by_query, which only accepts true or false
     */
    static Refresh deleteByQueryRefresh(DocumentSearchConfiguration.Refresh refresh) {
        return refresh == DocumentSearchConfiguration.Refresh.NONE ? Refresh.False : Refresh.True;
    }

    static Refresh refresh(DocumentSearchConfiguration.Refresh refresh) {
        return switch (refresh) {
            case NONE -> Refresh.False;
            case WAIT_FOR -> Refresh.WaitFor;
            case TRUE -> Refresh.True;
        };
    }
}
