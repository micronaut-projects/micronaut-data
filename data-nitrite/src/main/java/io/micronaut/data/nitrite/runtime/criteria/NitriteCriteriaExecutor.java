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
package io.micronaut.data.nitrite.runtime.criteria;

import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.data.model.jpa.criteria.PersistentEntityCriteriaDelete;
import io.micronaut.data.model.jpa.criteria.PersistentEntityCriteriaUpdate;
import io.micronaut.data.model.jpa.criteria.PersistentEntityQuery;
import io.micronaut.data.model.jpa.criteria.impl.AbstractPersistentEntityCriteriaDelete;
import io.micronaut.data.model.jpa.criteria.impl.AbstractPersistentEntityCriteriaQuery;
import io.micronaut.data.model.jpa.criteria.impl.AbstractPersistentEntityCriteriaUpdate;
import io.micronaut.data.model.query.BindingParameter;
import io.micronaut.data.model.query.builder.QueryBuilder;
import io.micronaut.data.model.query.builder.QueryParameterBinding;
import io.micronaut.data.model.query.builder.QueryResult;
import io.micronaut.data.model.runtime.RuntimePersistentEntity;
import io.micronaut.data.nitrite.model.query.NitriteQueryOperators;
import io.micronaut.data.nitrite.model.query.builder.NitriteQueryBuilder;
import io.micronaut.data.nitrite.model.query.builder.NitriteRuntimeFilter;
import io.micronaut.data.nitrite.runtime.CollectionUpdateLock;
import io.micronaut.data.nitrite.runtime.ValueConverter;
import io.micronaut.data.nitrite.runtime.mapping.NitriteEntityMapper;
import io.micronaut.data.nitrite.runtime.query.NitriteFilterBuilder;
import io.micronaut.data.nitrite.runtime.query.NitriteFilterUtils;
import io.micronaut.data.nitrite.runtime.query.NitriteQueryParser;
import io.micronaut.data.nitrite.runtime.read.CollectionAggregator;
import io.micronaut.data.nitrite.runtime.read.CollectionProjectionMapper;
import jakarta.persistence.criteria.CriteriaDelete;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.CriteriaUpdate;
import org.dizitart.no2.collection.Document;
import org.dizitart.no2.collection.FindOptions;
import org.dizitart.no2.collection.NitriteCollection;
import org.dizitart.no2.common.SortOrder;
import org.dizitart.no2.filters.Filter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

/**
 * Helper class to execute Criteria queries for Nitrite.
 *
 * @since 5.2.0
 */
@Internal
public final class NitriteCriteriaExecutor {

    private final QueryBuilder queryBuilder;
    private final NitriteEntityMapper entityMapper;
    private final NitriteQueryParser queryParser;
    private final NitriteFilterBuilder filterBuilder;
    private final Function<Class<?>, NitriteCollection> collectionFactory;
    private final Function<Class<?>, RuntimePersistentEntity<?>> entityFactory;
    private final CollectionProjectionMapper projectionMapper;
    private final ConversionService conversionService;

    /**
     * Creates a new NitriteCriteriaExecutor.
     *
     * @param queryBuilder the query builder
     * @param entityMapper the entity mapper
     * @param queryParser the query parser
     * @param filterBuilder the filter builder
     * @param conversionService the conversion service
     * @param collectionFactory the collection factory function
     * @param entityFactory the entity factory function
     */
    public NitriteCriteriaExecutor(QueryBuilder queryBuilder,
                                   NitriteEntityMapper entityMapper,
                                   NitriteQueryParser queryParser,
                                   NitriteFilterBuilder filterBuilder,
                                   ConversionService conversionService,
                                   Function<Class<?>, NitriteCollection> collectionFactory,
                                   Function<Class<?>, RuntimePersistentEntity<?>> entityFactory) {
        this.queryBuilder = queryBuilder;
        this.entityMapper = entityMapper;
        this.queryParser = queryParser;
        this.filterBuilder = filterBuilder;
        this.collectionFactory = collectionFactory;
        this.entityFactory = entityFactory;
        this.conversionService = conversionService;
        this.projectionMapper = new CollectionProjectionMapper(new ValueConverter(conversionService), entityMapper);
    }

    /**
     * Checks if any entities match the given criteria query.
     *
     * @param query the criteria query
     * @return true if at least one entity matches
     */
    public boolean exists(@NonNull CriteriaQuery<?> query) {
        Class<?> type = getEntityType(query);
        NitriteRuntimeFilter runtimeFilter = tryBuildRuntimeFilter((AbstractPersistentEntityCriteriaQuery<?>) query);
        if (runtimeFilter != null) {
            Filter filter = buildFilterFromRuntimeFilter(runtimeFilter, type);
            return collectionFactory.apply(type).find(filter).iterator().hasNext();
        }
        QueryResult queryResult = Objects.requireNonNull(((AbstractPersistentEntityCriteriaQuery<?>) query)
                .build(AnnotationMetadata.EMPTY_METADATA, queryBuilder), "Failed to build query for criteria query");
        Filter filter = buildFilterFromQueryResult(queryResult, type);
        return collectionFactory.apply(type).find(filter).iterator().hasNext();
    }

    /**
     * Finds a single entity matching the criteria query.
     *
     * @param query the criteria query
     * @param <R> the result type
     * @return the first matching entity, or null if none found
     */
    public <R> @Nullable R findOne(@NonNull CriteriaQuery<R> query) {
        Class<?> entityType = getEntityType(query);
        RuntimePersistentEntity<?> persistentEntity = entityFactory.apply(entityType);
        Class<R> resultType = (Class<R>) ((PersistentEntityQuery<?>) query).getResultType();

        NitriteRuntimeFilter runtimeFilter = tryBuildRuntimeFilter((AbstractPersistentEntityCriteriaQuery<?>) query);
        if (runtimeFilter != null) {
            Filter filter = buildFilterFromRuntimeFilter(runtimeFilter, entityType);
            FindOptions options = buildFindOptionsFromRuntimeFilter(runtimeFilter, persistentEntity, -1, -1);
            if (Long.class.equals(resultType) || long.class.equals(resultType)) {
                return (R) Long.valueOf(collectionFactory.apply(entityType).find(filter, options).size());
            }
            Document doc = collectionFactory.apply(entityType).find(filter, options).firstOrNull();
            if (doc == null) {
                return null;
            }
            List<String> projectedFields = queryParser.extractProjectionFields(runtimeFilter.projection());
            if (!projectedFields.isEmpty()) {
                return projectionMapper.mapDocument(doc, projectedFields, persistentEntity, resultType, false);
            }
            return entityMapper.fromDocument(doc, resultType);
        }

        QueryResult queryResult = Objects.requireNonNull(((AbstractPersistentEntityCriteriaQuery<?>) query)
                .build(AnnotationMetadata.EMPTY_METADATA, queryBuilder), "Failed to build query for criteria query");
        Object parsedQuery = parseQueryForFilter(queryResult.getQuery());
        Filter filter = buildFilterFromQueryResult(queryResult, entityType, parsedQuery);
        FindOptions options = buildFindOptions(persistentEntity, -1, -1, parsedQuery);

        // A numeric result type alone does not imply a count: max/min/sum/avg also produce a
        // $group stage, so the selection decides which aggregate to execute.
        AggregateSelection aggregateSelection = extractAggregateSelection(parsedQuery);
        if (aggregateSelection != null) {
            Object aggregate = new CollectionAggregator().aggregate(
                collectionFactory.apply(entityType).find(filter).toList(),
                aggregateSelection.field(),
                aggregateSelection.function());
            if (aggregate == null) {
                return null;
            }
            return conversionService.convert(aggregate, resultType).orElse((R) aggregate);
        }
        // Handle count queries specially
        if (Long.class.equals(resultType) || long.class.equals(resultType)) {
            String queryStr = queryResult.getQuery();
            if (queryStr != null && queryStr.contains(NitriteQueryOperators.GROUP)) {
                String fieldPath = queryParser.extractGroupFieldPath(parsedQuery);
                if (fieldPath != null) {
                    long count = collectionFactory.apply(entityType).find(filter).toList().stream()
                        .map(doc -> doc.get(fieldPath))
                        .filter(Objects::nonNull)
                        .distinct()
                        .count();
                    return (R) Long.valueOf(count);
                }
            }
            return (R) Long.valueOf(collectionFactory.apply(entityType).find(filter, options).size());
        }

        Document doc = collectionFactory.apply(entityType).find(filter, options).firstOrNull();
        if (doc == null) {
            return null;
        }
        List<String> projectedFields = queryParser.extractProjectionFields(parsedQuery);
        if (!projectedFields.isEmpty()) {
            return projectionMapper.mapDocument(doc, projectedFields, persistentEntity, resultType, false);
        }
        return entityMapper.fromDocument(doc, resultType);
    }

    /**
     * Finds all entities matching the criteria query.
     *
     * @param query the criteria query
     * @param <T> the entity type
     * @return list of matching entities
     */
    public <T> List<T> findAll(@NonNull CriteriaQuery<T> query) {
        Class<T> type = (Class<T>) ((PersistentEntityQuery<?>) query).getResultType();
        Class<?> entityType = getEntityType(query);
        RuntimePersistentEntity<?> persistentEntity = entityFactory.apply(entityType);

        NitriteRuntimeFilter runtimeFilter = tryBuildRuntimeFilter((AbstractPersistentEntityCriteriaQuery<?>) query);
        Filter filter;
        FindOptions options;
        List<String> projectedFields;
        if (runtimeFilter != null) {
            filter = buildFilterFromRuntimeFilter(runtimeFilter, entityType);
            options = buildFindOptionsFromRuntimeFilter(runtimeFilter, persistentEntity, -1, -1);
            projectedFields = queryParser.extractProjectionFields(runtimeFilter.projection());
        } else {
            QueryResult queryResult = Objects.requireNonNull(((AbstractPersistentEntityCriteriaQuery<?>) query)
                    .build(AnnotationMetadata.EMPTY_METADATA, queryBuilder), "Failed to build query for criteria query");
            Object parsedQuery = parseQueryForFilter(queryResult.getQuery());
            filter = buildFilterFromQueryResult(queryResult, entityType, parsedQuery);
            options = buildFindOptions(persistentEntity, -1, -1, parsedQuery);
            projectedFields = queryParser.extractProjectionFields(parsedQuery);
        }
        List<T> results = new ArrayList<>();
        for (Document doc : collectionFactory.apply(entityType).find(filter, options)) {
            T result = projectedFields.isEmpty()
                ? entityMapper.fromDocument(doc, type)
                : projectionMapper.mapDocument(doc, projectedFields, persistentEntity, type, false);
            if (result != null) {
                results.add(result);
            }
        }
        return results;
    }

    /**
     * Finds all entities matching the criteria query with offset and limit.
     *
     * @param query the criteria query
     * @param offset the offset
     * @param limit the limit
     * @param <T> the entity type
     * @return list of matching entities
     */
    public <T> List<T> findAll(@NonNull CriteriaQuery<T> query, int offset, int limit) {
        Class<T> type = (Class<T>) ((PersistentEntityQuery<?>) query).getResultType();
        Class<?> entityType = getEntityType(query);
        RuntimePersistentEntity<?> persistentEntity = entityFactory.apply(entityType);

        NitriteRuntimeFilter runtimeFilter = tryBuildRuntimeFilter((AbstractPersistentEntityCriteriaQuery<?>) query);
        Filter filter;
        FindOptions options;
        List<String> projectedFields;
        if (runtimeFilter != null) {
            filter = buildFilterFromRuntimeFilter(runtimeFilter, entityType);
            options = buildFindOptionsFromRuntimeFilter(runtimeFilter, persistentEntity, offset, limit);
            projectedFields = queryParser.extractProjectionFields(runtimeFilter.projection());
        } else {
            QueryResult queryResult = Objects.requireNonNull(((AbstractPersistentEntityCriteriaQuery<?>) query)
                    .build(AnnotationMetadata.EMPTY_METADATA, queryBuilder), "Failed to build query for criteria query");
            Object parsedQuery = parseQueryForFilter(queryResult.getQuery());
            filter = buildFilterFromQueryResult(queryResult, entityType, parsedQuery);
            options = buildFindOptions(persistentEntity, offset, limit, parsedQuery);
            projectedFields = queryParser.extractProjectionFields(parsedQuery);
        }
        List<T> results = new ArrayList<>();
        for (Document doc : collectionFactory.apply(entityType).find(filter, options)) {
            T result = projectedFields.isEmpty()
                ? entityMapper.fromDocument(doc, type)
                : projectionMapper.mapDocument(doc, projectedFields, persistentEntity, type, false);
            if (result != null) {
                results.add(result);
            }
        }
        return results;
    }

    /**
     * Updates all entities matching the criteria query.
     *
     * @param query the criteria update query
     * @return optional containing the number of updated entities
     */
    public Optional<Number> updateAll(@NonNull CriteriaUpdate<Number> query) {
        // For Nitrite, we need to fetch entities, apply updates, and save back
        try {
            // Build the query result to get the filter
            QueryResult queryResult = Objects.requireNonNull(((AbstractPersistentEntityCriteriaUpdate<?>) query)
                    .build(AnnotationMetadata.EMPTY_METADATA, queryBuilder), "Failed to build query for criteria update");

            Class<?> entityType = getEntityType(query);
            Filter filter = buildFilterFromQueryResult(queryResult, entityType);

            // Get the update values from the CriteriaUpdate
            RuntimePersistentEntity<?> persistentEntity = entityFactory.apply(entityType);
            Map<String, Object> updateValues = getUpdateValues(query, persistentEntity);

            NitriteCollection collection = collectionFactory.apply(entityType);
            // Each matching document is read, modified and written back as a whole, so the
            // sequence is held under one lock: Nitrite releases its own lock between the read and
            // the write, which would let a concurrent update be overwritten.
            return Optional.of(CollectionUpdateLock.withLock(collection.getName(), () -> {
                List<Document> docs = new ArrayList<>();
                for (Document doc : collection.find(filter)) {
                    docs.add(doc);
                }
                for (Document doc : docs) {
                    for (Map.Entry<String, Object> entry : updateValues.entrySet()) {
                        doc.put(entry.getKey(), entry.getValue());
                    }
                    Filter idFilter = NitriteFilterUtils.eq("_id", doc.get("_id"));
                    collection.update(idFilter, doc);
                }
                return docs.size();
            }));
        } catch (Exception e) {
            throw new IllegalStateException("Failed to update entities by criteria", e);
        }
    }

    private Map<String, Object> getUpdateValues(CriteriaUpdate<?> query, RuntimePersistentEntity<?> persistentEntity) {
        if (query instanceof AbstractPersistentEntityCriteriaUpdate<?> update) {
            Map<String, Object> rawValues = update.getUpdateValues();
            Map<String, Object> resolvedValues = new LinkedHashMap<>();
            int index = 0;
            for (Map.Entry<String, Object> entry : rawValues.entrySet()) {
                Object value = entry.getValue();
                if (value instanceof BindingParameter bindingParam) {
                    // Each bound assignment takes its own index; sharing index 0 would collapse the
                    // parameters of a multi-column update onto one another. Literal assignments are
                    // not bound and must not consume an index.
                    value = bindingParam.bind(BindingParameter.BindingContext.create().index(index)).getValue();
                    index++;
                }
                // Assignments are keyed by the Java property path; the stored document uses the
                // persisted path, which differs for @MappedProperty at any depth.
                resolvedValues.put(NitriteEntityMapper.persistedPath(entry.getKey(), persistentEntity), value);
            }
            return Collections.unmodifiableMap(resolvedValues);
        }
        return Collections.emptyMap();
    }

    /**
     * Deletes all entities matching the criteria delete query.
     *
     * @param query the criteria delete query
     * @return optional containing the number of deleted entities
     */
    public Optional<Number> deleteAll(@NonNull CriteriaDelete<Number> query) {
        QueryResult queryResult = Objects.requireNonNull(((AbstractPersistentEntityCriteriaDelete<?>) query)
                .build(AnnotationMetadata.EMPTY_METADATA, queryBuilder), "Failed to build query for criteria delete");
        Class<?> type = getEntityType(query);
        Filter filter = buildFilterFromQueryResult(queryResult, type);
        long count = collectionFactory.apply(type).remove(filter).getAffectedCount();
        return Optional.of(count);
    }

    private Class<?> getEntityType(Object query) {
        if (query instanceof CriteriaUpdate<?> update) {
            return ((RuntimePersistentEntity<?>) ((PersistentEntityCriteriaUpdate<?>) update).getPersistentEntity()).getIntrospection().getBeanType();
        } else if (query instanceof CriteriaDelete<?> delete) {
            return ((RuntimePersistentEntity<?>) ((PersistentEntityCriteriaDelete<?>) delete).getPersistentEntity()).getIntrospection().getBeanType();
        } else {
            return ((RuntimePersistentEntity<?>) ((PersistentEntityQuery<?>) query).getPersistentEntity()).getIntrospection().getBeanType();
        }
    }

    /**
     * Attempts the fast path: build the filter/sort/projection directly from the Criteria
     * predicate tree, skipping the JSON serialize/reparse round trip {@link #buildFilterFromQueryResult}
     * needs. Returns {@code null} when the query needs the aggregation pipeline (joins, group,
     * count) — callers fall back to the {@link QueryResult}-based path for that case.
     */
    private @Nullable NitriteRuntimeFilter tryBuildRuntimeFilter(AbstractPersistentEntityCriteriaQuery<?> query) {
        if (!(queryBuilder instanceof NitriteQueryBuilder nitriteQueryBuilder)) {
            return null;
        }
        return nitriteQueryBuilder.buildRuntimeFilter(query.toSelectQueryDefinition());
    }

    private Filter buildFilterFromRuntimeFilter(NitriteRuntimeFilter runtimeFilter, Class<?> entityType) {
        Map<String, Object> filterMap = runtimeFilter.filter();
        if (filterMap.isEmpty()) {
            return Filter.ALL;
        }
        List<QueryParameterBinding> bindings = runtimeFilter.parameterBindings();
        Object[] params = new Object[bindings.size()];
        for (int i = 0; i < bindings.size(); i++) {
            params[i] = bindings.get(i).getValue();
        }
        return filterBuilder.buildFilterFromJson(entityFactory.apply(entityType), filterMap, params, Collections.emptyMap());
    }

    private FindOptions buildFindOptionsFromRuntimeFilter(NitriteRuntimeFilter runtimeFilter, RuntimePersistentEntity<?> persistentEntity, int offset, int limit) {
        FindOptions options = new FindOptions();
        if (offset > 0) {
            options.skip((long) offset);
        } else if (runtimeFilter.offset() > 0) {
            options.skip((long) runtimeFilter.offset());
        }
        if (limit > 0) {
            options.limit((long) limit);
        } else if (runtimeFilter.limit() != -1) {
            options.limit((long) runtimeFilter.limit());
        }
        for (Map.Entry<String, Object> entry : runtimeFilter.sort().entrySet()) {
            SortOrder order = ((Number) entry.getValue()).intValue() == 1 ? SortOrder.Ascending : SortOrder.Descending;
            options.thenOrderBy(entityMapper.normalizeFieldName(entry.getKey(), persistentEntity), order);
        }
        return options;
    }

    /**
     * Parses the query JSON once, guarded the same way {@link #buildFilterFromQueryResult} is,
     * so callers that need the filter plus sort/aggregate info can share a single parse instead
     * of each re-parsing the same string.
     */
    private @Nullable Object parseQueryForFilter(@Nullable String queryString) {
        if (queryString == null || queryString.trim().isEmpty() || "{}".equals(queryString.trim())) {
            return null;
        }
        try {
            return queryParser.parseJson(queryString);
        } catch (Exception e) {
            throw new IllegalStateException(
                "Failed to build Nitrite filter from criteria query: " + queryString, e);
        }
    }

    private Filter buildFilterFromQueryResult(QueryResult queryResult, Class<?> entityType) {
        return buildFilterFromQueryResult(queryResult, entityType, parseQueryForFilter(queryResult.getQuery()));
    }

    private Filter buildFilterFromQueryResult(QueryResult queryResult, Class<?> entityType, @Nullable Object parsedQuery) {
        if (parsedQuery == null) {
            return Filter.ALL;
        }
        try {
            Map<String, Object> filterMap = queryParser.extractFilterMap(parsedQuery);
            if (filterMap == null || filterMap.isEmpty()) {
                return Filter.ALL;
            }

            // Build parameter array from parameter bindings
            List<QueryParameterBinding> bindings = queryResult.getParameterBindings();
            Object[] params = new Object[bindings.size()];
            for (int i = 0; i < bindings.size(); i++) {
                params[i] = bindings.get(i).getValue();
            }

            return filterBuilder.buildFilterFromJson(
                    entityFactory.apply(entityType),
                    filterMap,
                    params,
                    Collections.emptyMap());
        } catch (Exception e) {
            throw new IllegalStateException(
                "Failed to build Nitrite filter from criteria query: " + queryResult.getQuery(), e);
        }
    }

    private @Nullable AggregateSelection extractAggregateSelection(@Nullable Object parsedQuery) {
        try {
            // Only a pipeline carries a $group stage; a single-stage query never does.
            if (!(parsedQuery instanceof List<?> pipeline)) {
                return null;
            }
            for (Object stage : pipeline) {
                if (!(stage instanceof Map<?, ?> stageMap) || !(stageMap.get(NitriteQueryOperators.GROUP) instanceof Map<?, ?> group)) {
                    continue;
                }
                if (group.get("_id") != null) {
                    // A grouped aggregate produces one value per key; only the ungrouped form can
                    // be answered by collapsing the whole result set to a single value.
                    return null;
                }
                for (Map.Entry<?, ?> entry : group.entrySet()) {
                    if (!(entry.getValue() instanceof Map<?, ?> operation)) {
                        continue;
                    }
                    for (String operator : List.of(NitriteQueryOperators.MAX, NitriteQueryOperators.MIN,
                        NitriteQueryOperators.SUM, NitriteQueryOperators.AVG)) {
                        Object field = operation.get(operator);
                        if (field instanceof String path && path.startsWith("$")) {
                            String function = Character.toUpperCase(operator.charAt(1)) + operator.substring(2);
                            return new AggregateSelection(function, path.substring(1));
                        }
                    }
                }
            }
        } catch (Exception ignored) {
            // The normal query path reports malformed criteria queries.
        }
        return null;
    }

    private FindOptions buildFindOptions(RuntimePersistentEntity<?> persistentEntity, int offset, int limit, @Nullable Object parsedQuery) {
        FindOptions options = new FindOptions();

        // Handle explicit offset/limit parameters
        if (offset > 0) {
            options.skip((long) offset);
        }
        if (limit > 0) {
            options.limit((long) limit);
        }

        if (parsedQuery != null) {
            try {
                List<Map<?, ?>> stages;
                if (parsedQuery instanceof Map<?, ?> m) {
                    stages = List.of(m);
                } else if (parsedQuery instanceof List<?> pipeline) {
                    List<Map<?, ?>> pipelineStages = new ArrayList<>();
                    for (Object s : pipeline) {
                        if (s instanceof Map<?, ?> sm) {
                            pipelineStages.add(sm);
                        }
                    }
                    stages = pipelineStages;
                } else {
                    return options;
                }

                for (Map<?, ?> stage : stages) {
                    if (stage.get(NitriteQueryOperators.SORT) instanceof Map<?, ?> sortMap) {
                        for (Map.Entry<?, ?> entry : sortMap.entrySet()) {
                            SortOrder order = ((Number) entry.getValue()).intValue() == 1 ? SortOrder.Ascending : SortOrder.Descending;
                            options.thenOrderBy(entityMapper.normalizeFieldName(entry.getKey().toString(), persistentEntity), order);
                        }
                    }
                    if (offset <= 0 && stage.get(NitriteQueryOperators.SKIP) instanceof Number skip) {
                        options.skip(skip.longValue());
                    }
                    if (limit <= 0 && stage.get(NitriteQueryOperators.LIMIT) instanceof Number lim) {
                        options.limit(lim.longValue());
                    }
                }

            } catch (Exception e) {
                // Ignore parse errors for options
            }
        }
        return options;
    }

    private record AggregateSelection(String function, String field) {
    }
}
