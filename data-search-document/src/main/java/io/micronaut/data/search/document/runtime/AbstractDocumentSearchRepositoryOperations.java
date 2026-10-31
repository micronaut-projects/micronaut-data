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
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.beans.BeanProperty;
import io.micronaut.core.type.Argument;
import io.micronaut.data.event.EntityEventContext;
import io.micronaut.data.exceptions.DataAccessException;
import io.micronaut.data.model.Pageable;
import io.micronaut.data.model.Sort;
import io.micronaut.data.model.runtime.AttributeConverterRegistry;
import io.micronaut.data.model.runtime.PreparedQuery;
import io.micronaut.data.model.runtime.RuntimeEntityRegistry;
import io.micronaut.data.model.runtime.RuntimePersistentEntity;
import io.micronaut.data.model.runtime.RuntimePersistentProperty;
import io.micronaut.data.model.runtime.StoredQuery;
import io.micronaut.data.model.vector.search.Score;
import io.micronaut.data.model.vector.search.SearchResult;
import io.micronaut.data.model.vector.search.SearchResults;
import io.micronaut.data.runtime.convert.DataConversionService;
import io.micronaut.data.runtime.date.DateTimeProvider;
import io.micronaut.data.runtime.event.DefaultEntityEventContext;
import io.micronaut.data.runtime.operations.internal.AbstractRepositoryOperations;
import io.micronaut.data.runtime.operations.internal.query.BindableParametersStoredQuery;
import io.micronaut.data.runtime.operations.internal.query.DefaultBindableParametersStoredQuery;
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
import java.util.UUID;

/**
 * The request building, document mapping and entity events shared by the blocking and the
 * reactive document search operations.
 *
 * @since 5.4.0
 */
@Internal
abstract class AbstractDocumentSearchRepositoryOperations extends AbstractRepositoryOperations {

    protected static final String QUERY = "query";
    private static final Argument<Map<String, Object>> DOCUMENT = Argument.mapOf(String.class, Object.class);

    protected final JsonMapper jsonMapper;
    protected final DocumentSearchConfiguration configuration;

    AbstractDocumentSearchRepositoryOperations(JsonMapper jsonMapper,
                                               DocumentSearchConfiguration configuration,
                                               DateTimeProvider<Object> dateTimeProvider,
                                               RuntimeEntityRegistry runtimeEntityRegistry,
                                               DataConversionService conversionService,
                                               AttributeConverterRegistry attributeConverterRegistry) {
        super(dateTimeProvider, runtimeEntityRegistry, conversionService, attributeConverterRegistry);
        this.jsonMapper = jsonMapper;
        this.configuration = configuration;
    }

    protected final String indexName(RuntimePersistentEntity<?> entity) {
        String prefix = configuration.getIndexPrefix();
        return SearchIndexNames.validate(prefix == null ? entity.getPersistedName() : prefix + entity.getPersistedName());
    }

    // ---------- entity events around writes

    /**
     * Fires the pre-persist event and assigns a client-side id when the entity needs one.
     *
     * @return The entity to index, or {@code null} when a listener vetoed the persist
     */
    @Nullable
    protected final <T> T prePersist(T value, RuntimePersistentEntity<T> entity) {
        EntityEventContext<T> event = new DefaultEntityEventContext<>(entity, value);
        if (!entityEventRegistry.prePersist((EntityEventContext<Object>) event)) {
            return null;
        }
        T current = event.getEntity();
        RuntimePersistentProperty<T> identity = requireIdentity(entity);
        Object id = identity.getProperty().get(current);
        if (id == null) {
            if (identity.getType() == UUID.class) {
                current = setId(identity, current, UUID.randomUUID());
            } else if (identity.getType() != String.class) {
                throw new DataAccessException("Document search stores generate String ids only; assign the id of " + entity.getName() + " or use a String or UUID id");
            }
        }
        return current;
    }

    /**
     * @return The id to index the entity under, or {@code null} to let the store assign one
     */
    @Nullable
    protected final <T> String documentId(T current, RuntimePersistentEntity<T> entity) {
        Object id = requireIdentity(entity).getProperty().get(current);
        return id == null ? null : idString(id);
    }

    /**
     * Stores a store-assigned id and fires the post-persist event.
     */
    protected final <T> T postPersist(T current, RuntimePersistentEntity<T> entity, String documentId) {
        RuntimePersistentProperty<T> identity = requireIdentity(entity);
        if (identity.getProperty().get(current) == null) {
            current = setId(identity, current, documentId);
        }
        EntityEventContext<T> post = new DefaultEntityEventContext<>(entity, current);
        entityEventRegistry.postPersist((EntityEventContext<Object>) post);
        return post.getEntity();
    }

    /**
     * Fires the pre-update event.
     *
     * @return The entity to index, or {@code null} when a listener vetoed the update
     */
    @Nullable
    protected final <T> T preUpdate(T value, RuntimePersistentEntity<T> entity) {
        EntityEventContext<T> event = new DefaultEntityEventContext<>(entity, value);
        if (!entityEventRegistry.preUpdate((EntityEventContext<Object>) event)) {
            return null;
        }
        T current = event.getEntity();
        if (requireIdentity(entity).getProperty().get(current) == null) {
            throw new DataAccessException("Cannot update an entity of " + entity.getName() + " without an id");
        }
        return current;
    }

    protected final <T> T postUpdate(T current, RuntimePersistentEntity<T> entity) {
        EntityEventContext<T> post = new DefaultEntityEventContext<>(entity, current);
        entityEventRegistry.postUpdate((EntityEventContext<Object>) post);
        return post.getEntity();
    }

    /**
     * Fires the pre-remove event.
     *
     * @return The id of the document to delete, or {@code null} when a listener vetoed the removal or the entity has no id
     */
    @Nullable
    protected final <T> String preRemove(T value, RuntimePersistentEntity<T> entity) {
        EntityEventContext<T> event = new DefaultEntityEventContext<>(entity, value);
        if (!entityEventRegistry.preRemove((EntityEventContext<Object>) event)) {
            return null;
        }
        Object id = requireIdentity(entity).getProperty().get(event.getEntity());
        return id == null ? null : idString(id);
    }

    protected final <T> void postRemove(T value, RuntimePersistentEntity<T> entity) {
        entityEventRegistry.postRemove((EntityEventContext<Object>) new DefaultEntityEventContext<>(entity, value));
    }

    // ---------- documents

    protected final <T, R> List<R> toEntities(SearchTransport.SearchHits hits, RuntimePersistentEntity<T> entity, @Nullable PreparedQuery<T, R> preparedQuery) {
        List<R> results = new ArrayList<>(hits.hits().size());
        for (SearchTransport.SearchHit hit : hits.hits()) {
            results.add(toEntity(hit, entity, preparedQuery));
        }
        return results;
    }

    protected final <T> SearchResults<T> toSearchResults(SearchTransport.SearchHits hits, RuntimePersistentEntity<T> entity, PreparedQuery<T, ?> preparedQuery) {
        List<SearchResult<T>> results = new ArrayList<>(hits.hits().size());
        for (SearchTransport.SearchHit hit : hits.hits()) {
            results.add(new SearchResult<>(toEntity(hit, entity, preparedQuery), new Score(hit.score() == null ? 0d : hit.score()), hit.highlights()));
        }
        return SearchResults.of(results, hits.total());
    }

    protected final <T, R> R toEntity(SearchTransport.SearchHit hit, RuntimePersistentEntity<T> entity, @Nullable PreparedQuery<T, ?> preparedQuery) {
        Map<String, Object> source = new LinkedHashMap<>(hit.source());
        RuntimePersistentProperty<T> identity = requireIdentity(entity);
        source.putIfAbsent(identity.getName(), hit.id());
        T value = fromDocument(source, entity);
        if (preparedQuery != null) {
            Class<?> resultType = preparedQuery.getResultType();
            // a SearchResults query wraps entities; any other foreign result type is a DTO projection
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

    protected final <T> Map<String, Object> toDocument(T value, RuntimePersistentEntity<T> entity) {
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

    protected final <T> RuntimePersistentProperty<T> requireIdentity(RuntimePersistentEntity<T> entity) {
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

    protected final String idString(Object id) {
        return conversionService.convertRequired(id, String.class);
    }

    // ---------- requests

    protected final <T, R> DocumentStoredQuery<T, R> storedQuery(PreparedQuery<T, R> preparedQuery) {
        StoredQuery<T, R> storedQuery = preparedQuery instanceof DefaultPreparedQuery<T, R> defaultPreparedQuery
            ? defaultPreparedQuery.getStoredQueryDelegate()
            : null;
        if (storedQuery instanceof DocumentStoredQuery<T, R> documentStoredQuery) {
            return documentStoredQuery;
        }
        throw new IllegalStateException("Expected a document search query, got: " + preparedQuery);
    }

    protected final <T, R> Map<String, Object> body(PreparedQuery<T, R> preparedQuery, DocumentStoredQuery<T, R> storedQuery) {
        MethodInvocationContext<?, ?> context = preparedQuery instanceof DefaultPreparedQuery<?, ?> defaultPreparedQuery ? defaultPreparedQuery.getContext() : null;
        return storedQuery.bodyFor(context, null, new ValueBinder(storedQuery));
    }

    /**
     * The body of a paged query: its predicate, or everything for a plain paged query, with the pageable applied.
     */
    protected final <R> Map<String, Object> pagedBody(io.micronaut.data.model.runtime.PagedQuery<R> query, RuntimePersistentEntity<R> entity) {
        Map<String, Object> body;
        if (query instanceof PreparedQuery<?, ?> pq) {
            PreparedQuery<R, R> preparedQuery = (PreparedQuery<R, R>) pq;
            body = body(preparedQuery, storedQuery(preparedQuery));
        } else {
            body = matchAll();
        }
        applyPageable(body, query.getPageable(), entity);
        body.put("track_total_hits", true);
        return body;
    }

    protected static Map<String, Object> matchAll() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put(QUERY, Map.of("match_all", Map.of()));
        return body;
    }

    protected static String queryOnly(Map<String, Object> body) {
        return SearchJson.write(Map.of(QUERY, body.getOrDefault(QUERY, Map.of("match_all", Map.of()))));
    }

    protected final <T> void applyPageable(Map<String, Object> body, Pageable pageable, RuntimePersistentEntity<T> entity) {
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
                entries.add(Map.of(sortField(order, entity), Map.of("order", order.isAscending() ? "asc" : "desc")));
            }
            body.put("sort", entries);
        }
    }

    /** The field a sort order applies to: the score, or the keyword path of an entity property. */
    private static String sortField(Sort.Order order, RuntimePersistentEntity<?> entity) {
        if (DocumentSearchQueryBuilder.SCORE_FIELD.equals(order.getProperty())) {
            return DocumentSearchQueryBuilder.SCORE_FIELD;
        }
        io.micronaut.data.model.PersistentPropertyPath path = entity.getPropertyPath(order.getProperty());
        if (path == null) {
            throw new DataAccessException("Cannot sort on unknown property: " + order.getProperty());
        }
        return DocumentSearchQueryBuilder.keywordPath(path);
    }

    /**
     * Whether the delete-by-query of a prepared query is allowed; document stores run no update queries.
     */
    protected final <T> DocumentStoredQuery<T, Number> deleteStoredQuery(PreparedQuery<T, Number> preparedQuery) {
        DocumentStoredQuery<T, Number> storedQuery = storedQuery(preparedQuery);
        if (storedQuery.getOperationType() != StoredQuery.OperationType.DELETE) {
            throw new DataAccessException("Update queries are not supported by document search stores");
        }
        return storedQuery;
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
