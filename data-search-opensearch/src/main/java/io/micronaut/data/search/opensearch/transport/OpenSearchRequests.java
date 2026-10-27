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
import io.micronaut.data.search.document.conf.DocumentSearchConfiguration;
import io.micronaut.data.search.document.runtime.BulkResponses;
import io.micronaut.data.search.document.runtime.SearchTransport;
import jakarta.json.stream.JsonParser;
import org.jspecify.annotations.Nullable;
import org.opensearch.client.json.JsonData;
import org.opensearch.client.json.JsonpMapper;
import org.opensearch.client.opensearch._types.Refresh;
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
        return request.build();
    }

    static SearchTransport.SearchHits toHits(SearchResponse<Map> response) {
        List<SearchTransport.SearchHit> hits = new ArrayList<>(response.hits().hits().size());
        for (Hit<Map> hit : response.hits().hits()) {
            hits.add(new SearchTransport.SearchHit(hit.id(), hit.score(), hit.source() == null ? Map.of() : (Map<String, Object>) hit.source(), hit.highlight()));
        }
        long total = response.hits().total() == null ? hits.size() : response.hits().total().value();
        Float maxScore = response.hits().maxScore();
        return new SearchTransport.SearchHits(hits, total, maxScore == null ? null : maxScore.doubleValue());
    }

    static BulkRequest bulkRequest(String index, List<SearchTransport.BulkDocument> documents, DocumentSearchConfiguration.Refresh refresh) {
        List<BulkOperation> operations = new ArrayList<>(documents.size());
        for (SearchTransport.BulkDocument document : documents) {
            operations.add(BulkOperation.of(op -> op.index(i -> i.id(document.id()).document(document.source()))));
        }
        return BulkRequest.of(b -> b.index(index).operations(operations).refresh(refresh(refresh)));
    }

    static List<String> bulkIds(BulkResponse response) {
        return BulkResponses.ids("OpenSearch", response.items(), item -> item.error() == null ? null : item.error().reason(), BulkResponseItem::id);
    }

    static Map<String, Object> parse(JsonpMapper mapper, String body) {
        try (JsonParser parser = mapper.jsonProvider().createParser(new StringReader(body))) {
            return (Map<String, Object>) JsonData.from(parser, mapper).to(Map.class, mapper);
        }
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
