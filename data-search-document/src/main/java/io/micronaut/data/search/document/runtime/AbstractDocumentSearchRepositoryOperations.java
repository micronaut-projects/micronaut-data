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
import io.micronaut.core.beans.BeanIntrospection;
import io.micronaut.core.beans.BeanProperty;
import io.micronaut.core.reflect.ReflectionUtils;
import io.micronaut.core.type.Argument;
import io.micronaut.data.model.Embedded;
import io.micronaut.data.model.runtime.RuntimeAssociation;
import io.micronaut.data.annotation.search.Searchable;
import io.micronaut.data.event.EntityEventContext;
import io.micronaut.data.annotation.TypeRole;
import io.micronaut.data.exceptions.DataAccessException;
import io.micronaut.data.exceptions.NonUniqueResultException;
import io.micronaut.data.model.CursoredPage;
import io.micronaut.data.model.CursoredPageable;
import io.micronaut.data.model.Page;
import io.micronaut.data.model.Limit;
import io.micronaut.data.model.Pageable;
import io.micronaut.data.model.Sort;
import io.micronaut.data.model.runtime.AttributeConverterRegistry;
import io.micronaut.data.model.runtime.PreparedQuery;
import io.micronaut.data.model.runtime.RuntimeEntityRegistry;
import io.micronaut.data.model.runtime.RuntimePersistentEntity;
import io.micronaut.data.model.runtime.RuntimePersistentProperty;
import io.micronaut.data.model.runtime.StoredQuery;
import io.micronaut.data.model.vector.DoubleVector;
import io.micronaut.data.model.vector.FloatVector;
import io.micronaut.data.model.vector.Vector;
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
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * The request building, document mapping and entity events shared by the blocking and the
 * reactive document search operations.
 *
 * @since 5.3.0
 */
@Internal
abstract class AbstractDocumentSearchRepositoryOperations extends AbstractRepositoryOperations {

    protected static final String QUERY = "query";
    private static final String ORDER = "order";
    private static final String ASC = "asc";
    private static final String DESC = "desc";
    private static final String FROM = "from";
    private static final String SIZE = "size";
    private static final String SORT = "sort";
    private static final String KNN = "knn";
    private static final String NUM_CANDIDATES = "num_candidates";
    // the hits a request without a size returns, the default of Elasticsearch and OpenSearch
    private static final int DEFAULT_SIZE = 10;

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

    /**
     * The index of an entity: {@code @Searchable(name)} when set, else the persisted name, behind the
     * configured prefix.
     *
     * @param entity The entity
     * @return The index name
     */
    protected final String indexName(RuntimePersistentEntity<?> entity) {
        String name = entity.getAnnotationMetadata().stringValue(Searchable.class, "name")
            .filter(n -> !n.isEmpty())
            .orElseGet(entity::getPersistedName);
        String prefix = configuration.getIndexPrefix();
        return SearchIndexNames.validate(prefix == null ? name : prefix + name);
    }

    // ---------- entity events around writes

    /**
     * Fires the pre-persist event and assigns a client-side id when the entity has none: a random
     * {@code UUID}, or its string form for a {@code String} id. The id is generated here rather than by
     * the store so the document source holds it, which the cursored paging tiebreaker sorts on, and so
     * an externally versioned write, which the stores reject without an id, can be sent.
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
            } else if (identity.getType() == String.class) {
                current = setId(identity, current, UUID.randomUUID().toString());
            } else {
                throw new DataAccessException("Document search stores generate String and UUID ids only; assign the id of " + entity.getName() + " or use a String or UUID id");
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
     * The external version to index a new entity with: its {@code @Version} property, which the
     * version-generating entity listener has set to {@code 0} in the pre-persist event.
     *
     * @return The version, or {@code null} for an entity without a version property
     */
    @Nullable
    protected final <T> Long persistVersion(T current, RuntimePersistentEntity<T> entity) {
        return documentVersion(current, entity);
    }

    /**
     * The external version to index an updated entity with: its {@code @Version} property, which the
     * version-generating entity listener has incremented in the pre-update event, so the store rejects
     * the write when another writer got there first.
     *
     * @return The version, or {@code null} for an entity without a version property
     */
    @Nullable
    protected final <T> Long updateVersion(T current, RuntimePersistentEntity<T> entity) {
        return documentVersion(current, entity);
    }

    /**
     * Whether a save may replace a document with the same id: Jakarta Data's {@code @Save} inserts or
     * replaces, while an insert is a create-only write.
     *
     * @param annotationMetadata The metadata of the repository method
     * @return True to write without the create-only check
     */
    protected static boolean replacesExisting(io.micronaut.core.annotation.AnnotationMetadata annotationMetadata) {
        return annotationMetadata.hasAnnotation("jakarta.data.repository.Save");
    }

    /**
     * The version to delete an entity at: its {@code @Version} property as read, so the store rejects
     * the delete when the document has been updated since. It must be read before the pre-remove event,
     * whose version-generating listener increments the property of a mutable entity in place.
     *
     * @param current The entity
     * @param entity The persistent entity
     * @param <T> The entity type
     * @return The version, or {@code null} for an entity without a version property
     */
    @Nullable
    protected final <T> Long deleteVersion(T current, RuntimePersistentEntity<T> entity) {
        return documentVersion(current, entity);
    }

    @Nullable
    private <T> Long documentVersion(T current, RuntimePersistentEntity<T> entity) {
        if (!entity.hasVersion()) {
            return null;
        }
        Object value = entity.getVersion().getProperty().get(current);
        return value == null ? null : conversionService.convertRequired(value, Long.class);
    }

    /**
     * Writes a search body, requesting the document versions when the entity has a {@code @Version}.
     *
     * @param body The body
     * @param entity The entity
     * @return The JSON body
     */
    protected final String searchJson(Map<String, Object> body, RuntimePersistentEntity<?> entity) {
        if (entity.hasVersion()) {
            body.put("version", true);
        }
        return SearchJson.write(body);
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
     * Fires the pre-persist event of each entity of a batch and builds the documents to index.
     *
     * @param values The entities
     * @param entity The entity type
     * @param replace Whether a document without a version replaces one with the same id, as a Jakarta Data
     *                {@code @Save} does, rather than being a create-only write
     * @param <T> The entity type
     * @return The batch
     */
    protected final <T> PersistBatch<T> persistBatch(Iterable<T> values, RuntimePersistentEntity<T> entity, boolean replace) {
        PersistBatch<T> batch = new PersistBatch<>(entity);
        for (T value : values) {
            T current = prePersist(value, entity);
            if (current == null) {
                batch.results.add(value);
            } else {
                batch.positions.add(batch.results.size());
                batch.results.add(current);
                batch.toIndex.add(current);
                Long version = persistVersion(current, entity);
                batch.documents.add(new SearchTransport.BulkDocument(documentId(current, entity), toDocument(current, entity), version, !replace && version == null));
            }
        }
        return batch;
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

    protected final <T> SearchResults<T> toSearchResults(SearchTransport.SearchHits hits, RuntimePersistentEntity<T> entity, @Nullable PreparedQuery<T, ?> preparedQuery) {
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
        if (entity.hasVersion() && hit.version() != null) {
            // the store's version wins over the copy serialized in the source
            source.put(entity.getVersion().getName(), hit.version());
        }
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

    /**
     * The document of an entity: its persistent properties under their property names, which are the
     * names the index mapping and the queries use, each value in its JSON form. A property named
     * {@code isOdd} with an {@code isOdd()} getter is therefore stored as {@code isOdd}, not as the
     * {@code odd} a JSON mapper derives from the getter. Null values are left out and embedded
     * objects are written the same way.
     *
     * @param value The entity
     * @param entity The persistent entity
     * @param <T> The entity type
     * @return The document
     */
    protected final <T> Map<String, Object> toDocument(T value, RuntimePersistentEntity<T> entity) {
        try {
            return writeDocument(value, entity);
        } catch (IOException e) {
            throw new DataAccessException("Cannot serialize entity " + entity.getName() + ": " + e.getMessage(), e);
        }
    }

    private <T> Map<String, Object> writeDocument(T value, RuntimePersistentEntity<T> entity) throws IOException {
        Map<String, Object> document = new LinkedHashMap<>();
        for (RuntimePersistentProperty<T> property : documentProperties(entity)) {
            Object propertyValue = property.getProperty().get(value);
            if (propertyValue == null) {
                continue;
            }
            if (property instanceof Embedded) {
                RuntimePersistentEntity<Object> embedded = (RuntimePersistentEntity<Object>) ((RuntimeAssociation<T>) property).getAssociatedEntity();
                document.put(property.getName(), writeDocument(propertyValue, embedded));
            } else if (propertyValue instanceof String || propertyValue instanceof Number || propertyValue instanceof Boolean) {
                document.put(property.getName(), propertyValue);
            } else if (propertyValue instanceof Vector vector) {
                // a vector is a plain JSON array of numbers, whatever its runtime type
                document.put(property.getName(), SearchJson.arrayToList(vector.toDoubleArray()));
            } else {
                document.put(property.getName(), jsonMapper.readValue(jsonMapper.writeValueAsBytes(propertyValue), Argument.OBJECT_ARGUMENT));
            }
        }
        return document;
    }

    private <T> T fromDocument(Map<String, Object> source, RuntimePersistentEntity<T> entity) {
        try {
            return readDocument(source, entity);
        } catch (IOException | RuntimeException e) {
            throw new DataAccessException("Cannot deserialize entity " + entity.getName() + ": " + e.getMessage(), e);
        }
    }

    /**
     * Reads an entity from its document. The constructor arguments are passed to the constructor and the other
     * properties set afterwards, so a property the document leaves out, because it was {@code null} when written,
     * keeps its default, also in a package whose types are non-null by default.
     */
    private <T> T readDocument(Map<String, Object> source, RuntimePersistentEntity<T> entity) throws IOException {
        Map<String, Object> values = new LinkedHashMap<>();
        for (RuntimePersistentProperty<T> property : documentProperties(entity)) {
            Object raw = source.get(property.getName());
            if (raw != null) {
                values.put(property.getName(), readValue(raw, property));
            }
        }
        BeanIntrospection<T> introspection = entity.getIntrospection();
        Argument<?>[] constructorArguments = introspection.getConstructorArguments();
        Object[] arguments = new Object[constructorArguments.length];
        for (int i = 0; i < constructorArguments.length; i++) {
            Argument<?> argument = constructorArguments[i];
            Object value = values.remove(argument.getName());
            arguments[i] = value == null && argument.isPrimitive() ? primitiveDefault(argument.getType()) : value;
        }
        T instance = introspection.instantiate(arguments);
        for (Map.Entry<String, Object> value : values.entrySet()) {
            BeanProperty<T, Object> property = introspection.getRequiredProperty(value.getKey(), Object.class);
            if (property.isReadOnly()) {
                instance = property.withValue(instance, value.getValue());
            } else {
                property.set(instance, value.getValue());
            }
        }
        return instance;
    }

    private <T> @Nullable Object readValue(Object raw, RuntimePersistentProperty<T> property) throws IOException {
        if (property instanceof Embedded && raw instanceof Map<?, ?> nested) {
            RuntimePersistentEntity<Object> embedded = (RuntimePersistentEntity<Object>) ((RuntimeAssociation<T>) property).getAssociatedEntity();
            return readDocument((Map<String, Object>) nested, embedded);
        }
        if (ReflectionUtils.getWrapperType(property.getType()).isInstance(raw) && !(raw instanceof Map) && !(raw instanceof Iterable)) {
            return raw;
        }
        if (Vector.class.isAssignableFrom(property.getType()) && raw instanceof Collection<?> numbers) {
            return vectorOf((Collection<? extends Number>) numbers, property.getType());
        }
        return jsonMapper.readValue(jsonMapper.writeValueAsBytes(raw), property.getArgument());
    }

    /** The value a primitive constructor argument gets when the document has none, as Java initialises a field. */
    private Object primitiveDefault(Class<?> type) {
        if (type == boolean.class) {
            return false;
        }
        if (type == char.class) {
            return '\0';
        }
        return conversionService.convertRequired(0, type);
    }

    /** A stored JSON array of numbers as the vector type of the property. */
    private static Vector vectorOf(Collection<? extends Number> numbers, Class<?> type) {
        Vector vector = Vector.of(numbers);
        if (type.isInstance(vector)) {
            return vector;
        }
        if (type == FloatVector.class) {
            return Vector.of(vector.toFloatArray());
        }
        if (type == DoubleVector.class) {
            return Vector.of(vector.toDoubleArray());
        }
        return vector;
    }

    /**
     * The properties a document holds: the identity, the version and every other persistent property.
     */
    private static <T> Collection<RuntimePersistentProperty<T>> documentProperties(RuntimePersistentEntity<T> entity) {
        Map<String, RuntimePersistentProperty<T>> properties = new LinkedHashMap<>();
        if (entity.hasIdentity()) {
            properties.put(entity.getIdentity().getName(), entity.getIdentity());
        }
        if (entity.hasVersion()) {
            properties.put(entity.getVersion().getName(), entity.getVersion());
        }
        for (RuntimePersistentProperty<T> property : entity.getPersistentProperties()) {
            properties.putIfAbsent(property.getName(), property);
        }
        return properties.values();
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
        applyPageable(body, query, entity);
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

    /**
     * Applies the window and the sort of a query: its pageable, or else its limit, such as a Jakarta Data
     * {@code Limit} parameter, with its sort.
     */
    protected final <T> void applyPageable(Map<String, Object> body, io.micronaut.data.model.runtime.PagedQuery<?> query, RuntimePersistentEntity<T> entity) {
        Pageable pageable = query.getPageable();
        if (query instanceof PreparedQuery<?, ?> preparedQuery && pageable.isUnpaged()) {
            Limit limit = preparedQuery.getQueryLimit();
            if (!limit.isLimited()) {
                limit = preparedQuery.getParameterInRole(TypeRole.LIMIT, Limit.class).orElse(limit);
            }
            if (limit.isLimited()) {
                if (limit.offset() > 0) {
                    body.put(FROM, limit.offset());
                }
                body.put(SIZE, limit.maxResults());
            }
            applyPageable(body, Pageable.UNPAGED.withSort(preparedQuery.getSort()), entity);
            return;
        }
        applyPageable(body, pageable, entity);
    }

    /**
     * Windows a single result query. Its pageable and sort decide which hit comes first, a query that declares
     * its own size, as findFirst does, keeps it, and otherwise a second hit is fetched when unique results are checked.
     *
     * @param body The search body
     * @param query The query
     * @param entity The entity
     * @param <T> The entity type
     * @return Whether the results must be checked for a second hit
     */
    protected final <T> boolean applySingleResult(Map<String, Object> body, PreparedQuery<?, ?> query, RuntimePersistentEntity<T> entity) {
        Object declaredSize = body.get(SIZE);
        boolean checkUnique = configuration.isUniqueResultOnFindOne() && declaredSize == null;
        Object size = checkUnique ? 2 : declaredSize == null ? 1 : declaredSize;
        // set before the pageable, so an unpaged vector search sizes its knn clause for this window
        body.put(SIZE, size);
        applyPageable(body, query, entity);
        body.put(SIZE, size);
        return checkUnique;
    }

    /**
     * The first result of a single result query.
     *
     * @param results The results
     * @param checkUnique Whether a second result is an error
     * @param query The query
     * @param <R> The result type
     * @return The first result or null
     */
    protected static <R> @Nullable R singleResult(List<R> results, boolean checkUnique, PreparedQuery<?, ?> query) {
        if (checkUnique && results.size() > 1) {
            throw new NonUniqueResultException("Multiple results found for query: " + query.getQuery());
        }
        return results.isEmpty() ? null : results.get(0);
    }

    /**
     * Adds the sort of a pageable after the query's own {@code OrderBy} or the {@code sort} of a native body,
     * which take precedence. A declared entry may be an object or a field name, the string form both stores accept.
     */
    private <T> void appendSort(Map<String, Object> body, Sort sort, RuntimePersistentEntity<T> entity) {
        if (!sort.isSorted()) {
            return;
        }
        List<Object> entries = new ArrayList<>(declaredSort(body));
        entries.addAll(sortEntries(sort.getOrderBy(), entity, false));
        body.put(SORT, entries);
    }

    /** The {@code sort} a body declares: a list of entries, or a single entry. */
    private static List<?> declaredSort(Map<String, Object> body) {
        Object declared = body.get(SORT);
        if (declared instanceof List<?> list) {
            return list;
        }
        return declared == null ? List.of() : List.of(declared);
    }

    protected final <T> void applyPageable(Map<String, Object> body, Pageable pageable, RuntimePersistentEntity<T> entity) {
        if (pageable instanceof CursoredPageable cursored) {
            if (containsKnn(body)) {
                // the k nearest neighbours are the documents search_after pages through, and the position of a
                // cursor in that window is unknown, so no k covers the next page
                throw new UnsupportedOperationException("Cursored paging is not supported for a vector search in a document search store, use a Pageable with an offset");
            }
            applyCursoredPageable(body, cursored, entity);
            return;
        }
        if (pageable.isUnpaged()) {
            // without a size the stores return only their default of 10 hits; a query's own limit stays
            body.putIfAbsent(SIZE, configuration.getMaxResults());
        } else {
            body.put(FROM, pageable.getOffset());
            body.put(SIZE, pageable.getSize());
        }
        // after the window is known, so an unlimited vector search follows the max-results ceiling
        sizeKnn(body, window(body));
        appendSort(body, pageable.getSort(), entity);
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

    /** The end of the window of hits a request returns: its {@code from} plus its {@code size}, or the stores' default size. */
    private static int window(Map<String, Object> body) {
        long from = body.get(FROM) instanceof Number number ? number.longValue() : 0;
        long size = body.get(SIZE) instanceof Number number ? number.longValue() : DEFAULT_SIZE;
        return (int) Math.min(Integer.MAX_VALUE, from + size);
    }

    /** Whether a request holds a {@code knn} query. */
    private static boolean containsKnn(Object node) {
        if (node instanceof Map<?, ?> map) {
            return map.containsKey(KNN) || map.values().stream().anyMatch(AbstractDocumentSearchRepositoryOperations::containsKnn);
        }
        return node instanceof List<?> list && list.stream().anyMatch(AbstractDocumentSearchRepositoryOperations::containsKnn);
    }

    /**
     * A {@code knn} clause compiled with the method's limit returns at most {@code k} hits, so a page beyond
     * that window would be empty: {@code k} and {@code num_candidates} grow to cover the requested window,
     * up to the {@value DocumentSearchQueryBuilder#MAX_KNN_CANDIDATES} the stores accept.
     */
    @SuppressWarnings("unchecked")
    private static void sizeKnn(Object node, int window) {
        int k = Math.min(window, DocumentSearchQueryBuilder.MAX_KNN_CANDIDATES);
        if (node instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (KNN.equals(entry.getKey()) && entry.getValue() instanceof Map<?, ?> knn && knn.get("k") instanceof Number current && current.intValue() < k) {
                    Map<String, Object> clause = (Map<String, Object>) knn;
                    int candidates = Math.max(k * 10, knn.get(NUM_CANDIDATES) instanceof Number c ? c.intValue() : 0);
                    clause.put("k", k);
                    clause.put(NUM_CANDIDATES, Math.min(candidates, DocumentSearchQueryBuilder.MAX_KNN_CANDIDATES));
                } else {
                    sizeKnn(entry.getValue(), window);
                }
            }
        } else if (node instanceof List<?> list) {
            for (Object item : list) {
                sizeKnn(item, window);
            }
        }
    }

    /**
     * Cursored pagination with {@code search_after}: the requested sort plus the entity id as a
     * tiebreaker, reversed for a backward page, and the cursor as the {@code search_after} values.
     */
    private <T> void applyCursoredPageable(Map<String, Object> body, CursoredPageable pageable, RuntimePersistentEntity<T> entity) {
        List<Sort.Order> orders = cursorOrders(pageable, entity);
        // the method's OrderBy comes first, then the pageable's sort and the id tiebreaker
        List<Map<String, Object>> entries = new ArrayList<>();
        for (Object entry : declaredSort(body)) {
            Map<String, Object> object = sortObject(entry);
            entries.add(pageable.isBackward() ? reversed(object) : object);
        }
        for (Map<String, Object> entry : sortEntries(orders, entity, pageable.isBackward())) {
            // a field the OrderBy already sorts on is one sort value, not two
            if (entries.stream().noneMatch(declared -> declared.keySet().equals(entry.keySet()))) {
                entries.add(entry);
            }
        }
        body.put(SORT, entries);
        if (pageable.getSize() > 0) {
            body.put(SIZE, pageable.getSize());
        }
        int sortValues = entries.size();
        pageable.cursor().ifPresent(cursor -> {
            if (cursor.size() != sortValues) {
                throw new DataAccessException("The cursor must hold one value per sort property, including the id tiebreaker; got " + cursor.size() + " for " + sortValues);
            }
            body.put("search_after", cursor.elements());
        });
    }

    /** A sort entry with its direction flipped, for a backward cursored page. */
    /**
     * A declared sort entry in the object form with an explicit order: a field name, {@code "title"}, or a field
     * with its order as a string, {@code {"title": "desc"}}, sorts in the stores' default order, descending for
     * the score and ascending otherwise.
     */
    private static Map<String, Object> sortObject(Object entry) {
        if (entry instanceof String field) {
            return Map.of(field, Map.of(ORDER, defaultOrder(field)));
        }
        if (!(entry instanceof Map<?, ?> map)) {
            throw new DataAccessException("A sort entry must be a field name or an object, got: " + entry);
        }
        Map<String, Object> object = new LinkedHashMap<>();
        map.forEach((field, options) -> {
            Map<String, Object> order = new LinkedHashMap<>();
            if (options instanceof Map<?, ?> declared) {
                declared.forEach((key, value) -> order.put(String.valueOf(key), value));
            } else if (options != null) {
                order.put(ORDER, options);
            }
            order.putIfAbsent(ORDER, defaultOrder(String.valueOf(field)));
            object.put(String.valueOf(field), order);
        });
        return object;
    }

    private static String defaultOrder(String field) {
        return DocumentSearchQueryBuilder.SCORE_FIELD.equals(field) ? DESC : ASC;
    }

    private static Map<String, Object> reversed(Map<String, Object> entry) {
        Map<String, Object> flipped = new LinkedHashMap<>();
        entry.forEach((field, options) -> {
            Map<String, Object> order = new LinkedHashMap<>((Map<String, Object>) options);
            order.put(ORDER, ASC.equals(order.get(ORDER)) ? DESC : ASC);
            flipped.put(field, order);
        });
        return flipped;
    }

    /**
     * The sort of a cursored page: the pageable's orders with the entity id appended as a tiebreaker.
     */
    protected final <T> List<Sort.Order> cursorOrders(CursoredPageable pageable, RuntimePersistentEntity<T> entity) {
        List<Sort.Order> orders = new ArrayList<>(pageable.getSort().getOrderBy());
        String idName = requireIdentity(entity).getName();
        if (orders.stream().noneMatch(order -> order.getProperty().equals(idName))) {
            orders.add(Sort.Order.asc(idName));
        }
        return orders;
    }

    private <T> List<Map<String, Object>> sortEntries(List<Sort.Order> orders, RuntimePersistentEntity<T> entity, boolean reversed) {
        List<Map<String, Object>> entries = new ArrayList<>();
        for (Sort.Order order : orders) {
            boolean ascending = order.isAscending() != reversed;
            entries.add(Map.of(sortField(order, entity), DocumentSearchQueryBuilder.sortOptions(ascending,
                order.getNullOrdering() == Sort.Order.NullOrdering.FIRST, order.getNullOrdering() == Sort.Order.NullOrdering.LAST)));
        }
        return entries;
    }

    /**
     * Builds the page of a paged query from the hits: a {@link CursoredPage} with one cursor per hit
     * for a {@link CursoredPageable}, a plain {@link Page} otherwise.
     */
    protected final <R> Page<R> toPage(SearchTransport.SearchHits hits,
                                       io.micronaut.data.model.runtime.PagedQuery<R> query,
                                       RuntimePersistentEntity<R> entity,
                                       @Nullable PreparedQuery<R, R> preparedQuery) {
        List<R> content = toEntities(hits, entity, preparedQuery);
        if (!(query.getPageable() instanceof CursoredPageable cursored)) {
            return Page.of(content, query.getPageable(), hits.total());
        }
        List<Pageable.Cursor> cursors = new ArrayList<>(hits.hits().size());
        for (SearchTransport.SearchHit hit : hits.hits()) {
            cursors.add(Pageable.Cursor.of(hit.sort()));
        }
        if (cursored.isBackward()) {
            content = new ArrayList<>(content);
            java.util.Collections.reverse(content);
            java.util.Collections.reverse(cursors);
        }
        return CursoredPage.of(content, cursored, cursors, cursored.requestTotal() ? hits.total() : null);
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

    /**
     * The entities of a batch insert after the pre-persist event, sent in bulk requests of at most
     * the configured bulk size. Each written entity gets its id and the post-persist event as soon as
     * its request returns, also when the store rejected other documents of the request.
     *
     * @param <T> The entity type
     */
    protected final class PersistBatch<T> {

        // the results keep the input order, with vetoed entities in place
        private final List<T> results = new ArrayList<>();
        private final List<T> toIndex = new ArrayList<>();
        // the position of each indexed entity in the results, as equal entities cannot be told apart by indexOf
        private final List<Integer> positions = new ArrayList<>();
        private final List<SearchTransport.BulkDocument> documents = new ArrayList<>();
        private final RuntimePersistentEntity<T> entity;

        private PersistBatch(RuntimePersistentEntity<T> entity) {
            this.entity = entity;
        }

        /**
         * @return The entities in input order, the persisted ones after their post-persist event
         */
        public List<T> results() {
            return results;
        }

        /**
         * @return The index the entities go to
         */
        public String index() {
            return indexName(entity);
        }

        /**
         * @return The documents to index, split into requests of at most the configured bulk size
         */
        public List<List<SearchTransport.BulkDocument>> requests() {
            int size = configuration.getBulkSize();
            List<List<SearchTransport.BulkDocument>> requests = new ArrayList<>((documents.size() + size - 1) / size);
            for (int from = 0; from < documents.size(); from += size) {
                requests.add(documents.subList(from, Math.min(from + size, documents.size())));
            }
            return requests;
        }

        /**
         * Stores the ids of the written documents of one request and fires their post-persist event.
         *
         * @param request The position of the request in {@link #requests()}
         * @param ids The id of each document of the request, {@code null} for one that was not written
         */
        public void written(int request, List<@Nullable String> ids) {
            int offset = request * configuration.getBulkSize();
            for (int i = 0; i < ids.size(); i++) {
                String id = ids.get(i);
                if (id != null) {
                    results.set(positions.get(offset + i), postPersist(toIndex.get(offset + i), entity, id));
                }
            }
        }
    }
}
