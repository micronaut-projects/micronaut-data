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

import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Internal;
import io.micronaut.data.exceptions.DataAccessException;
import io.micronaut.data.search.document.conf.DocumentSearchConfiguration;
import io.micronaut.data.search.document.runtime.SearchTransport;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import jakarta.json.stream.JsonParser;
import org.jspecify.annotations.Nullable;
import org.opensearch.client.json.JsonData;
import org.opensearch.client.json.JsonpMapper;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch._types.Refresh;
import org.opensearch.client.opensearch._types.SortOptions;
import org.opensearch.client.opensearch._types.mapping.TypeMapping;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.opensearch.client.opensearch.core.SearchRequest;
import org.opensearch.client.opensearch.core.SearchResponse;
import org.opensearch.client.opensearch.core.search.Highlight;
import org.opensearch.client.opensearch.core.search.Hit;
import org.opensearch.client.opensearch.core.search.TrackHits;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * {@link SearchTransport} over the OpenSearch Java client provided by {@code micronaut-opensearch}.
 * The JSON bodies are parsed with the client's own mapper into typed requests.
 *
 * @since 5.4.0
 */
@Internal
@Singleton
@Named("default")
@Requires(beans = OpenSearchClient.class)
public final class OpenSearchSearchTransport implements SearchTransport {

    private static final String QUERY = "query";

    private final OpenSearchClient client;
    private final JsonpMapper mapper;

    /**
     * @param client The client
     */
    public OpenSearchSearchTransport(OpenSearchClient client) {
        this.client = client;
        this.mapper = client._transport().jsonpMapper();
    }

    @Override
    public SearchHits search(String index, String body) {
        try {
            Map<String, Object> parsed = parse(body);
            SearchRequest.Builder request = new SearchRequest.Builder().index(index).query(query(parsed));
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
            SearchResponse<Map> response = client.search(request.build(), Map.class);
            List<SearchHit> hits = new ArrayList<>(response.hits().hits().size());
            for (Hit<Map> hit : response.hits().hits()) {
                hits.add(new SearchHit(hit.id(), hit.score(), hit.source() == null ? Map.of() : (Map<String, Object>) hit.source(), hit.highlight()));
            }
            long total = response.hits().total() == null ? hits.size() : response.hits().total().value();
            Float maxScore = response.hits().maxScore();
            return new SearchHits(hits, total, maxScore == null ? null : maxScore.doubleValue());
        } catch (IOException e) {
            throw new DataAccessException("OpenSearch search failed: " + e.getMessage(), e);
        }
    }

    @Override
    public long count(String index, String body) {
        try {
            Query query = query(parse(body));
            return client.count(c -> c.index(index).query(query)).count();
        } catch (IOException e) {
            throw new DataAccessException("OpenSearch count failed: " + e.getMessage(), e);
        }
    }

    @Override
    public String index(String index, @Nullable String id, Map<String, Object> source, DocumentSearchConfiguration.Refresh refresh) {
        try {
            return client.index(i -> i.index(index).id(id).document(source).refresh(refresh(refresh))).id();
        } catch (IOException e) {
            throw new DataAccessException("OpenSearch index failed: " + e.getMessage(), e);
        }
    }

    @Override
    @Nullable
    public Map<String, Object> get(String index, String id) {
        try {
            var response = client.get(g -> g.index(index).id(id), Map.class);
            return response.found() ? (Map<String, Object>) response.source() : null;
        } catch (IOException e) {
            throw new DataAccessException("OpenSearch get failed: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean delete(String index, String id, DocumentSearchConfiguration.Refresh refresh) {
        try {
            return client.delete(d -> d.index(index).id(id).refresh(refresh(refresh))).result().jsonValue().equals("deleted");
        } catch (IOException e) {
            throw new DataAccessException("OpenSearch delete failed: " + e.getMessage(), e);
        }
    }

    @Override
    public long deleteByQuery(String index, String body, DocumentSearchConfiguration.Refresh refresh) {
        try {
            Query query = query(parse(body));
            Long deleted = client.deleteByQuery(d -> d.index(index).query(query).refresh(refresh(refresh))).deleted();
            return deleted == null ? 0 : deleted;
        } catch (IOException e) {
            throw new DataAccessException("OpenSearch delete by query failed: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean indexExists(String index) {
        try {
            return client.indices().exists(e -> e.index(index)).value();
        } catch (IOException e) {
            throw new DataAccessException("OpenSearch index exists failed: " + e.getMessage(), e);
        }
    }

    @Override
    public void createIndex(String index, String body) {
        try {
            Map<String, Object> parsed = parse(body);
            Object mappings = parsed.get("mappings");
            client.indices().create(c -> {
                c.index(index);
                if (mappings != null) {
                    c.mappings(JsonData.of(mappings, mapper).to(TypeMapping.class, mapper));
                }
                return c;
            });
        } catch (IOException e) {
            throw new DataAccessException("OpenSearch create index failed: " + e.getMessage(), e);
        }
    }

    private Map<String, Object> parse(String body) {
        try (JsonParser parser = mapper.jsonProvider().createParser(new StringReader(body))) {
            return (Map<String, Object>) JsonData.from(parser, mapper).to(Map.class, mapper);
        }
    }

    private Query query(Map<String, Object> body) {
        Object query = body.get(QUERY);
        if (query == null) {
            return Query.of(q -> q.matchAll(m -> m));
        }
        return JsonData.of(query, mapper).to(Query.class, mapper);
    }

    private static Refresh refresh(DocumentSearchConfiguration.Refresh refresh) {
        return switch (refresh) {
            case NONE -> Refresh.False;
            case WAIT_FOR -> Refresh.WaitFor;
            case TRUE -> Refresh.True;
        };
    }
}
