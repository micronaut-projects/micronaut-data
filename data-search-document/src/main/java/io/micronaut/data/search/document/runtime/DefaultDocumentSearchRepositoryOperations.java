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

import io.micronaut.aop.MethodInvocationContext;
import io.micronaut.context.BeanContext;
import io.micronaut.context.annotation.EachBean;
import io.micronaut.context.annotation.Parameter;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.type.Argument;
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
import io.micronaut.data.model.runtime.StoredQuery;
import io.micronaut.data.model.runtime.UpdateBatchOperation;
import io.micronaut.data.model.runtime.UpdateOperation;
import io.micronaut.data.model.vector.search.SearchResults;
import io.micronaut.data.operations.async.AsyncCapableRepository;
import io.micronaut.data.operations.async.AsyncRepositoryOperations;
import io.micronaut.data.operations.reactive.ReactiveCapableRepository;
import io.micronaut.data.operations.reactive.ReactiveRepositoryOperations;
import io.micronaut.data.runtime.convert.DataConversionService;
import io.micronaut.data.runtime.date.DateTimeProvider;
import io.micronaut.data.runtime.operations.ExecutorAsyncOperations;
import io.micronaut.data.runtime.operations.ExecutorReactiveOperations;
import io.micronaut.data.runtime.operations.ReactorToAsyncOperationsAdaptor;
import io.micronaut.data.runtime.operations.internal.ExecutorServiceResolver;
import io.micronaut.data.runtime.query.MethodContextAwareStoredQueryDecorator;
import io.micronaut.data.search.document.conf.DocumentSearchConfiguration;
import io.micronaut.data.search.document.query.SearchJson;
import io.micronaut.inject.qualifiers.Qualifiers;
import io.micronaut.json.JsonMapper;
import jakarta.inject.Named;
import java.io.IOException;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.stream.Stream;

/**
 * Repository operations over a {@link SearchTransport}. Entities are converted to and from documents
 * with the Micronaut {@link JsonMapper}, so Serde annotations control the document shape. Reactive
 * and async repositories use the {@link AsyncSearchTransport} of the same name when the store
 * provides one, and run this blocking transport on the IO executor otherwise.
 *
 * @since 5.4.0
 */
@Internal
@EachBean(SearchTransport.class)
public final class DefaultDocumentSearchRepositoryOperations extends AbstractDocumentSearchRepositoryOperations
    implements DocumentSearchRepositoryOperations, SearchQueryExecutor, MethodContextAwareStoredQueryDecorator, ReactiveCapableRepository, AsyncCapableRepository {

    private final String name;
    private final SearchTransport transport;
    private final BeanContext beanContext;
    private final ExecutorServiceResolver executorServiceResolver;
    @Nullable
    private volatile ReactiveRepositoryOperations reactiveOperations;
    @Nullable
    private volatile AsyncRepositoryOperations asyncOperations;

    /**
     * @param name The transport bean name
     * @param transport The transport
     * @param jsonMapper The JSON mapper
     * @param configuration The configuration
     * @param dateTimeProvider The date time provider
     * @param runtimeEntityRegistry The entity registry
     * @param conversionService The conversion service
     * @param attributeConverterRegistry The attribute converter registry
     * @param beanContext The bean context, to find the async transport of the same name
     * @param executorService The IO executor for the blocking fallback of reactive repositories
     */
    DefaultDocumentSearchRepositoryOperations(@Parameter @Nullable String name,
                                              @Parameter SearchTransport transport,
                                              JsonMapper jsonMapper,
                                              DocumentSearchConfiguration configuration,
                                              DateTimeProvider<Object> dateTimeProvider,
                                              RuntimeEntityRegistry runtimeEntityRegistry,
                                              DataConversionService conversionService,
                                              AttributeConverterRegistry attributeConverterRegistry,
                                              BeanContext beanContext,
                                              @Named("io") @Nullable ExecutorService executorService) {
        super(jsonMapper, configuration, dateTimeProvider, runtimeEntityRegistry, conversionService, attributeConverterRegistry);
        this.name = name == null ? "default" : name;
        this.transport = transport;
        this.beanContext = beanContext;
        this.executorServiceResolver = new ExecutorServiceResolver(executorService);
    }

    @Override
    public SearchTransport getTransport() {
        return transport;
    }

    @Override
    public String getIndexName(Class<?> entityType) {
        return indexName(runtimeEntityRegistry.getEntity(entityType));
    }

    @Override
    public <E, R> StoredQuery<E, R> decorate(MethodInvocationContext<?, ?> context, StoredQuery<E, R> storedQuery) {
        RuntimePersistentEntity<E> entity = runtimeEntityRegistry.getEntity(storedQuery.getRootEntity());
        return new DocumentStoredQuery<>(storedQuery, entity, conversionService, jsonMapper);
    }

    // ---------- reactive and async

    @Override
    public ReactiveRepositoryOperations reactive() {
        ReactiveRepositoryOperations operations = reactiveOperations;
        if (operations == null) {
            Optional<DefaultDocumentSearchReactiveRepositoryOperations> nonBlocking =
                beanContext.findBean(DefaultDocumentSearchReactiveRepositoryOperations.class, Qualifiers.byName(name));
            operations = nonBlocking.isPresent()
                ? nonBlocking.get()
                : new ExecutorReactiveOperations(this, executorServiceResolver.get(), conversionService);
            reactiveOperations = operations;
        }
        return operations;
    }

    @Override
    public AsyncRepositoryOperations async() {
        AsyncRepositoryOperations operations = asyncOperations;
        if (operations == null) {
            operations = reactive() instanceof DefaultDocumentSearchReactiveRepositoryOperations reactor
                ? new ReactorToAsyncOperationsAdaptor(reactor, executorServiceResolver.get())
                : new ExecutorAsyncOperations(this, executorServiceResolver.get());
            asyncOperations = operations;
        }
        return operations;
    }

    // ---------- raw bodies

    @Override
    public <T> SearchResults<T> search(Class<T> entityType, String body) {
        RuntimePersistentEntity<T> entity = runtimeEntityRegistry.getEntity(entityType);
        return toSearchResults(transport.search(indexName(entity), searchJson(parseBody(body), entity)), entity, null);
    }

    @Override
    public <T> List<T> find(Class<T> entityType, String body) {
        RuntimePersistentEntity<T> entity = runtimeEntityRegistry.getEntity(entityType);
        return search(parseBody(body), entity, null);
    }

    @Override
    public long count(Class<?> entityType, String body) {
        RuntimePersistentEntity<?> entity = runtimeEntityRegistry.getEntity(entityType);
        return transport.count(indexName(entity), queryOnly(parseBody(body)));
    }

    private Map<String, Object> parseBody(String body) {
        try {
            return Objects.requireNonNull(jsonMapper.readValue(body, Argument.mapOf(String.class, Object.class)), "body");
        } catch (IOException e) {
            throw new DataAccessException("Cannot parse the search request body: " + e.getMessage(), e);
        }
    }

    // ---------- reads

    @Override
    @Nullable
    public <T, R> R findOne(PreparedQuery<T, R> preparedQuery) {
        DocumentStoredQuery<T, R> storedQuery = storedQuery(preparedQuery);
        if (storedQuery.isCount()) {
            long count = transport.count(indexName(storedQuery.getPersistentEntity()), queryOnly(body(preparedQuery, storedQuery)));
            return conversionService.convertRequired(count, preparedQuery.getResultArgument());
        }
        Map<String, Object> body = body(preparedQuery, storedQuery);
        RuntimePersistentEntity<T> entity = storedQuery.getPersistentEntity();
        if (preparedQuery.getResultType() == SearchResults.class) {
            applyPageable(body, preparedQuery, entity);
            // totalHits is exact, not capped at the result window
            body.put("track_total_hits", true);
            return (R) toSearchResults(transport.search(indexName(entity), searchJson(body, entity)), entity, preparedQuery);
        }
        // a single result still honours a Sort parameter, which decides which hit comes first
        applyPageable(body, preparedQuery, entity);
        body.put("size", 1);
        List<R> results = search(body, entity, preparedQuery);
        return results.isEmpty() ? null : results.get(0);
    }

    @Override
    public <T> boolean exists(PreparedQuery<T, Boolean> preparedQuery) {
        DocumentStoredQuery<T, Boolean> storedQuery = storedQuery(preparedQuery);
        return transport.count(indexName(storedQuery.getPersistentEntity()), queryOnly(body(preparedQuery, storedQuery))) > 0;
    }

    @Override
    public <T> Iterable<T> findAll(PagedQuery<T> query) {
        RuntimePersistentEntity<T> entity = runtimeEntityRegistry.getEntity(query.getRootEntity());
        Map<String, Object> body = matchAll();
        applyPageable(body, query, entity);
        return search(body, entity, null);
    }

    @Override
    public <T> long count(PagedQuery<T> pagedQuery) {
        RuntimePersistentEntity<T> entity = runtimeEntityRegistry.getEntity(pagedQuery.getRootEntity());
        return transport.count(indexName(entity), SearchJson.write(matchAll()));
    }

    @Override
    public <T, R> Iterable<R> findAll(PreparedQuery<T, R> preparedQuery) {
        DocumentStoredQuery<T, R> storedQuery = storedQuery(preparedQuery);
        if (storedQuery.isCount() || preparedQuery.getResultType() == SearchResults.class) {
            // A count or a SearchResults is one value built from the whole response
            R result = findOne(preparedQuery);
            return result == null ? List.of() : List.of(result);
        }
        Map<String, Object> body = body(preparedQuery, storedQuery);
        RuntimePersistentEntity<T> entity = storedQuery.getPersistentEntity();
        applyPageable(body, preparedQuery, entity);
        return search(body, entity, preparedQuery);
    }

    @Override
    public <T, R> Stream<R> findStream(PreparedQuery<T, R> preparedQuery) {
        return ((List<R>) findAll(preparedQuery)).stream();
    }

    @Override
    public <T> Stream<T> findStream(PagedQuery<T> query) {
        return ((List<T>) findAll(query)).stream();
    }

    @Override
    public <R> Page<R> findPage(PagedQuery<R> query) {
        RuntimePersistentEntity<R> entity = runtimeEntityRegistry.getEntity(query.getRootEntity());
        PreparedQuery<R, R> preparedQuery = query instanceof PreparedQuery<?, ?> pq ? (PreparedQuery<R, R>) pq : null;
        SearchTransport.SearchHits hits = transport.search(indexName(entity), searchJson(pagedBody(query, entity), entity));
        return toPage(hits, query, entity, preparedQuery);
    }

    // ---------- writes

    @Override
    public <T> T persist(InsertOperation<T> operation) {
        RuntimePersistentEntity<T> entity = runtimeEntityRegistry.getEntity(operation.getRootEntity());
        return persistOne(operation.getEntity(), entity);
    }

    @Override
    public <T> Iterable<T> persistAll(InsertBatchOperation<T> operation) {
        RuntimePersistentEntity<T> entity = runtimeEntityRegistry.getEntity(operation.getRootEntity());
        List<T> persisted = new ArrayList<>();
        List<T> toIndex = new ArrayList<>();
        // the position of each indexed entity in the result, as equal entities cannot be told apart by indexOf
        List<Integer> positions = new ArrayList<>();
        List<SearchTransport.BulkDocument> documents = new ArrayList<>();
        for (T value : operation) {
            T current = prePersist(value, entity);
            if (current == null) {
                persisted.add(value);
            } else {
                positions.add(persisted.size());
                persisted.add(current);
                toIndex.add(current);
                Long version = persistVersion(current, entity);
                documents.add(new SearchTransport.BulkDocument(documentId(current, entity), toDocument(current, entity), version));
            }
        }
        if (documents.isEmpty()) {
            return persisted;
        }
        List<String> ids = transport.bulkIndex(indexName(entity), documents, configuration.getRefresh());
        for (int i = 0; i < toIndex.size(); i++) {
            T current = toIndex.get(i);
            T result = postPersist(current, entity, ids.get(i));
            persisted.set(positions.get(i), result);
        }
        return persisted;
    }

    @Override
    public <T> T update(UpdateOperation<T> operation) {
        RuntimePersistentEntity<T> entity = runtimeEntityRegistry.getEntity(operation.getRootEntity());
        return updateOne(operation.getEntity(), entity);
    }

    @Override
    public <T> Iterable<T> updateAll(UpdateBatchOperation<T> operation) {
        RuntimePersistentEntity<T> entity = runtimeEntityRegistry.getEntity(operation.getRootEntity());
        List<T> updated = new ArrayList<>();
        for (T value : operation) {
            updated.add(updateOne(value, entity));
        }
        return updated;
    }

    @Override
    public Optional<Number> executeUpdate(PreparedQuery<?, Number> preparedQuery) {
        return deleteByQuery(preparedQuery);
    }

    private <T> Optional<Number> deleteByQuery(PreparedQuery<T, Number> preparedQuery) {
        DocumentStoredQuery<T, Number> storedQuery = deleteStoredQuery(preparedQuery);
        long deleted = transport.deleteByQuery(indexName(storedQuery.getPersistentEntity()), queryOnly(body(preparedQuery, storedQuery)), configuration.getRefresh());
        return Optional.of(deleted);
    }

    @Override
    @Nullable
    public <T> T findOne(Class<T> type, Object id) {
        RuntimePersistentEntity<T> entity = runtimeEntityRegistry.getEntity(type);
        SearchTransport.SearchHit hit = transport.getHit(indexName(entity), idString(id));
        if (hit == null) {
            return null;
        }
        return toEntity(hit, entity, null);
    }

    @Override
    public <T> int delete(DeleteOperation<T> operation) {
        RuntimePersistentEntity<T> entity = runtimeEntityRegistry.getEntity(operation.getRootEntity());
        return deleteOne(operation.getEntity(), entity) ? 1 : 0;
    }

    @Override
    public <T> Optional<Number> deleteAll(DeleteBatchOperation<T> operation) {
        RuntimePersistentEntity<T> entity = runtimeEntityRegistry.getEntity(operation.getRootEntity());
        int deleted = 0;
        for (T value : operation) {
            if (deleteOne(value, entity)) {
                deleted++;
            }
        }
        return Optional.of(deleted);
    }

    // ---------- helpers

    private <T> T persistOne(T value, RuntimePersistentEntity<T> entity) {
        T current = prePersist(value, entity);
        if (current == null) {
            return value;
        }
        Long version = persistVersion(current, entity);
        String documentId = transport.index(indexName(entity), documentId(current, entity), toDocument(current, entity), version, configuration.getRefresh());
        return postPersist(current, entity, documentId);
    }

    private <T> T updateOne(T value, RuntimePersistentEntity<T> entity) {
        T current = preUpdate(value, entity);
        if (current == null) {
            return value;
        }
        Long version = updateVersion(current, entity);
        transport.index(indexName(entity), documentId(current, entity), toDocument(current, entity), version, configuration.getRefresh());
        return postUpdate(current, entity);
    }

    private <T> boolean deleteOne(T value, RuntimePersistentEntity<T> entity) {
        String id = preRemove(value, entity);
        if (id == null) {
            return false;
        }
        boolean deleted = transport.delete(indexName(entity), id, configuration.getRefresh());
        postRemove(value, entity);
        return deleted;
    }

    private <T, R> List<R> search(Map<String, Object> body, RuntimePersistentEntity<T> entity, @Nullable PreparedQuery<T, R> preparedQuery) {
        return toEntities(transport.search(indexName(entity), searchJson(body, entity)), entity, preparedQuery);
    }
}
