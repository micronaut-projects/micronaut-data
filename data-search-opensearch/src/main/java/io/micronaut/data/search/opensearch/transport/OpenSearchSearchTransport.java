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
import org.jspecify.annotations.Nullable;
import org.opensearch.client.json.JsonData;
import org.opensearch.client.json.JsonpMapper;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch._types.Refresh;
import org.opensearch.client.opensearch._types.mapping.TypeMapping;
import org.opensearch.client.opensearch._types.query_dsl.Query;

import java.io.IOException;
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
            return OpenSearchRequests.toHits(client.search(OpenSearchRequests.searchRequest(mapper, index, body), Map.class));
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
        return index(index, id, source, null, refresh);
    }

    @Override
    public String index(String index, @Nullable String id, Map<String, Object> source, @Nullable Long version, DocumentSearchConfiguration.Refresh refresh) {
        try {
            return client.index(i -> OpenSearchRequests.indexRequest(i, index, id, source, version, refresh)).id();
        } catch (IOException e) {
            throw new DataAccessException("OpenSearch index failed: " + e.getMessage(), e);
        } catch (RuntimeException e) {
            throw OpenSearchRequests.conflictOrFailure(e, id);
        }
    }

    @Override
    @Nullable
    public SearchHit getHit(String index, String id) {
        try {
            var response = client.get(g -> g.index(index).id(id), Map.class);
            return response.found() ? new SearchHit(id, null, (Map<String, Object>) response.source(), Map.of(), response.version(), List.of()) : null;
        } catch (IOException e) {
            throw new DataAccessException("OpenSearch get failed: " + e.getMessage(), e);
        }
    }

    @Override
    public List<String> bulkIndex(String index, List<BulkDocument> documents, DocumentSearchConfiguration.Refresh refresh) {
        try {
            return OpenSearchRequests.bulkIds(client.bulk(OpenSearchRequests.bulkRequest(index, documents, refresh)));
        } catch (IOException e) {
            throw new DataAccessException("OpenSearch bulk failed: " + e.getMessage(), e);
        } catch (RuntimeException e) {
            throw OpenSearchRequests.conflictOrFailure(e, null);
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
            // delete_by_query only accepts true or false, not wait_for
            Long deleted = client.deleteByQuery(d -> d.index(index).query(query).refresh(OpenSearchRequests.deleteByQueryRefresh(refresh))).deleted();
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
        return OpenSearchRequests.parse(mapper, body);
    }

    private Query query(Map<String, Object> body) {
        return OpenSearchRequests.query(mapper, body);
    }

    private static Refresh refresh(DocumentSearchConfiguration.Refresh refresh) {
        return OpenSearchRequests.refresh(refresh);
    }
}
