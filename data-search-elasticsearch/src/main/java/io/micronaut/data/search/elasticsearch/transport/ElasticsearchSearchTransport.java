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

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.Refresh;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Internal;
import io.micronaut.data.exceptions.DataAccessException;
import io.micronaut.data.search.document.conf.DocumentSearchConfiguration;
import io.micronaut.data.search.document.runtime.SearchTransport;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * {@link SearchTransport} over the Elasticsearch Java client provided by {@code micronaut-elasticsearch}.
 *
 * @since 5.4.0
 */
@Internal
@Singleton
@Named("default")
@Requires(beans = ElasticsearchClient.class)
public final class ElasticsearchSearchTransport implements SearchTransport {

    private final ElasticsearchClient client;

    /**
     * @param client The client
     */
    public ElasticsearchSearchTransport(ElasticsearchClient client) {
        this.client = client;
    }

    @Override
    public SearchHits search(String index, String body) {
        try {
            SearchResponse<Map> response = client.search(s -> s.index(index).withJson(new StringReader(body)), Map.class);
            List<SearchHit> hits = new ArrayList<>(response.hits().hits().size());
            for (Hit<Map> hit : response.hits().hits()) {
                hits.add(new SearchHit(hit.id(), hit.score(), hit.source() == null ? Map.of() : (Map<String, Object>) hit.source()));
            }
            long total = response.hits().total() == null ? hits.size() : response.hits().total().value();
            return new SearchHits(hits, total, response.hits().maxScore());
        } catch (IOException e) {
            throw new DataAccessException("Elasticsearch search failed: " + e.getMessage(), e);
        }
    }

    @Override
    public long count(String index, String body) {
        try {
            return client.count(c -> c.index(index).withJson(new StringReader(body))).count();
        } catch (IOException e) {
            throw new DataAccessException("Elasticsearch count failed: " + e.getMessage(), e);
        }
    }

    @Override
    public String index(String index, @Nullable String id, Map<String, Object> source, DocumentSearchConfiguration.Refresh refresh) {
        try {
            return client.index(i -> i.index(index).id(id).document(source).refresh(refresh(refresh))).id();
        } catch (IOException e) {
            throw new DataAccessException("Elasticsearch index failed: " + e.getMessage(), e);
        }
    }

    @Override
    @Nullable
    public Map<String, Object> get(String index, String id) {
        try {
            var response = client.get(g -> g.index(index).id(id), Map.class);
            return response.found() ? (Map<String, Object>) response.source() : null;
        } catch (IOException e) {
            throw new DataAccessException("Elasticsearch get failed: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean delete(String index, String id, DocumentSearchConfiguration.Refresh refresh) {
        try {
            return client.delete(d -> d.index(index).id(id).refresh(refresh(refresh))).result().jsonValue().equals("deleted");
        } catch (IOException e) {
            throw new DataAccessException("Elasticsearch delete failed: " + e.getMessage(), e);
        }
    }

    @Override
    public long deleteByQuery(String index, String body, DocumentSearchConfiguration.Refresh refresh) {
        try {
            Long deleted = client.deleteByQuery(d -> d.index(index).withJson(new StringReader(body)).refresh(refresh != DocumentSearchConfiguration.Refresh.NONE)).deleted();
            return deleted == null ? 0 : deleted;
        } catch (IOException e) {
            throw new DataAccessException("Elasticsearch delete by query failed: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean indexExists(String index) {
        try {
            return client.indices().exists(e -> e.index(index)).value();
        } catch (IOException e) {
            throw new DataAccessException("Elasticsearch index exists failed: " + e.getMessage(), e);
        }
    }

    @Override
    public void createIndex(String index, String body) {
        try {
            client.indices().create(c -> c.index(index).withJson(new StringReader(body)));
        } catch (IOException e) {
            throw new DataAccessException("Elasticsearch create index failed: " + e.getMessage(), e);
        }
    }

    private static Refresh refresh(DocumentSearchConfiguration.Refresh refresh) {
        return switch (refresh) {
            case NONE -> Refresh.False;
            case WAIT_FOR -> Refresh.WaitFor;
            case TRUE -> Refresh.True;
        };
    }
}
