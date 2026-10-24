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
import io.micronaut.data.search.document.conf.DocumentSearchConfiguration;
import io.micronaut.data.search.document.runtime.SearchTransport;
import jakarta.json.stream.JsonParser;
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
        if (parsed.get("from") instanceof Number from) {
            request.from(from.intValue());
        }
        if (parsed.get("size") instanceof Number size) {
            request.size(size.intValue());
        }
        if (Boolean.TRUE.equals(parsed.get("track_total_hits"))) {
            request.trackTotalHits(TrackHits.of(t -> t.enabled(true)));
        }
        Object highlight = parsed.get("highlight");
        if (highlight != null) {
            request.highlight(JsonData.of(highlight, mapper).to(Highlight.class, mapper));
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
        List<String> ids = new ArrayList<>(response.items().size());
        for (BulkResponseItem item : response.items()) {
            if (item.error() != null) {
                throw new DataAccessException("OpenSearch bulk item failed: " + item.error().reason());
            }
            ids.add(item.id());
        }
        return ids;
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

    static Refresh refresh(DocumentSearchConfiguration.Refresh refresh) {
        return switch (refresh) {
            case NONE -> Refresh.False;
            case WAIT_FOR -> Refresh.WaitFor;
            case TRUE -> Refresh.True;
        };
    }
}
