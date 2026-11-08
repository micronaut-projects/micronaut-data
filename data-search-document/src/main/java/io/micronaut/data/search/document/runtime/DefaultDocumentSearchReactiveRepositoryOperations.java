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
import io.micronaut.data.exceptions.DataAccessException;
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
import java.util.concurrent.CompletionStage;
import java.util.function.Supplier;

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
    // Every operation builds its request on subscription, so nothing runs for an unsubscribed
    // publisher, a retry rebuilds the request, and a failure is an error signal rather than a throw.

    @Override
    public <T> Mono<T> findOne(Class<T> type, Object id) {
        return Mono.defer(() -> {
            RuntimePersistentEntity<T> entity = runtimeEntityRegistry.getEntity(type);
            String documentId = idString(id);
            return call(() -> transport.getHit(indexName(entity), documentId))
                .flatMap(hit -> hit.isEmpty() ? Mono.empty() : Mono.just(this.<T, T>toEntity(hit.get(), entity, null)));
        });
    }

    @Override
    public <T> Mono<T> findOptional(Class<T> type, Object id) {
        return findOne(type, id);
    }

    @Override
    public <T> Mono<Boolean> exists(PreparedQuery<T, Boolean> preparedQuery) {
        return Mono.defer(() -> {
            DocumentStoredQuery<T, Boolean> storedQuery = storedQuery(preparedQuery);
            String body = queryOnly(body(preparedQuery, storedQuery));
            return call(() -> transport.count(indexName(storedQuery.getPersistentEntity()), body)).map(count -> count > 0);
        });
    }

    @Override
    public <T, R> Mono<R> findOne(PreparedQuery<T, R> preparedQuery) {
        return Mono.defer(() -> {
            DocumentStoredQuery<T, R> storedQuery = storedQuery(preparedQuery);
            RuntimePersistentEntity<T> entity = storedQuery.getPersistentEntity();
            if (storedQuery.isCount()) {
                String body = queryOnly(body(preparedQuery, storedQuery));
                // the interceptor converts the count to the method's element type
                return call(() -> transport.count(indexName(entity), body)).map(count -> (R) count);
            }
            Map<String, Object> body = body(preparedQuery, storedQuery);
            if (preparedQuery.getResultType() == SearchResults.class) {
                applyPageable(body, preparedQuery, entity);
                // totalHits is exact, not capped at the result window
                body.put("track_total_hits", true);
                return search(body, entity).map(hits -> (R) toSearchResults(hits, entity, preparedQuery));
            }
            boolean checkUnique = applySingleResult(body, preparedQuery, entity);
            return search(body, entity).mapNotNull(hits -> singleResult(toEntities(hits, entity, preparedQuery), checkUnique, preparedQuery));
        });
    }

    @Override
    public <T, R> Mono<R> findOptional(PreparedQuery<T, R> preparedQuery) {
        return findOne(preparedQuery);
    }

    @Override
    public <T> Flux<T> findAll(PagedQuery<T> pagedQuery) {
        return Flux.defer(() -> {
            RuntimePersistentEntity<T> entity = runtimeEntityRegistry.getEntity(pagedQuery.getRootEntity());
            Map<String, Object> body = matchAll();
            applyPageable(body, pagedQuery, entity);
            return search(body, entity).flatMapIterable(hits -> this.<T, T>toEntities(hits, entity, null));
        });
    }

    @Override
    public <T> Mono<Long> count(PagedQuery<T> pagedQuery) {
        return Mono.defer(() -> {
            RuntimePersistentEntity<T> entity = runtimeEntityRegistry.getEntity(pagedQuery.getRootEntity());
            return call(() -> transport.count(indexName(entity), SearchJson.write(matchAll())));
        });
    }

    @Override
    public <T, R> Flux<R> findAll(PreparedQuery<T, R> preparedQuery) {
        return Flux.defer(() -> {
            DocumentStoredQuery<T, R> storedQuery = storedQuery(preparedQuery);
            RuntimePersistentEntity<T> entity = storedQuery.getPersistentEntity();
            if (storedQuery.isCount() || preparedQuery.getResultType() == SearchResults.class) {
                // the reactive interceptors read a count or a SearchResults as the single element of findAll
                return findOne(preparedQuery).flux();
            }
            Map<String, Object> body = body(preparedQuery, storedQuery);
            applyPageable(body, preparedQuery, entity);
            return search(body, entity).flatMapIterable(hits -> toEntities(hits, entity, preparedQuery));
        });
    }

    @Override
    public <R> Mono<Page<R>> findPage(PagedQuery<R> pagedQuery) {
        return Mono.defer(() -> {
            RuntimePersistentEntity<R> entity = runtimeEntityRegistry.getEntity(pagedQuery.getRootEntity());
            PreparedQuery<R, R> preparedQuery = pagedQuery instanceof PreparedQuery<?, ?> pq ? (PreparedQuery<R, R>) pq : null;
            return search(pagedBody(pagedQuery, entity), entity)
                .map(hits -> toPage(hits, pagedQuery, entity, preparedQuery));
        });
    }

    // ---------- writes

    @Override
    public <T> Mono<T> persist(InsertOperation<T> operation) {
        return Mono.defer(() -> persistOne(operation.getEntity(), runtimeEntityRegistry.getEntity(operation.getRootEntity()),
            replacesExisting(operation.getAnnotationMetadata())));
    }

    @Override
    public <T> Flux<T> persistAll(InsertBatchOperation<T> operation) {
        return Flux.defer(() -> {
            // built per subscription, so listeners run on subscribe and a retry starts from fresh state
            RuntimePersistentEntity<T> entity = runtimeEntityRegistry.getEntity(operation.getRootEntity());
            boolean replace = replacesExisting(operation.getAnnotationMetadata());
            // the results keep the input order, with vetoed entities in place
            List<T> results = new ArrayList<>();
            List<T> toIndex = new ArrayList<>();
            List<Integer> positions = new ArrayList<>();
            List<SearchTransport.BulkDocument> documents = new ArrayList<>();
            for (T value : operation) {
                T current = prePersist(value, entity);
                if (current == null) {
                    results.add(value);
                } else {
                    positions.add(results.size());
                    results.add(current);
                    toIndex.add(current);
                    Long version = persistVersion(current, entity);
                    documents.add(new SearchTransport.BulkDocument(documentId(current, entity), toDocument(current, entity), version, !replace && version == null));
                }
            }
            if (documents.isEmpty()) {
                return Flux.fromIterable(results);
            }
            return call(() -> transport.bulkIndex(indexName(entity), documents, configuration.getRefresh()))
                .flatMapIterable(ids -> {
                    for (int i = 0; i < toIndex.size(); i++) {
                        results.set(positions.get(i), postPersist(toIndex.get(i), entity, ids.get(i)));
                    }
                    return results;
                });
        });
    }

    @Override
    public <T> Mono<T> update(UpdateOperation<T> operation) {
        return Mono.defer(() -> updateOne(operation.getEntity(), runtimeEntityRegistry.getEntity(operation.getRootEntity())));
    }

    @Override
    public <T> Flux<T> updateAll(UpdateBatchOperation<T> operation) {
        return Flux.defer(() -> {
            RuntimePersistentEntity<T> entity = runtimeEntityRegistry.getEntity(operation.getRootEntity());
            return Flux.fromIterable(operation).concatMap(value -> updateOne(value, entity));
        });
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
        return Mono.defer(() -> {
            DocumentStoredQuery<T, Number> storedQuery = deleteStoredQuery(preparedQuery);
            String body = queryOnly(body(preparedQuery, storedQuery));
            return call(() -> transport.deleteByQuery(indexName(storedQuery.getPersistentEntity()), body, configuration.getRefresh()))
                .map(deleted -> (Number) deleted);
        });
    }

    @Override
    public <T> Mono<Number> delete(DeleteOperation<T> operation) {
        return Mono.defer(() -> deleteOne(operation.getEntity(), runtimeEntityRegistry.getEntity(operation.getRootEntity())))
            .map(deleted -> (Number) (deleted ? 1 : 0));
    }

    @Override
    public <T> Mono<Number> deleteAll(DeleteBatchOperation<T> operation) {
        return Flux.defer(() -> {
            RuntimePersistentEntity<T> entity = runtimeEntityRegistry.getEntity(operation.getRootEntity());
            return Flux.fromIterable(operation).concatMap(value -> deleteOne(value, entity));
        })
            .filter(deleted -> deleted)
            .count()
            .map(count -> (Number) count);
    }

    // ---------- helpers

    private <T> Mono<T> persistOne(T value, RuntimePersistentEntity<T> entity, boolean replace) {
        return Mono.defer(() -> {
            T current = prePersist(value, entity);
            if (current == null) {
                return Mono.just(value);
            }
            Long version = persistVersion(current, entity);
            Map<String, Object> document = toDocument(current, entity);
            String documentId = documentId(current, entity);
            // a create-only write, so saving an entity with the id of an existing document fails instead of
            // replacing it; a versioned entity relies on the external version instead
            return call(() -> version == null && !replace
                ? transport.create(indexName(entity), documentId, document, configuration.getRefresh())
                : transport.index(indexName(entity), documentId, document, version, configuration.getRefresh()))
                .map(assignedId -> postPersist(current, entity, assignedId));
        });
    }

    private <T> Mono<T> updateOne(T value, RuntimePersistentEntity<T> entity) {
        return Mono.defer(() -> {
            T current = preUpdate(value, entity);
            if (current == null) {
                return Mono.just(value);
            }
            Long version = updateVersion(current, entity);
            Map<String, Object> document = toDocument(current, entity);
            String documentId = documentId(current, entity);
            return call(() -> transport.index(indexName(entity), documentId, document, version, configuration.getRefresh()))
                .map(ignored -> postUpdate(current, entity));
        });
    }

    private <T> Mono<Boolean> deleteOne(T value, RuntimePersistentEntity<T> entity) {
        return Mono.defer(() -> {
            String id = preRemove(value, entity);
            if (id == null) {
                return Mono.just(false);
            }
            Long version = deleteVersion(value, entity);
            return call(() -> transport.delete(indexName(entity), id, version, configuration.getRefresh()))
                .doOnNext(deleted -> {
                    if (deleted) {
                        postRemove(value, entity);
                    }
                });
        });
    }

    private <T> Mono<SearchTransport.SearchHits> search(Map<String, Object> body, RuntimePersistentEntity<T> entity) {
        String json = searchJson(body, entity);
        return call(() -> transport.search(indexName(entity), json));
    }

    /**
     * Sends a transport request, reporting a client failure as a {@link DataAccessException} like the
     * blocking transports do.
     */
    private static <R> Mono<R> call(Supplier<CompletionStage<R>> request) {
        return Mono.fromCompletionStage(request)
            .onErrorMap(e -> !(e instanceof DataAccessException), e -> new DataAccessException("Search request failed: " + e.getMessage(), e));
    }
}
