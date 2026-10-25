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

import io.micronaut.context.annotation.EachBean;
import io.micronaut.context.annotation.Parameter;
import io.micronaut.core.annotation.Internal;
import io.micronaut.data.model.Page;
import io.micronaut.data.model.runtime.AttributeConverterRegistry;
import io.micronaut.data.model.runtime.DeleteBatchOperation;
import io.micronaut.data.model.runtime.DeleteOperation;
import io.micronaut.data.model.runtime.InsertBatchOperation;
import io.micronaut.data.model.runtime.InsertOperation;
import io.micronaut.data.model.runtime.PagedQuery;
import io.micronaut.data.model.runtime.PreparedQuery;
import io.micronaut.data.model.runtime.RuntimeEntityRegistry;
import io.micronaut.data.model.runtime.RuntimePersistentEntity;
import io.micronaut.data.model.runtime.UpdateBatchOperation;
import io.micronaut.data.model.runtime.UpdateOperation;
import io.micronaut.data.model.vector.search.SearchResults;
import io.micronaut.data.operations.reactive.ReactorReactiveRepositoryOperations;
import io.micronaut.data.runtime.convert.DataConversionService;
import io.micronaut.data.runtime.date.DateTimeProvider;
import io.micronaut.data.search.document.conf.DocumentSearchConfiguration;
import io.micronaut.data.search.document.query.SearchJson;
import io.micronaut.json.JsonMapper;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Non-blocking repository operations over an {@link AsyncSearchTransport}, backing the reactive and
 * async repositories of the {@link SearchTransport} with the same name.
 *
 * @since 5.4.0
 */
@Internal
@EachBean(AsyncSearchTransport.class)
public final class DefaultDocumentSearchReactiveRepositoryOperations extends AbstractDocumentSearchRepositoryOperations
    implements ReactorReactiveRepositoryOperations {

    private final AsyncSearchTransport transport;

    /**
     * @param transport The async transport
     * @param jsonMapper The JSON mapper
     * @param configuration The configuration
     * @param dateTimeProvider The date time provider
     * @param runtimeEntityRegistry The entity registry
     * @param conversionService The conversion service
     * @param attributeConverterRegistry The attribute converter registry
     */
    DefaultDocumentSearchReactiveRepositoryOperations(@Parameter AsyncSearchTransport transport,
                                                      JsonMapper jsonMapper,
                                                      DocumentSearchConfiguration configuration,
                                                      DateTimeProvider<Object> dateTimeProvider,
                                                      RuntimeEntityRegistry runtimeEntityRegistry,
                                                      DataConversionService conversionService,
                                                      AttributeConverterRegistry attributeConverterRegistry) {
        super(jsonMapper, configuration, dateTimeProvider, runtimeEntityRegistry, conversionService, attributeConverterRegistry);
        this.transport = transport;
    }

    // ---------- reads

    @Override
    public <T> Mono<T> findOne(Class<T> type, Object id) {
        RuntimePersistentEntity<T> entity = runtimeEntityRegistry.getEntity(type);
        String documentId = idString(id);
        return Mono.fromCompletionStage(() -> transport.get(indexName(entity), documentId))
            .flatMap(source -> source.isEmpty()
                ? Mono.empty()
                : Mono.just(this.<T, T>toEntity(new SearchTransport.SearchHit(documentId, null, source.get()), entity, null)));
    }

    @Override
    public <T> Mono<T> findOptional(Class<T> type, Object id) {
        return findOne(type, id);
    }

    @Override
    public <T> Mono<Boolean> exists(PreparedQuery<T, Boolean> preparedQuery) {
        DocumentStoredQuery<T, Boolean> storedQuery = storedQuery(preparedQuery);
        String body = queryOnly(body(preparedQuery, storedQuery));
        return Mono.fromCompletionStage(() -> transport.count(indexName(storedQuery.getPersistentEntity()), body)).map(count -> count > 0);
    }

    @Override
    public <T, R> Mono<R> findOne(PreparedQuery<T, R> preparedQuery) {
        DocumentStoredQuery<T, R> storedQuery = storedQuery(preparedQuery);
        RuntimePersistentEntity<T> entity = storedQuery.getPersistentEntity();
        if (storedQuery.isCount()) {
            String body = queryOnly(body(preparedQuery, storedQuery));
            // the interceptor converts the count to the method's element type
            return Mono.fromCompletionStage(() -> transport.count(indexName(entity), body)).map(count -> (R) count);
        }
        Map<String, Object> body = body(preparedQuery, storedQuery);
        if (preparedQuery.getResultType() == SearchResults.class) {
            applyPageable(body, preparedQuery.getPageable(), entity);
            // totalHits is exact, not capped at the result window
            body.put("track_total_hits", true);
            return search(body, entity).map(hits -> (R) toSearchResults(hits, entity, preparedQuery));
        }
        // a single result still honours a Sort parameter, which decides which hit comes first
        applyPageable(body, preparedQuery.getPageable(), entity);
        body.put("size", 1);
        return search(body, entity).flatMap(hits -> {
            List<R> results = toEntities(hits, entity, preparedQuery);
            return results.isEmpty() ? Mono.empty() : Mono.just(results.get(0));
        });
    }

    @Override
    public <T, R> Mono<R> findOptional(PreparedQuery<T, R> preparedQuery) {
        return findOne(preparedQuery);
    }

    @Override
    public <T> Flux<T> findAll(PagedQuery<T> pagedQuery) {
        RuntimePersistentEntity<T> entity = runtimeEntityRegistry.getEntity(pagedQuery.getRootEntity());
        Map<String, Object> body = matchAll();
        applyPageable(body, pagedQuery.getPageable(), entity);
        return search(body, entity).flatMapIterable(hits -> this.<T, T>toEntities(hits, entity, null));
    }

    @Override
    public <T> Mono<Long> count(PagedQuery<T> pagedQuery) {
        RuntimePersistentEntity<T> entity = runtimeEntityRegistry.getEntity(pagedQuery.getRootEntity());
        return Mono.fromCompletionStage(() -> transport.count(indexName(entity), SearchJson.write(matchAll())));
    }

    @Override
    public <T, R> Flux<R> findAll(PreparedQuery<T, R> preparedQuery) {
        DocumentStoredQuery<T, R> storedQuery = storedQuery(preparedQuery);
        RuntimePersistentEntity<T> entity = storedQuery.getPersistentEntity();
        if (storedQuery.isCount() || preparedQuery.getResultType() == SearchResults.class) {
            // the reactive interceptors read a count or a SearchResults as the single element of findAll
            return findOne(preparedQuery).flux();
        }
        Map<String, Object> body = body(preparedQuery, storedQuery);
        applyPageable(body, preparedQuery.getPageable(), entity);
        return search(body, entity).flatMapIterable(hits -> toEntities(hits, entity, preparedQuery));
    }

    @Override
    public <R> Mono<Page<R>> findPage(PagedQuery<R> pagedQuery) {
        RuntimePersistentEntity<R> entity = runtimeEntityRegistry.getEntity(pagedQuery.getRootEntity());
        PreparedQuery<R, R> preparedQuery = pagedQuery instanceof PreparedQuery<?, ?> pq ? (PreparedQuery<R, R>) pq : null;
        return search(pagedBody(pagedQuery, entity), entity)
            .map(hits -> Page.of(toEntities(hits, entity, preparedQuery), pagedQuery.getPageable(), hits.total()));
    }

    // ---------- writes

    @Override
    public <T> Mono<T> persist(InsertOperation<T> operation) {
        RuntimePersistentEntity<T> entity = runtimeEntityRegistry.getEntity(operation.getRootEntity());
        return persistOne(operation.getEntity(), entity);
    }

    @Override
    public <T> Flux<T> persistAll(InsertBatchOperation<T> operation) {
        RuntimePersistentEntity<T> entity = runtimeEntityRegistry.getEntity(operation.getRootEntity());
        List<T> vetoed = new ArrayList<>();
        List<T> toIndex = new ArrayList<>();
        List<SearchTransport.BulkDocument> documents = new ArrayList<>();
        for (T value : operation) {
            T current = prePersist(value, entity);
            if (current == null) {
                vetoed.add(value);
            } else {
                toIndex.add(current);
                documents.add(new SearchTransport.BulkDocument(documentId(current, entity), toDocument(current, entity)));
            }
        }
        if (documents.isEmpty()) {
            return Flux.fromIterable(vetoed);
        }
        return Mono.fromCompletionStage(() -> transport.bulkIndex(indexName(entity), documents, configuration.getRefresh()))
            .flatMapIterable(ids -> {
                List<T> results = new ArrayList<>(toIndex.size() + vetoed.size());
                for (int i = 0; i < toIndex.size(); i++) {
                    results.add(postPersist(toIndex.get(i), entity, ids.get(i)));
                }
                results.addAll(vetoed);
                return results;
            });
    }

    @Override
    public <T> Mono<T> update(UpdateOperation<T> operation) {
        RuntimePersistentEntity<T> entity = runtimeEntityRegistry.getEntity(operation.getRootEntity());
        return updateOne(operation.getEntity(), entity);
    }

    @Override
    public <T> Flux<T> updateAll(UpdateBatchOperation<T> operation) {
        RuntimePersistentEntity<T> entity = runtimeEntityRegistry.getEntity(operation.getRootEntity());
        return Flux.fromIterable(operation).concatMap(value -> updateOne(value, entity));
    }

    @Override
    public Mono<Number> executeUpdate(PreparedQuery<?, Number> preparedQuery) {
        return deleteByQuery(preparedQuery);
    }

    @Override
    public Mono<Number> executeDelete(PreparedQuery<?, Number> preparedQuery) {
        return deleteByQuery(preparedQuery);
    }

    private <T> Mono<Number> deleteByQuery(PreparedQuery<T, Number> preparedQuery) {
        DocumentStoredQuery<T, Number> storedQuery = deleteStoredQuery(preparedQuery);
        String body = queryOnly(body(preparedQuery, storedQuery));
        return Mono.fromCompletionStage(() -> transport.deleteByQuery(indexName(storedQuery.getPersistentEntity()), body, configuration.getRefresh()))
            .map(deleted -> (Number) deleted);
    }

    @Override
    public <T> Mono<Number> delete(DeleteOperation<T> operation) {
        RuntimePersistentEntity<T> entity = runtimeEntityRegistry.getEntity(operation.getRootEntity());
        return deleteOne(operation.getEntity(), entity).map(deleted -> (Number) (deleted ? 1 : 0));
    }

    @Override
    public <T> Mono<Number> deleteAll(DeleteBatchOperation<T> operation) {
        RuntimePersistentEntity<T> entity = runtimeEntityRegistry.getEntity(operation.getRootEntity());
        return Flux.fromIterable(operation)
            .concatMap(value -> deleteOne(value, entity))
            .filter(deleted -> deleted)
            .count()
            .map(count -> (Number) count);
    }

    // ---------- helpers

    private <T> Mono<T> persistOne(T value, RuntimePersistentEntity<T> entity) {
        T current = prePersist(value, entity);
        if (current == null) {
            return Mono.just(value);
        }
        Map<String, Object> document = toDocument(current, entity);
        String documentId = documentId(current, entity);
        return Mono.fromCompletionStage(() -> transport.index(indexName(entity), documentId, document, configuration.getRefresh()))
            .map(assignedId -> postPersist(current, entity, assignedId));
    }

    private <T> Mono<T> updateOne(T value, RuntimePersistentEntity<T> entity) {
        T current = preUpdate(value, entity);
        if (current == null) {
            return Mono.just(value);
        }
        Map<String, Object> document = toDocument(current, entity);
        String documentId = documentId(current, entity);
        return Mono.fromCompletionStage(() -> transport.index(indexName(entity), documentId, document, configuration.getRefresh()))
            .map(ignored -> postUpdate(current, entity));
    }

    private <T> Mono<Boolean> deleteOne(T value, RuntimePersistentEntity<T> entity) {
        String id = preRemove(value, entity);
        if (id == null) {
            return Mono.just(false);
        }
        return Mono.fromCompletionStage(() -> transport.delete(indexName(entity), id, configuration.getRefresh()))
            .doOnNext(deleted -> postRemove(value, entity));
    }

    private <T> Mono<SearchTransport.SearchHits> search(Map<String, Object> body, RuntimePersistentEntity<T> entity) {
        String json = SearchJson.write(body);
        return Mono.fromCompletionStage(() -> transport.search(indexName(entity), json));
    }
}
