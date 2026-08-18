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
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.reflect.ReflectionUtils;
import io.micronaut.core.type.Argument;
import io.micronaut.data.annotation.Projection;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.Version;
import io.micronaut.data.event.EntityEventListener;
import io.micronaut.data.exceptions.NonUniqueResultException;
import io.micronaut.data.exceptions.OptimisticLockException;
import io.micronaut.data.model.Limit;
import io.micronaut.data.model.Pageable;
import io.micronaut.data.model.Sort;
import io.micronaut.data.model.query.JoinPath;
import io.micronaut.data.model.runtime.PreparedQuery;
import io.micronaut.data.model.runtime.RuntimePersistentEntity;
import io.micronaut.data.model.runtime.RuntimePersistentProperty;
import io.micronaut.data.model.runtime.StoredQuery;
import io.micronaut.data.nitrite.model.query.NitriteInternalKeys;
import io.micronaut.data.nitrite.model.query.NitriteQueryOperators;
import io.micronaut.data.nitrite.runtime.CollectionUpdateLock;
import io.micronaut.data.nitrite.runtime.NitriteOperationsHelper;
import io.micronaut.data.nitrite.runtime.NumericUpdateOperations;
import io.micronaut.data.nitrite.runtime.query.NitriteFilterUtils;
import io.micronaut.data.nitrite.runtime.query.NitriteQueryBinder;
import io.micronaut.data.nitrite.runtime.ValueConverter;
import io.micronaut.data.nitrite.runtime.mapping.NitriteEntityMapper;
import io.micronaut.data.nitrite.runtime.query.GeneratedQueryParser;
import io.micronaut.data.nitrite.runtime.query.NitriteFilterBuilder;
import io.micronaut.data.nitrite.runtime.query.NitritePreparedQuery;
import io.micronaut.data.nitrite.runtime.query.NitriteQueryParser;
import org.dizitart.no2.collection.Document;
import org.dizitart.no2.collection.FindOptions;
import org.dizitart.no2.collection.NitriteCollection;
import org.dizitart.no2.collection.UpdateOptions;
import org.dizitart.no2.common.RecordStream;
import org.dizitart.no2.filters.Filter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Executes runtime data retrieval queries against a Nitrite collection.
 * <p>
 * This executor consolidates fetching, aggregation, and projection logic:
 * <ul>
 *   <li><strong>Distinct Counts:</strong> Supports {@code COUNT_DISTINCT} via implicit {@code $group._id} pipelines.</li>
 *   <li><strong>Single-Field Projections:</strong> Fully supports native single-field projections (e.g. querying a {@code List<String>} of names), contrary to stale legacy comments.</li>
 *   <li><strong>Aggregations:</strong> Resolves derived aggregations (max, min, sum, avg) for numeric and temporal fields (note: sum/avg on dates is intentionally excluded).</li>
 * </ul>
 *
 * @since 5.2.0
 */
@Internal
public final class NitriteQueryExecutor {

    private static final Pattern TOP_FIRST_PATTERN = Pattern.compile("(?:Top|First)(\\d*)");
    private static final Pattern GENERATED_EQUALITY_PATTERN = Pattern.compile(
        "(?:[A-Za-z0-9_]+\\.)?([A-Za-z0-9_]+)\\s*=\\s*:p\\d+");
    private static final Object[] EMPTY_PARAMS = new Object[0];

    private final NitriteEntityMapper entityMapper;
    private final NitriteQueryParser queryParser;
    private final NitriteFilterBuilder filterBuilder;
    private final Function<Class<?>, NitriteCollection> collectionFactory;
    private final Function<Class<?>, RuntimePersistentEntity<?>> entityFactory;
    private final FindOptionsBuilder findOptionsFactory;
    private final NitriteOperationsHelper helper;
    // Centralized strategy classes for result handling
    private final ObjectRepositoryMapper entityMapperHandler;
    private final ValueConverter valueConverter;
    private final CollectionProjectionMapper projectionMapper;
    private final CollectionFieldMapper nativeProjectionHandler;
    private final CollectionAggregator aggregationHandler;
    private final JoinFetcher joinFetcher;
    /**
     * Parses SQL-shaped queries. Every leaf predicate is routed back through {@link #filterBuilder}
     * so a generated query and its JSON equivalent resolve fields and coerce values identically.
     */
    private final GeneratedQueryParser generatedQueryParser;

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
                                FindOptionsBuilder findOptionsFactory,
                                NitriteOperationsHelper helper,
                                EntityEventListener<Object> entityEventListener) {
        this.entityMapper = entityMapper;
        this.queryParser = queryParser;
        this.filterBuilder = filterBuilder;
        this.generatedQueryParser = new GeneratedQueryParser(
            (entity, propertyPath, operators) ->
                filterBuilder.buildFieldFilter(entity, propertyPath, operators, EMPTY_PARAMS, Collections.emptyMap()));
        this.collectionFactory = collectionFactory;
        this.entityFactory = entityFactory;
        this.findOptionsFactory = findOptionsFactory;
        this.helper = helper;

        // Initialize centralized strategy classes
        this.valueConverter = new ValueConverter(conversionService);
        this.entityMapperHandler = new ObjectRepositoryMapper(entityMapper);
        this.projectionMapper = new CollectionProjectionMapper(valueConverter, entityMapper);
        this.nativeProjectionHandler = new CollectionFieldMapper(queryParser);
        this.aggregationHandler = new CollectionAggregator();
        this.joinFetcher = new JoinFetcher(entityMapper, collectionFactory, entityFactory, conversionService);
    }

    /**
     * Converts a value for use in a filter.
     *
     * @param value the value to convert
     * @return the converted value
     */
    public @Nullable Object toFilterValue(@Nullable Object value) {
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
     * Resolves the document field a derived aggregate reads.
     *
     * <p>The property name derived from the method name is the Java name; resolving it through the
     * entity metadata is what lets an aggregate read a property mapped to a name that is neither
     * that Java name nor its snake-case form.
     *
     * @param propertyName the property name derived from the method name
     * @param nq the prepared query, used to resolve the root entity's metadata
     * @return the field name the property is stored under
     */
    private String persistedField(String propertyName, NitritePreparedQuery<?, ?> nq) {
        return entityMapper.normalizeFieldName(propertyName, entityFactory.apply(nq.getRootEntity()));
    }

    private Long handleDistinctCount(NitriteCollection coll, Filter filter, String queryStr) {
        String fieldPath = queryParser.extractGroupFieldPath(queryStr);
        if (fieldPath == null) {
            return coll.find(filter).size();
        }
        return coll.find(filter).toList().stream()
            .map(doc -> doc.get(fieldPath))
            .filter(Objects::nonNull)
            .distinct()
            .count();
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
    public <T, R> @Nullable R findOne(@NonNull PreparedQuery<T, R> q, NitritePreparedQuery<T, R> nq) {
        NitriteCollection coll = collectionFactory.apply(nq.getRootEntity());
        if (nq.getUpdateMap() != null) {
            Optional<Number> result = executeUpdate((PreparedQuery<?, Number>) nq, (NitritePreparedQuery<?, Number>) nq);
            return isNumericResultType(nq.getResultType()) ? (R) result.orElse(0L) : null;
        }

        Filter filter = nq.getNitriteFilter();
        helper.logFind(coll.getName(), filter);

        // Handle count queries
        if (isNumericResultType(nq.getResultType())) {
            String methodName = q.getName();
            String queryStr = nq.getQuery();
            boolean isCountQuery = methodName.startsWith("count") ||
                (nq.getOperationType() != null && nq.getOperationType() == StoredQuery.OperationType.COUNT) ||
                (queryStr != null && queryStr.contains(NitriteQueryOperators.COUNT));
            if (isCountQuery) {
                if (queryStr != null && queryStr.contains(NitriteQueryOperators.GROUP)) {
                    return (R) handleDistinctCount(coll, filter, queryStr);
                }
                return (R) Long.valueOf(coll.find(filter).size());
            }
        }

        String methodName = q.getName();

        // Handle aggregation methods (findMax/Min/Sum/Avg...By)
        if (aggregationHandler.isAggregationMethod(methodName)) {
            String aggFunc = aggregationHandler.extractAggFunc(methodName);
            String fieldName = aggregationHandler.extractFieldName(methodName);
            if (aggFunc != null && fieldName != null) {
                List<Document> docs = coll.find(filter).toList();
                Object result = aggregationHandler.aggregate(docs, persistedField(fieldName, nq), aggFunc);
                return valueConverter.convertWithTemporalHandling(result, nq.getResultType());
            }
        }

        Sort sort = nq.getSort();
        Sort parsedSort = null;
        String queryString = nq.getQuery();
        if (queryString != null) {
            parsedSort = helper.parseSortFromQuery(queryString);
        }
        if ((parsedSort == null || !parsedSort.isSorted()) && nq.getQueryHints() != null) {
            parsedSort = helper.parseSortFromHints(nq.getQueryHints());
        }
        if (parsedSort != null && parsedSort.isSorted()) {
            if (sort == null || !sort.isSorted()) {
                sort = parsedSort;
            } else {
                List<Sort.Order> merged = new ArrayList<>(parsedSort.getOrderBy());
                merged.addAll(sort.getOrderBy());
                sort = Sort.of(merged);
            }
        }
        Limit limit = resolveTopFirstLimit(q.getName(), nq.getQueryLimit());
        FindOptions findOptions = findOptionsFactory.build(nq.getPageable(), sort, entityFactory.apply(q.getRootEntity()));
        if (nq.getPageable().getMode() == Pageable.Mode.OFFSET && limit.maxResults() > 0) {
            findOptions.limit((long) limit.maxResults());
            findOptions.skip(limit.offset());
        }
        boolean hasFindOptions = (sort != null && sort.isSorted()) || limit.maxResults() > 0;
        Document doc = singleResult(hasFindOptions
            ? coll.find(filter, findOptions)
            : coll.find(filter));
        if (doc == null) {
            return null;
        }

        List<String> projectedFields = getProjectedFields(nq);
        if (Object[].class.equals(nq.getResultType()) && !projectedFields.isEmpty()) {
            RuntimePersistentEntity<?> entity = entityFactory.apply(nq.getRootEntity());
            return projectionMapper.mapDocument(doc, projectedFields, entity, nq.getResultType(), false);
        }

        // Handle DTO projection
        if (nq.isDtoProjection()) {
            RuntimePersistentEntity<?> entity = entityFactory.apply(nq.getRootEntity());
            return projectionMapper.mapDocument(remapDtoProjectionDocument(doc, nq), Collections.emptyList(), entity, nq.getResultType(), true);
        }

        // Handle native single-field projection (result type differs from root entity)
        if (!nq.getResultType().equals(nq.getRootEntity())) {
            String fieldName = queryParser.extractProjectionField(nq.getQuery());
            if (fieldName == null) {
                fieldName = nativeProjectionHandler.extractFieldName(nq.getQuery(), methodName);
            }
            if (fieldName != null) {
                RuntimePersistentEntity<?> entity = entityFactory.apply(nq.getRootEntity());
                R result = projectionMapper.mapDocument(doc, List.of(fieldName), entity, nq.getResultType(), false);
                if (result != null) {
                    return result;
                }
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

    private @Nullable Document singleResult(RecordStream<Document> cursor) {
        Document result = null;
        boolean found = false;
        for (Document doc : cursor) {
            if (found) {
                throw new NonUniqueResultException();
            }
            result = doc;
            found = true;
        }
        return result;
    }

    private Limit resolveTopFirstLimit(String methodName, Limit limit) {
        if (limit.maxResults() > 0) {
            return limit;
        }
        Matcher matcher = TOP_FIRST_PATTERN.matcher(methodName);
        if (matcher.find()) {
            String value = matcher.group(1);
            return Limit.of(value.isEmpty() ? 1 : Integer.parseInt(value), 0);
        }
        return limit;
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
        if (isNumericResultType(nq.getResultType())) {
            String methodName = q.getName();
            String queryStr = nq.getQuery();
            boolean isCountQuery = methodName.startsWith("count") ||
                (nq.getOperationType() != null && nq.getOperationType() == StoredQuery.OperationType.COUNT) ||
                (queryStr != null && queryStr.contains(NitriteQueryOperators.COUNT));
            if (isCountQuery) {
                if (queryStr != null && queryStr.contains(NitriteQueryOperators.GROUP)) {
                    return Collections.singletonList((R) handleDistinctCount(coll, filter, queryStr));
                }
                return Collections.singletonList((R) Long.valueOf(coll.find(filter).size()));
            }
        }

        // Setup sort and limit
        Sort s = nq.getSort();
        Sort parsedS = null;
        String queryString = nq.getQuery();
        if (queryString != null) {
            parsedS = helper.parseSortFromQuery(queryString);
        }
        if ((parsedS == null || !parsedS.isSorted()) && nq.getQueryHints() != null) {
            parsedS = helper.parseSortFromHints(nq.getQueryHints());
        }
        if (parsedS != null && parsedS.isSorted()) {
            if (s == null || !s.isSorted()) {
                s = parsedS;
            } else {
                List<Sort.Order> merged = new ArrayList<>(parsedS.getOrderBy());
                merged.addAll(s.getOrderBy());
                s = Sort.of(merged);
            }
        }
        Limit limit = nq.getQueryLimit();
        if (limit.maxResults() <= 0) {
            String methodName = q.getName();
            Matcher matcher = TOP_FIRST_PATTERN.matcher(methodName);
            if (matcher.find()) {
                String value = matcher.group(1);
                limit = Limit.of(value.isEmpty() ? 1 : Integer.parseInt(value), 0);
            }
        }

        FindOptions findOptions = findOptionsFactory.build(nq.getPageable(), s, entityFactory.apply(q.getRootEntity()));
        if (nq.getPageable().getMode() == Pageable.Mode.OFFSET && limit.maxResults() > 0) {
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
            if (aggFunc != null && fieldName != null) {
                Object result = aggregationHandler.aggregate(docs, persistedField(fieldName, nq), aggFunc);
                return Collections.singletonList(valueConverter.convertWithTemporalHandling(result, nq.getResultType()));
            }
        }

        List<String> projectedFields = getProjectedFields(nq);
        if (Object[].class.equals(nq.getResultType()) && !projectedFields.isEmpty()) {
            var cursor = coll.find(filter, findOptions);
            RuntimePersistentEntity<?> entity = entityFactory.apply(nq.getRootEntity());
            return projectionMapper.mapResults(cursor, projectedFields, entity, nq.getResultType(), false);
        }

        // Handle DTO projection
        if (nq.isDtoProjection()) {
            var cursor = coll.find(filter, findOptions);
            RuntimePersistentEntity<?> entity = entityFactory.apply(nq.getRootEntity());
            List<R> results = new ArrayList<>();
            for (Document doc : cursor) {
                R result = projectionMapper.mapDocument(remapDtoProjectionDocument(doc, nq), Collections.emptyList(), entity, nq.getResultType(), true);
                if (result != null) {
                    results.add(result);
                }
            }
            return results;
        }

        // Handle native single-field projection
        if (!nq.getResultType().equals(nq.getRootEntity()) && !nq.isDtoProjection()) {
            projectedFields = null;
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
                return projectionMapper.mapResults(cursor, projectedFields, entity, nq.getResultType(), false);
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
        Object[] jsonParams = buildJsonParameterValues(nq);
        Map<String, Object> namedParameters = buildNamedParameterValues(nq);

        Map<String, Object> updateOperations = buildUpdateOperations(nq, jsonParams);
        if (updateOperations.isEmpty()) {
            return Optional.of(0);
        }

        Filter filter;
        if (nq.getCompiledFilter() != null) {
            filter = nq.getCompiledFilter().bind(jsonParams, namedParameters);
        } else if (nq.getFilterMap() != null) {
            filter = filterBuilder.buildFilterFromJson(entityFactory.apply(nq.getRootEntity()), nq.getFilterMap(), jsonParams, namedParameters);
        } else {
            filter = buildGeneratedFilter(q, entityFactory.apply(nq.getRootEntity()), jsonParams);
        }

        Filter finalFilter = filter != null ? filter : nq.getNitriteFilter();
        NitriteCollection collection = collectionFactory.apply(nq.getRootEntity());
        RuntimePersistentEntity<?> entity = entityFactory.apply(nq.getRootEntity());
        long count;
        if (requiresDocumentUpdate(updateOperations)) {
            // The new value is derived from the stored one, so the read and the write have to be
            // held together: Nitrite releases its own lock between the two calls.
            count = CollectionUpdateLock.withLock(collection.getName(), () -> {
                long updated = 0;
                for (Document doc : collection.find(finalFilter).toList()) {
                    Document updateDoc = buildUpdateDocument(updateOperations, doc, jsonParams, namedParameters);
                    if (isOptimisticLocking(q, entity)) {
                        applyVersionIncrement(updateDoc, entity, namedParameters);
                    }
                    Object id = doc.get("id");
                    Filter idFilter = id == null ? finalFilter : NitriteFilterUtils.eq("id", id);
                    helper.logUpdate(collection.getName(), idFilter, updateDoc);
                    updated += collection.update(idFilter, updateDoc, UpdateOptions.updateOptions(false)).getAffectedCount();
                }
                return updated;
            });
        } else {
            Document updateDoc = buildUpdateDocument(updateOperations, null, jsonParams, namedParameters);
            if (isOptimisticLocking(q, entity)) {
                applyVersionIncrement(updateDoc, entity, namedParameters);
            }
            helper.logUpdate(collection.getName(), finalFilter, updateDoc);
            count = collection.update(finalFilter, updateDoc, UpdateOptions.updateOptions(false)).getAffectedCount();
        }
        if (count == 0 && !Filter.ALL.equals(finalFilter) && isOptimisticLocking(q, entity)) {
            throw new OptimisticLockException("Execute update returned unexpected row count. Expected: 1 got: 0");
        }
        return Optional.of(count);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> buildUpdateOperations(NitritePreparedQuery<?, Number> nq, Object[] jsonParams) {
        Map<String, Object> updateOperations = new LinkedHashMap<>();
        if (nq.getUpdateMap() != null) {
            updateOperations.putAll(nq.getUpdateMap());
        }
        Map<String, Object> filterMap = nq.getFilterMap();
        if (filterMap != null) {
            for (String operator : List.of(NitriteQueryOperators.SET, NitriteQueryOperators.INC, NitriteQueryOperators.MUL, NitriteQueryOperators.CONCAT)) {
                Object value = filterMap.get(operator);
                if (value instanceof Map<?, ?> map) {
                    updateOperations.put(operator, new LinkedHashMap<>((Map<String, Object>) map));
                }
            }
        }
        if (updateOperations.isEmpty()) {
            Map<String, Object> generatedSet = extractGeneratedSet(nq, jsonParams);
            if (!generatedSet.isEmpty()) {
                return Map.of(NitriteQueryOperators.SET, generatedSet);
            }
        }
        if (updateOperations.keySet().stream().noneMatch(key -> key.startsWith("$"))) {
            return Map.of(NitriteQueryOperators.SET, updateOperations);
        }
        return updateOperations;
    }

    private Map<String, Object> extractGeneratedSet(NitritePreparedQuery<?, Number> nq, Object[] jsonParams) {
        return generatedQueryParser.parseSet(nq.getQuery(), entityFactory.apply(nq.getRootEntity()), jsonParams);
    }

    /**
     * Builds a filter for a generated (SQL-shaped) query that carries no JSON filter map, by
     * parsing its {@code WHERE} clause. Falls back to {@code @Id}/{@code @Version} arguments
     * when the query has no {@code WHERE} clause.
     *
     * @param query the prepared query
     * @param entity the root entity
     * @param jsonParams the positional parameter values
     * @return the built filter, or {@link Filter#ALL} when no predicate can be derived
     */
    public Filter buildGeneratedFilter(PreparedQuery<?, ?> query, RuntimePersistentEntity<?> entity, Object[] jsonParams) {
        Filter parsed = generatedQueryParser.parseWhere(query.getQuery(), entity, jsonParams);
        if (parsed != null) {
            return parsed;
        }

        Argument<?>[] arguments = query.getArguments();
        Object[] values = query.getParameterArray();
        if (arguments == null || values == null) {
            return query instanceof NitritePreparedQuery<?, ?> nitriteQuery ? nitriteQuery.getNitriteFilter() : Filter.ALL;
        }
        List<Filter> filters = new ArrayList<>(2);
        for (int i = 0; i < Math.min(arguments.length, values.length); i++) {
            if (arguments[i].getAnnotationMetadata().hasAnnotation(Id.class)) {
                filters.add(NitriteFilterUtils.eq(NitriteEntityMapper.ID_FIELD, toFilterValue(values[i])));
            } else if (arguments[i].getAnnotationMetadata().hasAnnotation(Version.class) && entity.hasVersion()) {
                filters.add(NitriteFilterUtils.eq(entity.getVersion().getPersistedName(), toFilterValue(values[i])));
            }
        }
        if (filters.isEmpty()) {
            return query instanceof NitritePreparedQuery<?, ?> nitriteQuery ? nitriteQuery.getNitriteFilter() : Filter.ALL;
        }
        return filters.size() == 1 ? filters.getFirst() : Filter.and(filters.toArray(Filter[]::new));
    }

    /**
     * Whether the declared result type is numeric. Primitive result types such as {@code long} are
     * not assignable to {@link Number}, so they are widened to their wrapper first.
     *
     * @param resultType the declared result type
     * @return true if the method returns a number
     */
    private static boolean isNumericResultType(Class<?> resultType) {
        return Number.class.isAssignableFrom(ReflectionUtils.getWrapperType(resultType));
    }

    private boolean requiresDocumentUpdate(Map<String, Object> updateOperations) {
        return updateOperations.containsKey(NitriteQueryOperators.INC)
            || updateOperations.containsKey(NitriteQueryOperators.MUL)
            || updateOperations.containsKey(NitriteQueryOperators.CONCAT);
    }

    @SuppressWarnings("unchecked")
    private Document buildUpdateDocument(Map<String, Object> updateOperations,
                                         @Nullable Document currentDocument,
                                         Object[] jsonParams,
                                         Map<String, Object> namedParameters) {
        Document updateDoc = Document.createDocument();
        Object set = updateOperations.get(NitriteQueryOperators.SET);
        if (set instanceof Map<?, ?> setFields) {
            for (Map.Entry<String, Object> entry : ((Map<String, Object>) setFields).entrySet()) {
                updateDoc.put(entry.getKey(), resolveParameterValue(entry.getValue(), jsonParams, namedParameters));
            }
        }
        // Every derived operator reads the stored value, so they only run on the per-document
        // branch. requiresDocumentUpdate() names exactly those operators, so a null current
        // document here means none of them are present.
        if (currentDocument != null) {
            applyNumericUpdate(updateDoc, currentDocument, updateOperations.get(NitriteQueryOperators.INC),
                jsonParams, namedParameters, NitriteQueryOperators.INC);
            applyNumericUpdate(updateDoc, currentDocument, updateOperations.get(NitriteQueryOperators.MUL),
                jsonParams, namedParameters, NitriteQueryOperators.MUL);
            applyConcatUpdate(updateDoc, currentDocument, updateOperations.get(NitriteQueryOperators.CONCAT),
                jsonParams, namedParameters);
        }
        return updateDoc;
    }

    @SuppressWarnings("unchecked")
    private void applyNumericUpdate(Document updateDoc,
                                    Document currentDocument,
                                    @Nullable Object operation,
                                    Object[] jsonParams,
                                    Map<String, Object> namedParameters,
                                    String operator) {
        if (!(operation instanceof Map<?, ?> fields)) {
            return;
        }
        boolean multiply = NitriteQueryOperators.MUL.equals(operator);
        for (Map.Entry<String, Object> entry : ((Map<String, Object>) fields).entrySet()) {
            Object currentValue = currentDocument.get(entry.getKey());
            Object operand = resolveUpdateOperand(entry.getValue(), jsonParams, namedParameters);
            updateDoc.put(entry.getKey(), NumericUpdateOperations.apply(currentValue, operand, multiply, entry.getKey()));
        }
    }

    @SuppressWarnings("unchecked")
    private void applyConcatUpdate(Document updateDoc,
                                   Document currentDocument,
                                   @Nullable Object operation,
                                   Object[] jsonParams,
                                   Map<String, Object> namedParameters) {
        if (!(operation instanceof Map<?, ?> fields)) {
            return;
        }
        for (Map.Entry<String, Object> entry : ((Map<String, Object>) fields).entrySet()) {
            Object currentValue = currentDocument.get(entry.getKey());
            Object operand = resolveUpdateOperand(entry.getValue(), jsonParams, namedParameters);
            String currentStr = currentValue != null ? String.valueOf(currentValue) : "";
            String appendStr = operand != null ? String.valueOf(operand) : "";
            updateDoc.put(entry.getKey(), currentStr + appendStr);
        }
    }

    private @Nullable Object resolveUpdateOperand(Object value, Object[] jsonParams, Map<String, Object> namedParameters) {
        Object resolved = resolveParameterValue(value, jsonParams, namedParameters);
        if (value instanceof Map<?, ?> map) {
            if (map.containsKey(NitriteQueryOperators.VALUE)) {
                resolved = resolveParameterValue(map.get(NitriteQueryOperators.VALUE), jsonParams, namedParameters);
            }
            if (Boolean.TRUE.equals(map.get(NitriteInternalKeys.NEGATE)) && resolved instanceof Number number) {
                resolved = NumericUpdateOperations.negate(number);
            }
            if (Boolean.TRUE.equals(map.get(NitriteInternalKeys.RECIPROCATE)) && resolved instanceof Number number) {
                resolved = NumericUpdateOperations.reciprocal(number);
            }
        }
        return resolved;
    }

    /**
     * Executes a delete operation based on the provided prepared query.
     * Extracts the pre-compiled Nitrite filter, applies optimistic locking rules if applicable,
     * and performs the removal against the underlying Nitrite collection.
     *
     * @param q the generic prepared query containing execution metadata
     * @param nq the compiled Nitrite prepared query holding the exact filter
     * @return an optional containing the number of deleted records
     * @throws OptimisticLockException if optimistic locking is enabled and no records were deleted
     */
    public Optional<Number> executeDelete(@NonNull PreparedQuery<?, Number> q, NitritePreparedQuery<?, Number> nq) {
        helper.logDelete(collectionFactory.apply(nq.getRootEntity()).getName(), nq.getNitriteFilter());
        long count = collectionFactory.apply(nq.getRootEntity()).remove(nq.getNitriteFilter(), false).getAffectedCount();
        RuntimePersistentEntity<?> entity = entityFactory.apply(nq.getRootEntity());
        if (count == 0 && !Filter.ALL.equals(nq.getNitriteFilter()) && isOptimisticLocking(q, entity)) {
            throw new OptimisticLockException("Execute update returned unexpected row count. Expected: 1 got: 0");
        }
        return Optional.of(count);
    }

    /**
     * Executes a count query to determine the number of matching documents.
     * This operation bypasses normal result projection and mapping entirely to efficiently size
     * the query result directly from the Nitrite cursor. It natively supports {@code COUNT_DISTINCT}
     * by resolving a nested {@code $group} stage if present in the translated JSON string.
     *
     * @param q the generic prepared query
     * @param nq the compiled Nitrite prepared query holding the exact filter
     * @return the total number of documents matching the query filter
     */
    public long count(@NonNull PreparedQuery<?, ?> q, NitritePreparedQuery<?, ?> nq) {
        return collectionFactory.apply(nq.getRootEntity()).find(nq.getNitriteFilter()).size();
    }

    private Document remapDtoProjectionDocument(Document doc, NitritePreparedQuery<?, ?> nq) {
        List<String> projectedFields = getProjectedFields(nq);
        if (projectedFields.isEmpty()) {
            return doc;
        }
        RuntimePersistentEntity<?> rootEntity = entityFactory.apply(nq.getRootEntity());
        RuntimePersistentEntity<?> resultEntity = entityFactory.apply(nq.getResultType());
        Argument<?>[] constructorArguments = resultEntity.getIntrospection().getConstructorArguments();
        if (constructorArguments.length != projectedFields.size()) {
            return doc;
        }

        Document remapped = Document.createDocument();
        for (int i = 0; i < constructorArguments.length; i++) {
            String projectedField = projectedFields.get(i);
            String normalizedField = entityMapper.normalizeFieldName(projectedField, rootEntity);
            Object value = doc.get(normalizedField);
            if (value == null && !normalizedField.equals(projectedField)) {
                value = doc.get(projectedField);
            }
            if (value == null && ("id".equals(projectedField) || "id".equals(normalizedField))) {
                value = doc.get("_id");
            }
            remapped.put(constructorArguments[i].getName(), value);
        }
        return remapped;
    }

    private List<String> getProjectedFields(NitritePreparedQuery<?, ?> nq) {
        List<String> projectedFields = queryParser.extractProjectionFields(nq.getQuery());
        if (!projectedFields.isEmpty()) {
            return projectedFields;
        }
        return nq.getAnnotationMetadata()
            .getAnnotationValuesByType(Projection.class)
            .stream()
            .map(AnnotationValue::stringValue)
            .flatMap(Optional::stream)
            .toList();
    }

    boolean isOptimisticLocking(@NonNull PreparedQuery<?, ?> q, RuntimePersistentEntity<?> entity) {
        if (q.getArguments() != null
            && Arrays.stream(q.getArguments()).anyMatch(arg -> arg.getAnnotationMetadata().hasAnnotation(Version.class))) {
            return true;
        }
        if (!entity.hasVersion()) {
            return false;
        }
        RuntimePersistentProperty<?> version = entity.getVersion();
        if (q.getQueryBindings() != null && q.getQueryBindings().stream().anyMatch(binding -> {
            String[] propertyPath = binding.getPropertyPath();
            if (propertyPath == null || propertyPath.length == 0) {
                return false;
            }
            String property = propertyPath[propertyPath.length - 1];
            return version.getName().equals(property) || version.getPersistedName().equals(property);
        })) {
            return true;
        }
        String queryString = q.getQuery();
        int whereStart = queryString.indexOf(" WHERE ");
        if (whereStart < 0) {
            return false;
        }
        Matcher matcher = GENERATED_EQUALITY_PATTERN.matcher(queryString.substring(whereStart + 7));
        while (matcher.find()) {
            String property = matcher.group(1);
            if (version.getName().equals(property) || version.getPersistedName().equals(property)) {
                return true;
            }
        }
        return false;
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

    /**
     * Interface to build FindOptions from pageable, sort, and entity context.
     */
    @FunctionalInterface
    public interface FindOptionsBuilder {
        /**
         * Build the find options.
         *
         * @param pageable the pageable
         * @param sort the sort
         * @param entity the entity
         * @return the options
         */
        FindOptions build(Pageable pageable, Sort sort, RuntimePersistentEntity<?> entity);
    }
}
