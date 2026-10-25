/*
 * Copyright 2017-2026 original authors
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
package io.micronaut.data.search.elasticsearch.memory;

import io.micronaut.context.annotation.Replaces;
import io.micronaut.context.annotation.Requires;
import io.micronaut.data.search.document.conf.DocumentSearchConfiguration;
import io.micronaut.data.search.document.runtime.SearchTransport;
import io.micronaut.data.search.elasticsearch.transport.ElasticsearchSearchTransport;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A {@link SearchTransport} that keeps documents in memory and records the request bodies,
 * so the repository operations run without a search server. Searches ignore the query and
 * return every document of the index; a document with {@code pages} of zero has no score.
 */
@Singleton
@Named("default")
@Replaces(ElasticsearchSearchTransport.class)
@Requires(property = "spec.name", value = "DocumentSearchOperationsSpec")
public class InMemorySearchTransport implements SearchTransport {

    final Map<String, Map<String, Map<String, Object>>> indexes = new LinkedHashMap<>();
    final Map<String, String> createdIndexes = new LinkedHashMap<>();
    final List<String> bodies = new ArrayList<>();
    final List<DocumentSearchConfiguration.Refresh> refreshes = new ArrayList<>();
    private int generated;

    private Map<String, Map<String, Object>> documents(String index) {
        return indexes.computeIfAbsent(index, i -> new LinkedHashMap<>());
    }

    void clear() {
        indexes.clear();
        bodies.clear();
        refreshes.clear();
        generated = 0;
    }

    String lastBody() {
        return bodies.get(bodies.size() - 1);
    }

    @Override
    public SearchHits search(String index, String body) {
        bodies.add(body);
        List<SearchHit> hits = new ArrayList<>();
        for (Map.Entry<String, Map<String, Object>> document : documents(index).entrySet()) {
            Object pages = document.getValue().get("pages");
            Double score = Integer.valueOf(0).equals(pages) ? null : 1.5d;
            hits.add(new SearchHit(document.getKey(), score, document.getValue()));
        }
        return new SearchHits(hits, hits.size(), hits.isEmpty() ? null : 1.5d);
    }

    @Override
    public long count(String index, String body) {
        bodies.add(body);
        return documents(index).size();
    }

    @Override
    public String index(String index, @Nullable String id, Map<String, Object> source, DocumentSearchConfiguration.Refresh refresh) {
        refreshes.add(refresh);
        String documentId = id == null ? "generated-" + (++generated) : id;
        documents(index).put(documentId, new LinkedHashMap<>(source));
        return documentId;
    }

    @Override
    public @Nullable Map<String, Object> get(String index, String id) {
        return documents(index).get(id);
    }

    @Override
    public boolean delete(String index, String id, DocumentSearchConfiguration.Refresh refresh) {
        refreshes.add(refresh);
        return documents(index).remove(id) != null;
    }

    @Override
    public long deleteByQuery(String index, String body, DocumentSearchConfiguration.Refresh refresh) {
        bodies.add(body);
        refreshes.add(refresh);
        int size = documents(index).size();
        documents(index).clear();
        return size;
    }

    @Override
    public boolean indexExists(String index) {
        return createdIndexes.containsKey(index);
    }

    @Override
    public void createIndex(String index, String body) {
        createdIndexes.put(index, body);
    }
}
