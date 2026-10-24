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
import io.micronaut.context.annotation.EachBean;
import io.micronaut.context.annotation.Parameter;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.beans.BeanProperty;
import io.micronaut.core.type.Argument;
import io.micronaut.data.event.EntityEventContext;
import io.micronaut.data.exceptions.DataAccessException;
import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;
import io.micronaut.data.model.Sort;
import io.micronaut.data.model.runtime.AttributeConverterRegistry;
import io.micronaut.data.model.runtime.DeleteBatchOperation;
import io.micronaut.data.model.runtime.DeleteOperation;
import io.micronaut.data.model.runtime.InsertBatchOperation;
import io.micronaut.data.model.runtime.InsertOperation;
import io.micronaut.data.model.runtime.PagedQuery;
import io.micronaut.data.model.runtime.PreparedQuery;
import io.micronaut.data.model.runtime.RuntimeEntityRegistry;
import io.micronaut.data.model.runtime.RuntimePersistentEntity;
import io.micronaut.data.model.runtime.RuntimePersistentProperty;
import io.micronaut.data.model.runtime.StoredQuery;
import io.micronaut.data.model.runtime.UpdateBatchOperation;
import io.micronaut.data.model.runtime.UpdateOperation;
import io.micronaut.data.model.vector.search.Score;
import io.micronaut.data.model.vector.search.SearchResult;
import io.micronaut.data.model.vector.search.SearchResults;
import io.micronaut.data.runtime.convert.DataConversionService;
import io.micronaut.data.runtime.date.DateTimeProvider;
import io.micronaut.data.runtime.event.DefaultEntityEventContext;
import io.micronaut.data.runtime.operations.internal.AbstractRepositoryOperations;
import io.micronaut.data.runtime.operations.internal.query.BindableParametersStoredQuery;
import io.micronaut.data.runtime.operations.internal.query.DefaultBindableParametersStoredQuery;
import io.micronaut.data.runtime.query.MethodContextAwareStoredQueryDecorator;
import io.micronaut.data.runtime.query.internal.DefaultPreparedQuery;
import io.micronaut.data.search.document.conf.DocumentSearchConfiguration;
import io.micronaut.data.search.document.query.DocumentSearchQueryBuilder;
import io.micronaut.data.search.document.query.SearchJson;
import io.micronaut.json.JsonMapper;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * Repository operations over a {@link SearchTransport}. Entities are converted to and from documents
 * with the Micronaut {@link JsonMapper}, so Serde annotations control the document shape.
 *
 * @since 5.4.0
 */
@Internal
@EachBean(SearchTransport.class)
public final class DefaultDocumentSearchRepositoryOperations extends AbstractRepositoryOperations
    implements DocumentSearchRepositoryOperations, MethodContextAwareStoredQueryDecorator {

    private static final String QUERY = "query";
    private static final Argument<Map<String, Object>> DOCUMENT = Argument.mapOf(String.class, Object.class);

    private final SearchTransport transport;
    private final JsonMapper jsonMapper;
    private final DocumentSearchConfiguration configuration;

    /**
     * @param name The transport bean name
     * @param transport The transport
     * @param jsonMapper The JSON mapper
     * @param configuration The configuration
     * @param dateTimeProvider The date time provider
     * @param runtimeEntityRegistry The entity registry
     * @param conversionService The conversion service
     * @param attributeConverterRegistry The attribute converter registry
     */
    DefaultDocumentSearchRepositoryOperations(@Parameter @Nullable String name,
                                              @Parameter SearchTransport transport,
                                              JsonMapper jsonMapper,
                                              DocumentSearchConfiguration configuration,
                                              DateTimeProvider<Object> dateTimeProvider,
                                              RuntimeEntityRegistry runtimeEntityRegistry,
                                              DataConversionService conversionService,
                                              AttributeConverterRegistry attributeConverterRegistry) {
        super(dateTimeProvider, runtimeEntityRegistry, conversionService, attributeConverterRegistry);
        this.transport = transport;
        this.jsonMapper = jsonMapper;
        this.configuration = configuration;
    }

    @Override
    public SearchTransport getTransport() {
        return transport;
    }

    @Override
    public String getIndexName(Class<?> entityType) {
        return indexName(runtimeEntityRegistry.getEntity(entityType));
    }

    private String indexName(RuntimePersistentEntity<?> entity) {
        String prefix = configuration.getIndexPrefix();
        return prefix == null ? entity.getPersistedName() : prefix + entity.getPersistedName();
    }

    @Override
    public <E, R> StoredQuery<E, R> decorate(MethodInvocationContext<?, ?> context, StoredQuery<E, R> storedQuery) {
        RuntimePersistentEntity<E> entity = runtimeEntityRegistry.getEntity(storedQuery.getRootEntity());
        return new DocumentStoredQuery<>(storedQuery, entity, conversionService, jsonMapper);
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
            applyPageable(body, preparedQuery.getPageable(), entity);
            SearchTransport.SearchHits hits = transport.search(indexName(entity), SearchJson.write(body));
            List<SearchResult<T>> results = new ArrayList<>(hits.hits().size());
            for (SearchTransport.SearchHit hit : hits.hits()) {
                results.add(new SearchResult<>(toEntity(hit, entity, preparedQuery), new Score(hit.score() == null ? 0d : hit.score()), hit.highlights()));
            }
            return (R) SearchResults.of(results, hits.total());
        }
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
        applyPageable(body, query.getPageable(), entity);
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
        applyPageable(body, preparedQuery.getPageable(), entity);
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
        Map<String, Object> body;
        PreparedQuery<R, R> preparedQuery = null;
        if (query instanceof PreparedQuery<?, ?> pq) {
            preparedQuery = (PreparedQuery<R, R>) pq;
            body = body(preparedQuery, storedQuery(preparedQuery));
        } else {
            body = matchAll();
        }
        applyPageable(body, query.getPageable(), entity);
        body.put("track_total_hits", true);
        SearchTransport.SearchHits hits = transport.search(indexName(entity), SearchJson.write(body));
        List<R> content = new ArrayList<>(hits.hits().size());
        for (SearchTransport.SearchHit hit : hits.hits()) {
            content.add(toEntity(hit, entity, preparedQuery));
        }
        return Page.of(content, query.getPageable(), hits.total());
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
        for (T value : operation) {
            persisted.add(persistOne(value, entity));
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
        DocumentStoredQuery<T, Number> storedQuery = storedQuery(preparedQuery);
        if (storedQuery.getOperationType() != StoredQuery.OperationType.DELETE) {
            throw new DataAccessException("Update queries are not supported by document search stores");
        }
        long deleted = transport.deleteByQuery(indexName(storedQuery.getPersistentEntity()), queryOnly(body(preparedQuery, storedQuery)), configuration.getRefresh());
        return Optional.of(deleted);
    }

    @Override
    @Nullable
    public <T> T findOne(Class<T> type, Object id) {
        RuntimePersistentEntity<T> entity = runtimeEntityRegistry.getEntity(type);
        Map<String, Object> source = transport.get(indexName(entity), idString(id));
        if (source == null) {
            return null;
        }
        return toEntity(new SearchTransport.SearchHit(idString(id), null, source), entity, null);
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
        EntityEventContext<T> event = new DefaultEntityEventContext<>(entity, value);
        if (!entityEventRegistry.prePersist((EntityEventContext<Object>) event)) {
            return value;
        }
        T current = event.getEntity();
        RuntimePersistentProperty<T> identity = requireIdentity(entity);
        Object id = identity.getProperty().get(current);
        if (id == null) {
            if (identity.getType() == UUID.class) {
                id = UUID.randomUUID();
                current = setId(identity, current, id);
            } else if (identity.getType() != String.class) {
                throw new DataAccessException("Document search stores generate String ids only; assign the id of " + entity.getName() + " or use a String or UUID id");
            }
        }
        String documentId = transport.index(indexName(entity), id == null ? null : idString(id), toDocument(current, entity), configuration.getRefresh());
        if (id == null) {
            current = setId(identity, current, documentId);
        }
        EntityEventContext<T> post = new DefaultEntityEventContext<>(entity, current);
        entityEventRegistry.postPersist((EntityEventContext<Object>) post);
        return post.getEntity();
    }

    private <T> T updateOne(T value, RuntimePersistentEntity<T> entity) {
        EntityEventContext<T> event = new DefaultEntityEventContext<>(entity, value);
        if (!entityEventRegistry.preUpdate((EntityEventContext<Object>) event)) {
            return value;
        }
        T current = event.getEntity();
        Object id = requireIdentity(entity).getProperty().get(current);
        if (id == null) {
            throw new DataAccessException("Cannot update an entity of " + entity.getName() + " without an id");
        }
        transport.index(indexName(entity), idString(id), toDocument(current, entity), configuration.getRefresh());
        EntityEventContext<T> post = new DefaultEntityEventContext<>(entity, current);
        entityEventRegistry.postUpdate((EntityEventContext<Object>) post);
        return post.getEntity();
    }

    private <T> boolean deleteOne(T value, RuntimePersistentEntity<T> entity) {
        EntityEventContext<T> event = new DefaultEntityEventContext<>(entity, value);
        if (!entityEventRegistry.preRemove((EntityEventContext<Object>) event)) {
            return false;
        }
        Object id = requireIdentity(entity).getProperty().get(event.getEntity());
        if (id == null) {
            return false;
        }
        boolean deleted = transport.delete(indexName(entity), idString(id), configuration.getRefresh());
        entityEventRegistry.postRemove((EntityEventContext<Object>) event);
        return deleted;
    }

    private <T, R> List<R> search(Map<String, Object> body, RuntimePersistentEntity<T> entity, @Nullable PreparedQuery<T, R> preparedQuery) {
        SearchTransport.SearchHits hits = transport.search(indexName(entity), SearchJson.write(body));
        List<R> results = new ArrayList<>(hits.hits().size());
        for (SearchTransport.SearchHit hit : hits.hits()) {
            results.add(toEntity(hit, entity, preparedQuery));
        }
        return results;
    }

    private <T, R> R toEntity(SearchTransport.SearchHit hit, RuntimePersistentEntity<T> entity, @Nullable PreparedQuery<T, ?> preparedQuery) {
        Map<String, Object> source = new LinkedHashMap<>(hit.source());
        RuntimePersistentProperty<T> identity = requireIdentity(entity);
        source.putIfAbsent(identity.getName(), hit.id());
        T value = fromDocument(source, entity);
        if (preparedQuery != null) {
            Class<?> resultType = preparedQuery.getResultType();
            if (resultType != SearchResults.class && !resultType.isAssignableFrom(entity.getIntrospection().getBeanType())) {
                try {
                    return (R) Objects.requireNonNull(jsonMapper.readValue(jsonMapper.writeValueAsBytes(source), Argument.of(resultType)));
                } catch (IOException e) {
                    throw new DataAccessException("Cannot map search hit to " + resultType.getName() + ": " + e.getMessage(), e);
                }
            }
            value = triggerPostLoad(value, entity, preparedQuery.getAnnotationMetadata());
        } else {
            value = triggerPostLoad(value, entity, io.micronaut.core.annotation.AnnotationMetadata.EMPTY_METADATA);
        }
        return (R) value;
    }

    private <T> Map<String, Object> toDocument(T value, RuntimePersistentEntity<T> entity) {
        try {
            return Objects.requireNonNull(jsonMapper.readValue(jsonMapper.writeValueAsBytes(value), DOCUMENT));
        } catch (IOException e) {
            throw new DataAccessException("Cannot serialize entity " + entity.getName() + ": " + e.getMessage(), e);
        }
    }

    private <T> T fromDocument(Map<String, Object> source, RuntimePersistentEntity<T> entity) {
        try {
            return Objects.requireNonNull(jsonMapper.readValue(jsonMapper.writeValueAsBytes(source), Argument.of(entity.getIntrospection().getBeanType())));
        } catch (IOException e) {
            throw new DataAccessException("Cannot deserialize entity " + entity.getName() + ": " + e.getMessage(), e);
        }
    }

    private <T> RuntimePersistentProperty<T> requireIdentity(RuntimePersistentEntity<T> entity) {
        RuntimePersistentProperty<T> identity = entity.getIdentity();
        if (identity == null) {
            throw new DataAccessException("Entity " + entity.getName() + " has no @Id, which document search stores require");
        }
        return identity;
    }

    private <T> T setId(RuntimePersistentProperty<T> identity, T entity, Object id) {
        BeanProperty<T, Object> property = identity.getProperty();
        Object converted = property.getType().isInstance(id) ? id : conversionService.convertRequired(id, property.getType());
        if (property.isReadOnly()) {
            return property.withValue(entity, converted);
        }
        property.set(entity, converted);
        return entity;
    }

    private String idString(Object id) {
        return conversionService.convertRequired(id, String.class);
    }

    private <T, R> DocumentStoredQuery<T, R> storedQuery(PreparedQuery<T, R> preparedQuery) {
        StoredQuery<T, R> storedQuery = preparedQuery instanceof DefaultPreparedQuery<T, R> defaultPreparedQuery
            ? defaultPreparedQuery.getStoredQueryDelegate()
            : null;
        if (storedQuery instanceof DocumentStoredQuery<T, R> documentStoredQuery) {
            return documentStoredQuery;
        }
        throw new IllegalStateException("Expected a document search query, got: " + preparedQuery);
    }

    private <T, R> Map<String, Object> body(PreparedQuery<T, R> preparedQuery, DocumentStoredQuery<T, R> storedQuery) {
        MethodInvocationContext<?, ?> context = preparedQuery instanceof DefaultPreparedQuery<?, ?> defaultPreparedQuery ? defaultPreparedQuery.getContext() : null;
        return storedQuery.bodyFor(context, null, new ValueBinder(storedQuery));
    }

    private static Map<String, Object> matchAll() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put(QUERY, Map.of("match_all", Map.of()));
        return body;
    }

    private static String queryOnly(Map<String, Object> body) {
        return SearchJson.write(Map.of(QUERY, body.getOrDefault(QUERY, Map.of("match_all", Map.of()))));
    }

    private <T> void applyPageable(Map<String, Object> body, Pageable pageable, RuntimePersistentEntity<T> entity) {
        if (pageable.getMode() != Pageable.Mode.OFFSET) {
            throw new DataAccessException("Cursored pagination is not supported by document search stores yet");
        }
        if (!pageable.isUnpaged()) {
            body.put("from", pageable.getOffset());
            body.put("size", pageable.getSize());
        }
        Sort sort = pageable.getSort();
        if (sort.isSorted()) {
            List<Map<String, Object>> entries = new ArrayList<>();
            for (Sort.Order order : sort.getOrderBy()) {
                String field;
                if (DocumentSearchQueryBuilder.SCORE_FIELD.equals(order.getProperty())) {
                    field = DocumentSearchQueryBuilder.SCORE_FIELD;
                } else {
                    io.micronaut.data.model.PersistentPropertyPath path = entity.getPropertyPath(order.getProperty());
                    if (path == null) {
                        throw new DataAccessException("Cannot sort on unknown property: " + order.getProperty());
                    }
                    field = DocumentSearchQueryBuilder.keywordPath(path);
                }
                entries.add(Map.of(field, Map.of("order", order.isAscending() ? "asc" : "desc")));
            }
            body.put("sort", entries);
        }
    }

    /**
     * Converts bound values with the attribute converters and the conversion service.
     */
    private final class ValueBinder implements BindableParametersStoredQuery.Binder {

        ValueBinder(DefaultBindableParametersStoredQuery<?, ?> storedQuery) {
            // the stored query collects the values; this binder only converts them
        }

        @Override
        public Object autoPopulateRuntimeProperty(RuntimePersistentProperty<?> persistentProperty, @Nullable Object previousValue) {
            return runtimeEntityRegistry.autoPopulateRuntimeProperty(persistentProperty, previousValue);
        }

        @Override
        @Nullable
        public Object convert(@Nullable Object value, @Nullable RuntimePersistentProperty<?> property) {
            if (property != null && property.getConverter() != null) {
                return ((io.micronaut.data.model.runtime.convert.AttributeConverter<Object, Object>) property.getConverter()).convertToPersistedValue(value, createTypeConversionContext(property, property.getArgument()));
            }
            return value;
        }

        @Override
        @Nullable
        public Object convert(@Nullable Class<?> converterClass, @Nullable Object value, @Nullable Argument<?> argument) {
            if (converterClass == null) {
                return value;
            }
            return attributeConverterRegistry.getConverter(converterClass).convertToPersistedValue(value, createTypeConversionContext(null, argument));
        }

        private io.micronaut.core.convert.ConversionContext createTypeConversionContext(@Nullable RuntimePersistentProperty<?> property, @Nullable Argument<?> argument) {
            if (argument != null) {
                return io.micronaut.core.convert.ConversionContext.of(argument);
            }
            if (property != null) {
                return io.micronaut.core.convert.ConversionContext.of(property.getArgument());
            }
            return io.micronaut.core.convert.ConversionContext.DEFAULT;
        }

        @Override
        public void bindOne(io.micronaut.data.model.runtime.QueryParameterBinding binding, @Nullable Object value) {
            // values are collected by the stored query
        }

        @Override
        public void bindMany(io.micronaut.data.model.runtime.QueryParameterBinding binding, java.util.Collection<Object> values) {
            // values are collected by the stored query
        }
    }
}
