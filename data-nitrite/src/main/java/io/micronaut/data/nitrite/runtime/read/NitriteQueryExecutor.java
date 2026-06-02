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
package io.micronaut.data.nitrite.runtime.read;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.data.annotation.Version;
import io.micronaut.data.event.EntityEventListener;
import io.micronaut.data.exceptions.OptimisticLockException;
import io.micronaut.data.model.Limit;
import io.micronaut.data.model.Pageable;
import io.micronaut.data.model.Sort;
import io.micronaut.data.model.query.JoinPath;
import io.micronaut.data.model.runtime.PreparedQuery;
import io.micronaut.data.model.runtime.RuntimePersistentEntity;
import io.micronaut.data.model.runtime.RuntimePersistentProperty;
import io.micronaut.data.model.runtime.StoredQuery;
import io.micronaut.data.nitrite.runtime.NitriteOperationsHelper;
import io.micronaut.data.nitrite.runtime.query.NitriteQueryBinder;
import io.micronaut.data.nitrite.runtime.ValueConverter;
import io.micronaut.data.nitrite.runtime.mapping.NitriteEntityMapper;
import io.micronaut.data.nitrite.runtime.query.NitriteFilterBuilder;
import io.micronaut.data.nitrite.runtime.query.NitritePreparedQuery;
import io.micronaut.data.nitrite.runtime.query.NitriteQueryParser;
import org.dizitart.no2.collection.Document;
import org.dizitart.no2.collection.FindOptions;
import org.dizitart.no2.collection.NitriteCollection;
import org.dizitart.no2.collection.UpdateOptions;
import org.dizitart.no2.filters.Filter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.regex.Pattern;

/**
 * Executor for Nitrite queries.
 *
 * @since 4.14.0
 */
@Internal
public final class NitriteQueryExecutor {

    private static final Pattern TOP_FIRST_PATTERN = Pattern.compile("(?:Top|First)(\\d+)");

    private final NitriteEntityMapper entityMapper;
    private final NitriteQueryParser queryParser;
    private final NitriteFilterBuilder filterBuilder;
    private final Function<Class<?>, NitriteCollection> collectionFactory;
    private final Function<Class<?>, RuntimePersistentEntity<?>> entityFactory;
    private final BiFunction<Pageable, Sort, FindOptions> findOptionsFactory;
    private final NitriteOperationsHelper helper;

    // Centralized strategy classes for result handling
    private final ObjectRepositoryMapper entityMapperHandler;
    private final ValueConverter valueConverter;
    private final CollectionFieldMapper nativeProjectionHandler;
    private final CollectionAggregator aggregationHandler;
    private final JoinFetcher joinFetcher;

    /**
     * Creates a new NitriteQueryExecutor.
     *
     * @param entityMapper the entity mapper
     * @param queryParser the query parser
     * @param filterBuilder the filter builder
     * @param conversionService the conversion service
     * @param collectionFactory the collection factory function
     * @param entityFactory the entity factory function
     * @param findOptionsFactory the find options factory function
     * @param helper the operations helper
     * @param entityEventListener the entity event listener
     */
    public NitriteQueryExecutor(NitriteEntityMapper entityMapper,
                                NitriteQueryParser queryParser,
                                NitriteFilterBuilder filterBuilder,
                                ConversionService conversionService,
                                Function<Class<?>, NitriteCollection> collectionFactory,
                                Function<Class<?>, RuntimePersistentEntity<?>> entityFactory,
                                BiFunction<Pageable, Sort, FindOptions> findOptionsFactory,
                                NitriteOperationsHelper helper,
                                EntityEventListener<Object> entityEventListener) {
        this.entityMapper = entityMapper;
        this.queryParser = queryParser;
        this.filterBuilder = filterBuilder;
        this.collectionFactory = collectionFactory;
        this.entityFactory = entityFactory;
        this.findOptionsFactory = findOptionsFactory;
        this.helper = helper;

        // Initialize centralized strategy classes
        this.valueConverter = new ValueConverter(conversionService);
        this.entityMapperHandler = new ObjectRepositoryMapper(entityMapper);
        this.nativeProjectionHandler = new CollectionFieldMapper(queryParser, valueConverter, entityMapper);
        this.aggregationHandler = new CollectionAggregator();
        this.joinFetcher = new JoinFetcher(entityMapper, collectionFactory, entityFactory, conversionService);
    }

    /**
     * Converts a value for use in a filter.
     *
     * @param value the value to convert
     * @return the converted value
     */
    public Object toFilterValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Iterable<?> iterable && !(value instanceof Document)) {
            List<Object> list = new ArrayList<>();
            for (Object o : iterable) {
                list.add(toFilterValue(o));
            }
            return list;
        }
        return entityMapper.toFilterValue(value);
    }

    /**
     * Finds a single result matching the prepared query.
     *
     * @param q the prepared query
     * @param nq the nitrite prepared query
     * @param <T> the entity type
     * @param <R> the result type
     * @return the first matching result, or null if none found
     */
    public <T, R> R findOne(@NonNull PreparedQuery<T, R> q, NitritePreparedQuery<T, R> nq) {
        NitriteCollection coll = collectionFactory.apply(nq.getRootEntity());
        if (nq.getUpdateMap() != null) {
            Optional<Number> result = executeUpdate((PreparedQuery<?, Number>) nq, (NitritePreparedQuery<?, Number>) nq);
            return Number.class.isAssignableFrom(nq.getResultType()) ? (R) result.orElse(0L) : null;
        }

        Filter filter = nq.getNitriteFilter();
        helper.logFind(coll.getName(), filter);

        // Handle count queries
        if (Number.class.isAssignableFrom(nq.getResultType())) {
            String methodName = q.getName();
            String queryStr = nq.getQuery();
            boolean isCountQuery = methodName.startsWith("count") ||
                (nq.getOperationType() != null && nq.getOperationType() == StoredQuery.OperationType.COUNT) ||
                (queryStr != null && queryStr.contains("$count"));
            if (isCountQuery) {
                return (R) Long.valueOf(coll.find(filter).size());
            }
        }

        String methodName = q.getName();

        // Handle aggregation methods (findMax/Min/Sum/Avg...By)
        if (aggregationHandler.isAggregationMethod(methodName)) {
            String aggFunc = aggregationHandler.extractAggFunc(methodName);
            String fieldName = aggregationHandler.extractFieldName(methodName);
            List<Document> docs = coll.find(filter).toList();
            Object result = aggregationHandler.aggregate(docs, fieldName, aggFunc);
            return valueConverter.convertWithTemporalHandling(result, nq.getResultType());
        }

        Document doc = coll.find(filter).firstOrNull();
        if (doc == null) {
            return null;
        }

        // Handle DTO projection
        if (nq.isDtoProjection()) {
            return entityMapperHandler.projectDto(doc, nq.getResultType());
        }

        // Handle native single-field projection (result type differs from root entity)
        if (!nq.getResultType().equals(nq.getRootEntity())) {
            R result = nativeProjectionHandler.project(doc, nq.getQuery(), methodName, entityFactory.apply(nq.getRootEntity()), nq.getResultType());
            if (result != null) {
                return result;
            }
        }

        // Handle full entity load
        R entity = (R) entityMapperHandler.loadEntity(doc, nq.getRootEntity());

        // Fetch joined associations if specified
        Set<JoinPath> joinPaths = nq.getJoinPaths();
        if (joinPaths != null && !joinPaths.isEmpty()) {
            joinFetcher.fetch(Collections.singletonList(entity), joinPaths, nq.getRootEntity());
        }

        return entity;
    }

    /**
     * Finds all results matching the prepared query.
     *
     * @param q the prepared query
     * @param nq the nitrite prepared query
     * @param <T> the entity type
     * @param <R> the result type
     * @return iterable of matching results
     */
    public <T, R> Iterable<R> findAll(@NonNull PreparedQuery<T, R> q, NitritePreparedQuery<T, R> nq) {
        NitriteCollection coll = collectionFactory.apply(nq.getRootEntity());
        Filter filter = nq.getNitriteFilter();
        helper.logFind(coll.getName(), filter);

        // Handle count queries
        if (Number.class.isAssignableFrom(nq.getResultType())) {
            String methodName = q.getName();
            String queryStr = nq.getQuery();
            boolean isCountQuery = methodName.startsWith("count") ||
                (nq.getOperationType() != null && nq.getOperationType() == StoredQuery.OperationType.COUNT) ||
                (queryStr != null && queryStr.contains("$count"));
            if (isCountQuery) {
                return Collections.singletonList((R) Long.valueOf(coll.find(filter).size()));
            }
        }

        // Setup sort and limit
        Sort s = nq.getSort();
        if (s == null || !s.isSorted()) {
            String query = nq.getQuery();
            if (query != null) {
                s = helper.parseSortFromJsonQuery(query);
            }
            if ((s == null || !s.isSorted()) && nq.getQueryHints() != null) {
                s = helper.parseSortFromHints(nq.getQueryHints());
            }
        }
        Limit limit = nq.getQueryLimit();
        if (limit.maxResults() <= 0) {
            String methodName = q.getName();
            java.util.regex.Matcher matcher = TOP_FIRST_PATTERN.matcher(methodName);
            if (matcher.find()) {
                limit = Limit.of(Integer.parseInt(matcher.group(1)), 0);
            }
        }

        FindOptions findOptions = findOptionsFactory.apply(nq.getPageable(), s);
        if (limit.maxResults() > 0) {
            findOptions.limit((long) limit.maxResults());
            findOptions.skip(limit.offset());
        }

        String methodName = q.getName();
        String query = nq.getQuery();

        // Handle aggregation methods - not typically used with findAll, but handle for completeness
        if (aggregationHandler.isAggregationMethod(methodName)) {
            // Aggregation with findAll returns single aggregated value
            var cursor = coll.find(filter, findOptions);
            List<Document> docs = cursor.toList();
            String aggFunc = aggregationHandler.extractAggFunc(methodName);
            String fieldName = aggregationHandler.extractFieldName(methodName);
            Object result = aggregationHandler.aggregate(docs, fieldName, aggFunc);
            return Collections.singletonList(valueConverter.convertWithTemporalHandling(result, nq.getResultType()));
        }

        // Handle DTO projection
        if (nq.isDtoProjection()) {
            var cursor = coll.find(filter, findOptions);
            List<R> results = new ArrayList<>();
            for (Document doc : cursor) {
                results.add(entityMapperHandler.projectDto(doc, nq.getResultType()));
            }
            return results;
        }

        // Handle native single-field projection
        if (!nq.getResultType().equals(nq.getRootEntity()) && !nq.isDtoProjection()) {
            List<String> projectedFields = null;
            String projectField = queryParser.extractProjectionField(query);
            if (projectField != null) {
                projectedFields = Collections.singletonList(projectField);
            }
            if (projectedFields == null) {
                String fieldName = nativeProjectionHandler.extractFieldName(query, methodName);
                if (fieldName != null) {
                    projectedFields = Collections.singletonList(fieldName);
                }
            }

            if (projectedFields != null) {
                var cursor = coll.find(filter, findOptions);
                RuntimePersistentEntity<?> entity = entityFactory.apply(nq.getRootEntity());
                List<R> results = new ArrayList<>();
                String field = projectedFields.getFirst();
                for (Document doc : cursor) {
                    R result = nativeProjectionHandler.project(doc, field, entity, nq.getResultType());
                    if (result != null) {
                        results.add(result);
                    }
                }
                return results;
            }
        }

        // Handle full entity load
        var cursor = coll.find(filter, findOptions);
        List<R> results = new ArrayList<>();
        for (Document doc : cursor) {
            results.add((R) entityMapperHandler.loadEntity(doc, nq.getRootEntity()));
        }

        // Fetch joined associations if specified
        Set<JoinPath> joinPaths = nq.getJoinPaths();
        if (joinPaths != null && !joinPaths.isEmpty()) {
            joinFetcher.fetch(results, joinPaths, nq.getRootEntity());
        }

        return results;
    }

    /**
     * Executes an update query.
     *
     * @param q the prepared query
     * @param nq the nitrite prepared query
     * @return optional containing the number of updated entities
     */
    public Optional<Number> executeUpdate(@NonNull PreparedQuery<?, Number> q, NitritePreparedQuery<?, Number> nq) {
        if (nq.getFilterMap() == null) {
            throw new UnsupportedOperationException("executeUpdate() requires a JSON filter map: " + nq.getQuery());
        }
        Object[] jsonParams = buildJsonParameterValues(nq);
        Map<String, Object> namedParameters = buildNamedParameterValues(nq);

        @SuppressWarnings("unchecked")
        Map<String, Object> rawSetFields = (Map<String, Object>) nq.getFilterMap().get("$set");
        if (rawSetFields == null) {
            rawSetFields = nq.getUpdateMap();
        }
        if (rawSetFields == null || rawSetFields.isEmpty()) {
            return Optional.of(0);
        }
        Map<String, Object> setFields = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : rawSetFields.entrySet()) {
            setFields.put(entry.getKey(), resolveParameterValue(entry.getValue(), jsonParams, namedParameters));
        }

        Filter filter;
        if (nq.getCompiledFilter() != null) {
            filter = nq.getCompiledFilter().bind(jsonParams, namedParameters);
        } else {
            filter = filterBuilder.buildFilterFromJson(entityFactory.apply(nq.getRootEntity()), nq.getFilterMap(), jsonParams, namedParameters);
        }

        Document updateDoc = Document.createDocument();
        setFields.forEach(updateDoc::put);
        if (isOptimisticLocking(q)) {
            applyVersionIncrement(updateDoc, entityFactory.apply(nq.getRootEntity()), namedParameters);
        }
        Filter finalFilter = filter != null ? filter : nq.getNitriteFilter();
        helper.logUpdate(collectionFactory.apply(nq.getRootEntity()).getName(), finalFilter, updateDoc);
        long count = collectionFactory.apply(nq.getRootEntity()).update(finalFilter, updateDoc, UpdateOptions.updateOptions(false)).getAffectedCount();
        if (count == 0 && finalFilter != Filter.ALL && isOptimisticLocking(q)) {
            throw new OptimisticLockException("Execute update returned unexpected row count. Expected: 1 got: 0");
        }
        return Optional.of(count);
    }

    public Optional<Number> executeDelete(@NonNull PreparedQuery<?, Number> q, NitritePreparedQuery<?, Number> nq) {
        helper.logDelete(collectionFactory.apply(nq.getRootEntity()).getName(), nq.getNitriteFilter());
        long count = collectionFactory.apply(nq.getRootEntity()).remove(nq.getNitriteFilter(), false).getAffectedCount();
        if (count == 0 && nq.getNitriteFilter() != Filter.ALL && isOptimisticLocking(q)) {
            throw new OptimisticLockException("Execute update returned unexpected row count. Expected: 1 got: 0");
        }
        return Optional.of(count);
    }

    public long count(@NonNull PreparedQuery<?, ?> q, NitritePreparedQuery<?, ?> nq) {
        return collectionFactory.apply(nq.getRootEntity()).find(nq.getNitriteFilter()).size();
    }

    boolean isOptimisticLocking(@NonNull PreparedQuery<?, ?> q) {
        if (q.getArguments() != null && Arrays.stream(q.getArguments()).anyMatch(arg -> arg.getAnnotationMetadata().hasAnnotation(Version.class))) {
            return true;
        }
        String methodName = q.getName();
        if (methodName.contains("AndVersion") || methodName.contains("ByVersion")) {
            return true;
        }
        return q.getQuery().toLowerCase().contains("version");
    }

    private void applyVersionIncrement(Document updateDoc, RuntimePersistentEntity<?> entity, Map<String, Object> namedParameters) {
        RuntimePersistentProperty<?> versionProp = entity.getVersion();
        String vPersistedName = versionProp.getPersistedName();
        Object currentValInUpdate = updateDoc.get(vPersistedName);
        if (currentValInUpdate == null) {
            Object currentVersion = namedParameters.get(entity.getVersion().getName());
            if (currentVersion == null) {
                currentVersion = namedParameters.get(vPersistedName);
            }
            if (currentVersion instanceof Number n) {
                updateDoc.put(vPersistedName, n.longValue() + 1);
            }
        }
    }

    /**
     * Builds a map of named parameter values from a prepared query.
     *
     * @param q the prepared query
     * @return the map of named parameter values
     */
    public Map<String, Object> buildNamedParameterValues(@NonNull final PreparedQuery<?, ?> q) {
        return NitriteQueryBinder.buildNamedParameterValues(q, this::toFilterValue);
    }

    /**
     * Builds an array of JSON parameter values from a prepared query.
     *
     * @param q the prepared query
     * @return the array of JSON parameter values
     */
    public Object[] buildJsonParameterValues(@NonNull final PreparedQuery<?, ?> q) {
        return NitriteQueryBinder.buildJsonParameterValues(q, this::toFilterValue, null);
    }

    /**
     * Builds a filter from a prepared query.
     *
     * @return the built filter
     */
    private Object resolveParameterValue(Object value, Object[] jsonParams, Map<String, Object> namedParameters) {
        return NitriteQueryBinder.resolveParameterValue(value, jsonParams, namedParameters, this::toFilterValue);
    }

}
