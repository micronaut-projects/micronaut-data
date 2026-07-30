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
import io.micronaut.data.nitrite.runtime.ValueConverter;
import io.micronaut.data.nitrite.runtime.mapping.NitriteEntityMapper;
import io.micronaut.data.nitrite.runtime.query.NitriteFilterBuilder;
import io.micronaut.data.nitrite.runtime.query.NitriteQueryParser;
import io.micronaut.data.nitrite.runtime.read.CollectionProjectionMapper;
import jakarta.persistence.criteria.CriteriaDelete;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.CriteriaUpdate;
import org.dizitart.no2.collection.Document;
import org.dizitart.no2.collection.FindOptions;
import org.dizitart.no2.collection.NitriteCollection;
import org.dizitart.no2.common.SortOrder;
import org.dizitart.no2.filters.Filter;
import org.dizitart.no2.filters.FluentFilter;

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
 * @since 4.14.0
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
        this.projectionMapper = new CollectionProjectionMapper(new ValueConverter(conversionService), entityMapper);
    }

    /**
     * Checks if any entities match the given criteria query.
     *
     * @param query the criteria query
     * @return true if at least one entity matches
     */
    public boolean exists(@NonNull CriteriaQuery<?> query) {
        QueryResult queryResult = ((AbstractPersistentEntityCriteriaQuery<?>) query)
                .build(AnnotationMetadata.EMPTY_METADATA, queryBuilder);
        Class<?> type = getEntityType(query);
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
        QueryResult queryResult = ((AbstractPersistentEntityCriteriaQuery<?>) query)
                .build(AnnotationMetadata.EMPTY_METADATA, queryBuilder);
        Class<?> entityType = getEntityType(query);
        RuntimePersistentEntity<?> persistentEntity = entityFactory.apply(entityType);
        Class<R> resultType = (Class<R>) ((PersistentEntityQuery) query).getResultType();
        Filter filter = buildFilterFromQueryResult(queryResult, entityType);
        FindOptions options = buildFindOptions(queryResult, persistentEntity, -1, -1);

        // Handle count queries specially
        if (Long.class.equals(resultType) || long.class.equals(resultType)) {
            String queryStr = queryResult.getQuery();
            if (queryStr != null && queryStr.contains("$group")) {
                String fieldPath = queryParser.extractGroupFieldPath(queryStr);
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
        List<String> projectedFields = queryParser.extractProjectionFields(queryResult.getQuery());
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
        QueryResult queryResult = ((AbstractPersistentEntityCriteriaQuery<?>) query)
                .build(AnnotationMetadata.EMPTY_METADATA, queryBuilder);
        Class<T> type = (Class<T>) ((PersistentEntityQuery) query).getResultType();
        Class<?> entityType = getEntityType(query);
        RuntimePersistentEntity<?> persistentEntity = entityFactory.apply(entityType);
        Filter filter = buildFilterFromQueryResult(queryResult, entityType);
        FindOptions options = buildFindOptions(queryResult, persistentEntity, -1, -1);
        List<T> results = new ArrayList<>();
        List<String> projectedFields = queryParser.extractProjectionFields(queryResult.getQuery());
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
        QueryResult queryResult = ((AbstractPersistentEntityCriteriaQuery<?>) query)
                .build(AnnotationMetadata.EMPTY_METADATA, queryBuilder);
        Class<T> type = (Class<T>) ((PersistentEntityQuery) query).getResultType();
        Class<?> entityType = getEntityType(query);
        RuntimePersistentEntity<?> persistentEntity = entityFactory.apply(entityType);
        Filter filter = buildFilterFromQueryResult(queryResult, entityType);
        FindOptions options = buildFindOptions(queryResult, persistentEntity, offset, limit);
        List<T> results = new ArrayList<>();
        List<String> projectedFields = queryParser.extractProjectionFields(queryResult.getQuery());
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
            QueryResult queryResult = ((AbstractPersistentEntityCriteriaUpdate<?>) query)
                    .build(AnnotationMetadata.EMPTY_METADATA, queryBuilder);

            Class<?> entityType = getEntityType(query);
            Filter filter = buildFilterFromQueryResult(queryResult, entityType);

            // Get the update values from the CriteriaUpdate
            Map<String, Object> updateValues = getUpdateValues(query);

            // Get all matching entities
            NitriteCollection collection = collectionFactory.apply(entityType);
            List<Document> docs = new ArrayList<>();
            for (Document doc : collection.find(filter)) {
                docs.add(doc);
            }

            if (docs.isEmpty()) {
                return Optional.of(0);
            }

            // Apply update to each document
            for (Document doc : docs) {
                for (Map.Entry<String, Object> entry : updateValues.entrySet()) {
                    doc.put(entry.getKey(), entry.getValue());
                }
                // Update the document in the collection
                Filter idFilter = FluentFilter.where("_id").eq(doc.get("_id"));
                collection.update(idFilter, doc);
            }

            return Optional.of(docs.size());
        } catch (Exception e) {
            throw new IllegalStateException("Failed to update entities by criteria", e);
        }
    }

    private Map<String, Object> getUpdateValues(CriteriaUpdate<?> query) {
        if (query instanceof AbstractPersistentEntityCriteriaUpdate<?> update) {
            Map<String, Object> rawValues = update.getUpdateValues();
            Map<String, Object> resolvedValues = new LinkedHashMap<>();
            for (Map.Entry<String, Object> entry : rawValues.entrySet()) {
                Object value = entry.getValue();
                if (value instanceof BindingParameter bindingParam) {
                    value = bindingParam.bind(BindingParameter.BindingContext.create().index(0)).getValue();
                }
                resolvedValues.put(entry.getKey(), value);
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
        QueryResult queryResult = ((AbstractPersistentEntityCriteriaDelete<?>) query)
                .build(AnnotationMetadata.EMPTY_METADATA, queryBuilder);
        Class<?> type = getEntityType(query);
        Filter filter = buildFilterFromQueryResult(queryResult, type);
        long count = collectionFactory.apply(type).remove(filter).getAffectedCount();
        return Optional.of(count);
    }

    private Class<?> getEntityType(Object query) {
        if (query instanceof CriteriaUpdate<?> update) {
            return ((RuntimePersistentEntity) ((PersistentEntityCriteriaUpdate<?>) update).getPersistentEntity()).getIntrospection().getBeanType();
        } else if (query instanceof CriteriaDelete<?> delete) {
            return ((RuntimePersistentEntity) ((PersistentEntityCriteriaDelete<?>) delete).getPersistentEntity()).getIntrospection().getBeanType();
        } else {
            return ((RuntimePersistentEntity) ((PersistentEntityQuery<?>) query).getPersistentEntity()).getIntrospection().getBeanType();
        }
    }

    private Filter buildFilterFromQueryResult(QueryResult queryResult, Class<?> entityType) {
        String queryString = queryResult.getQuery();
        if (queryString == null || queryString.trim().isEmpty() || "{}".equals(queryString.trim())) {
            return Filter.ALL;
        }
        try {
            Map<String, Object> filterMap = queryParser.extractFilterMap(queryParser.parseJson(queryString));
            if (filterMap == null || filterMap.isEmpty()) {
                return Filter.ALL;
            }

            // Build parameter array from parameter bindings
            List<QueryParameterBinding> bindings = queryResult.getParameterBindings();
            Object[] params = new Object[bindings.size()];
            for (int i = 0; i < bindings.size(); i++) {
                params[i] = bindings.get(i).getValue();
            }

            // Resolve placeholders in the filter map using params
            resolveFilterMapPlaceholders(filterMap, params);

            return filterBuilder.buildFilterFromJson(
                    entityFactory.apply(entityType),
                    filterMap,
                    params,
                    Collections.emptyMap());
        } catch (Exception e) {
            throw new IllegalStateException(
                "Failed to build Nitrite filter from criteria query: " + queryString, e);
        }
    }

    private void resolveFilterMapPlaceholders(Map<String, Object> map, Object[] params) {
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            Object value = entry.getValue();
            if (value instanceof Map m) {
                resolveFilterMapPlaceholders(m, params);
            } else if (value instanceof String str && str.startsWith("$mn_qp:")) {
                // Resolve placeholder
                try {
                    int idx = Integer.parseInt(str.substring(7));
                    if (params != null && idx >= 0 && idx < params.length) {
                        entry.setValue(params[idx]);
                    }
                } catch (NumberFormatException e) {
                    // Ignore
                }
            }
        }
    }

    private FindOptions buildFindOptions(QueryResult queryResult, RuntimePersistentEntity<?> persistentEntity, int offset, int limit) {
        FindOptions options = new FindOptions();

        // Handle explicit offset/limit parameters
        if (offset > 0) {
            options.skip((long) offset);
        }
        if (limit > 0) {
            options.limit((long) limit);
        }

        String queryString = queryResult.getQuery();
        if (queryString != null && !queryString.isEmpty()) {
            try {
                Object parsed = queryParser.parseJson(queryString);
                List<Map<?, ?>> stages;
                if (parsed instanceof Map<?, ?> m) {
                    stages = List.of(m);
                } else if (parsed instanceof List<?> pipeline) {
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
                    if (stage.get("$sort") instanceof Map<?, ?> sortMap) {
                        for (Map.Entry<?, ?> entry : sortMap.entrySet()) {
                            SortOrder order = ((Number) entry.getValue()).intValue() == 1 ? SortOrder.Ascending : SortOrder.Descending;
                            options.thenOrderBy(entityMapper.normalizeFieldName(entry.getKey().toString(), persistentEntity), order);
                        }
                    }
                    if (offset <= 0 && stage.get("$skip") instanceof Number skip) {
                        options.skip(skip.longValue());
                    }
                    if (limit <= 0 && stage.get("$limit") instanceof Number lim) {
                        options.limit(lim.longValue());
                    }
                }

            } catch (Exception e) {
                // Ignore parse errors for options
            }
        }
        return options;
    }
}
