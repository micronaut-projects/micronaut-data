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
package io.micronaut.data.nitrite.runtime;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.type.Argument;
import io.micronaut.data.event.EntityEventListener;
import io.micronaut.data.model.Limit;
import io.micronaut.data.model.Pageable;
import io.micronaut.data.model.Sort;
import io.micronaut.data.model.query.JoinPath;
import io.micronaut.data.model.runtime.PreparedQuery;
import io.micronaut.data.model.runtime.QueryParameterBinding;
import io.micronaut.data.model.runtime.RuntimePersistentEntity;
import io.micronaut.data.model.runtime.RuntimePersistentProperty;
import io.micronaut.data.model.runtime.StoredQuery;
import io.micronaut.data.nitrite.runtime.mapping.NitriteEntityMapper;
import io.micronaut.data.nitrite.runtime.query.NitriteFilterBuilder;
import io.micronaut.data.nitrite.runtime.query.NitritePreparedQuery;
import io.micronaut.data.nitrite.runtime.query.NitriteQueryParser;
import io.micronaut.data.nitrite.runtime.query.NitriteStoredQuery;
import io.micronaut.data.nitrite.runtime.query.NitriteUpdateExecutor;
import org.dizitart.no2.collection.Document;
import org.dizitart.no2.common.RecordStream;
import org.dizitart.no2.collection.FindOptions;
import org.dizitart.no2.collection.NitriteCollection;
import org.dizitart.no2.collection.UpdateOptions;
import org.dizitart.no2.filters.Filter;
import org.dizitart.no2.filters.FluentFilter;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Executor for Nitrite queries (SQL and JSON).
 *
 * @since 4.14.0
 */
@Internal
public final class NitriteQueryExecutor {

    private static final Pattern TOP_FIRST_PATTERN = Pattern.compile("(?:Top|First)(\\d+)");

    private static final Pattern SQL_COMPARISON =
        Pattern.compile("(?:\\w+\\.)?(\\w+)\\s*(=|!=|<>|>|<|>=|<=)\\s*:(\\w+)");
    private static final Pattern SQL_IN_CLAUSE =
        Pattern.compile("(?:\\w+\\.)?(\\w+)\\s+(NOT\\s+)?IN\\s*\\(\\s*:(\\w+)\\s*\\)", Pattern.CASE_INSENSITIVE);
    private static final Pattern SQL_IS_NOT_NULL =
        Pattern.compile("(?:\\w+\\.)?(\\w+)\\s+IS\\s+NOT\\s+NULL");
    private static final Pattern SQL_IS_NULL =
        Pattern.compile("(?:\\w+\\.)?(\\w+)\\s+IS\\s+(?!NOT\\s+)NULL");

    private final NitriteEntityMapper entityMapper;
    private final NitriteQueryParser queryParser;
    private final NitriteFilterBuilder filterBuilder;
    private final NitriteUpdateExecutor updateExecutor;
    private final ConversionService conversionService;
    private final Function<Class<?>, NitriteCollection> collectionFactory;
    private final Function<Class<?>, RuntimePersistentEntity<?>> entityFactory;
    private final BiFunction<Pageable, Sort, FindOptions> findOptionsFactory;
    private final NitriteOperationsHelper helper;
    private final EntityEventListener<Object> entityEventListener;

    // Centralized strategy classes for result handling
    private final ObjectRepositoryMapper entityMapperHandler;
    private final ValueConverter valueConverter;
    private final CollectionProjectionMapper projectionMapper;
    private final CollectionFieldMapper nativeProjectionHandler;
    private final CollectionAggregator aggregationHandler;

    /**
     * Creates a new NitriteQueryExecutor.
     *
     * @param entityMapper the entity mapper
     * @param queryParser the query parser
     * @param filterBuilder the filter builder
     * @param updateExecutor the update executor
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
                                NitriteUpdateExecutor updateExecutor,
                                ConversionService conversionService,
                                Function<Class<?>, NitriteCollection> collectionFactory,
                                Function<Class<?>, RuntimePersistentEntity<?>> entityFactory,
                                BiFunction<Pageable, Sort, FindOptions> findOptionsFactory,
                                NitriteOperationsHelper helper,
                                EntityEventListener<Object> entityEventListener) {
        this.entityMapper = entityMapper;
        this.queryParser = queryParser;
        this.filterBuilder = filterBuilder;
        this.updateExecutor = updateExecutor;
        this.conversionService = conversionService;
        this.collectionFactory = collectionFactory;
        this.entityFactory = entityFactory;
        this.findOptionsFactory = findOptionsFactory;
        this.helper = helper;
        this.entityEventListener = entityEventListener;

        // Initialize centralized strategy classes
        this.valueConverter = new ValueConverter(conversionService);
        this.entityMapperHandler = new ObjectRepositoryMapper(entityMapper);
        this.projectionMapper = new CollectionProjectionMapper(valueConverter, entityMapper);
        this.nativeProjectionHandler = new CollectionFieldMapper(queryParser, valueConverter);
        this.aggregationHandler = new CollectionAggregator();
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
        String query = nq.getQuery();
        boolean isUpdate = nq.getUpdateMap() != null
            || (query != null && query.trim().regionMatches(true, 0, "UPDATE", 0, 6));
        if (isUpdate) {
            Optional<Number> result = executeUpdate((PreparedQuery<?, Number>) nq, (NitritePreparedQuery<?, Number>) nq);
            return Number.class.isAssignableFrom(nq.getResultType()) ? (R) result.orElse(0L) : null;
        }

        Filter filter = nq.getNitriteFilter();
        helper.logFind(coll.getName(), filter);

        // Handle count queries
        if (Number.class.isAssignableFrom(nq.getResultType())) {
            String methodName = q.getName();
            boolean isCountQuery = methodName.startsWith("count") ||
                (nq.getOperationType() != null && nq.getOperationType() == StoredQuery.OperationType.COUNT);
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
            return (R) valueConverter.convertWithTemporalHandling(result, nq.getResultType());
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
            R result = nativeProjectionHandler.project(doc, query, methodName, nq.getResultType());
            if (result != null) {
                return result;
            }
        }

        // Handle full entity load
        R entity = (R) entityMapperHandler.loadEntity(doc, nq.getRootEntity());

        // Fetch joined associations if specified
        Set<JoinPath> joinPaths = nq.getJoinPaths();
        if (joinPaths != null && !joinPaths.isEmpty()) {
            fetchJoins(Collections.singletonList(entity), joinPaths, nq.getRootEntity());
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
            boolean isCountQuery = methodName.startsWith("count") ||
                (nq.getOperationType() != null && nq.getOperationType() == StoredQuery.OperationType.COUNT);
            if (isCountQuery) {
                return Collections.singletonList((R) Long.valueOf(coll.find(filter).size()));
            }
        }

        // Setup sort and limit
        Sort s = nq.getSort();
        if (s == null || !s.isSorted()) {
            String query = nq.getQuery();
            if (query != null) {
                if (nq.isSql()) {
                    s = helper.parseSortFromSqlQuery(query);
                } else {
                    s = helper.parseSortFromJsonQuery(query);
                }
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
            findOptions.skip((long) limit.offset());
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
            return Collections.singletonList((R) valueConverter.convertWithTemporalHandling(result, nq.getResultType()));
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
            List<String> projectedFields = queryParser.parseSelectClause(query);
            if (projectedFields == null || projectedFields.isEmpty()) {
                String projectField = queryParser.extractProjectionField(query);
                if (projectField != null) {
                    projectedFields = Collections.singletonList(projectField);
                }
            }
            if (projectedFields == null || projectedFields.isEmpty()) {
                String fieldName = nativeProjectionHandler.extractFieldName(query, methodName);
                if (fieldName != null) {
                    projectedFields = Collections.singletonList(fieldName);
                }
            }

            if (projectedFields != null && !projectedFields.isEmpty()) {
                var cursor = coll.find(filter, findOptions);
                if (projectedFields.size() == 1) {
                    List<R> results = new ArrayList<>();
                    String field = projectedFields.get(0);
                    for (Document doc : cursor) {
                        R result = nativeProjectionHandler.project(doc, field, nq.getResultType());
                        if (result != null) {
                            results.add(result);
                        }
                    }
                    return results;
                } else {
                    // Multi-field projection - use centralized projection mapper
                    Document projection = Document.createDocument();
                    for (String field : projectedFields) {
                        projection.put(field, null);
                    }
                    RecordStream<Document> projectedCursor = cursor.project(projection);
                    return projectionMapper.mapResults(projectedCursor, projectedFields, nq.getResultType(), nq.isDtoProjection());
                }
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
            fetchJoins(results, joinPaths, nq.getRootEntity());
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
        Map<String, Object> setFields = null;
        Filter filter = null;
        Object[] jsonParams = buildJsonParameterValues(nq);
        Map<String, Object> namedParameters = buildNamedParameterValues(nq);
        if (nq.getFilterMap() != null) {
            Map<String, Object> rawSetFields = (Map<String, Object>) nq.getFilterMap().get("$set");
            if (rawSetFields != null) {
                setFields = new LinkedHashMap<>();
                for (Map.Entry<String, Object> entry : rawSetFields.entrySet()) {
                    setFields.put(entry.getKey(), resolveParameterValue(entry.getValue(), jsonParams, namedParameters));
                }
            }
            if (nq.getCompiledFilter() != null) {
                filter = nq.getCompiledFilter().bind(jsonParams, namedParameters);
            } else {
                filter = filterBuilder.buildFilterFromJson(entityFactory.apply(nq.getRootEntity()), nq.getFilterMap(), jsonParams, namedParameters);
            }
        } else if (nq.getQuery().trim().toUpperCase().startsWith("UPDATE")) {
            Object[] sqlParams = reorderParamsForSql(nq);
            setFields = updateExecutor.parseSetClause(nq.getQuery(), sqlParams, (pname, ps) -> toFilterValue(resolveSqlParam(pname, ps, namedParameters)));
            filter = parseFilterFromUpdateStatement(nq.getQuery(), sqlParams, namedParameters);
        }
        if (setFields == null || setFields.isEmpty()) {
            return Optional.of(0);
        }
        Document updateDoc = Document.createDocument();
        for (Map.Entry<String, Object> entry : setFields.entrySet()) {
            updateDoc.put(entry.getKey(), entry.getValue());
        }
        Filter finalFilter = filter != null ? filter : nq.getNitriteFilter();
        helper.logUpdate(collectionFactory.apply(nq.getRootEntity()).getName(), finalFilter, updateDoc);
        return Optional.of(collectionFactory.apply(nq.getRootEntity()).update(finalFilter, updateDoc, UpdateOptions.updateOptions(false)).getAffectedCount());
    }

    /**
     * Executes a delete query.
     *
     * @param q the prepared query
     * @param nq the nitrite prepared query
     * @return optional containing the number of deleted entities
     */
    public Optional<Number> executeDelete(@NonNull PreparedQuery<?, Number> q, NitritePreparedQuery<?, Number> nq) {
        NitriteCollection coll = collectionFactory.apply(nq.getRootEntity());
        return Optional.of(coll.remove(nq.getNitriteFilter()).getAffectedCount());
    }

    /**
     * Builds a map of named parameter values from a prepared query.
     *
     * @param q the prepared query
     * @return the map of named parameter values
     */
    public Map<String, Object> buildNamedParameterValues(@NonNull final PreparedQuery<?, ?> q) {
        Object[] params = q.getParameterArray();
        if (params == null || params.length == 0) {
            return Collections.emptyMap();
        }
        Map<String, Object> result = new HashMap<>();
        List<QueryParameterBinding> bindings = q.getQueryBindings();
        if (bindings != null) {
            for (QueryParameterBinding b : bindings) {
                if (b.getName() != null && b.getParameterIndex() >= 0 && b.getParameterIndex() < params.length) {
                    result.put(b.getName(), toFilterValue(params[b.getParameterIndex()]));
                }
            }
        }
        Argument[] args = q.getArguments();
        if (args != null) {
            int len = Math.min(args.length, params.length);
            for (int i = 0; i < len; i++) {
                if (args[i].getName() != null && !args[i].getName().isEmpty()) {
                    result.putIfAbsent(args[i].getName(), toFilterValue(params[i]));
                }
            }
        }
        return result;
    }

    /**
     * Builds an array of JSON parameter values from a prepared query.
     *
     * @param q the prepared query
     * @return the array of JSON parameter values
     */
    public Object[] buildJsonParameterValues(@NonNull final PreparedQuery<?, ?> q) {
        Object[] params = q.getParameterArray();
        if (params == null || params.length == 0) {
            return new Object[0];
        }
        List<QueryParameterBinding> bindings = q.getQueryBindings();
        if (bindings == null || bindings.isEmpty()) {
            return params;
        }
        Object[] values = new Object[bindings.size()];
        for (int i = 0; i < bindings.size(); i++) {
            QueryParameterBinding b = bindings.get(i);
            if (b.getParameterIndex() >= 0 && b.getParameterIndex() < params.length) {
                values[i] = toFilterValue(params[b.getParameterIndex()]);
            }
        }
        return values;
    }

    /**
     * Builds a filter from a prepared query.
     *
     * @param q the prepared query
     * @param stored the stored query
     * @return the built filter
     */
    public Filter buildFilterFromPreparedQuery(final PreparedQuery<?, ?> q, NitriteStoredQuery<?, ?> stored) {
        Map<String, Object> namedParameters = buildNamedParameterValues(q);
        if (stored.getCompiledFilter() != null) {
            return stored.getCompiledFilter().bind(ensureJsonParamsForFilter(stored.getFilterMap(), q.getParameterArray(), buildJsonParameterValues(q)), namedParameters);
        }
        if (stored.getFilterMap() != null) {
            return filterBuilder.buildFilterFromJson(entityFactory.apply(stored.getRootEntity()), stored.getFilterMap(), ensureJsonParamsForFilter(stored.getFilterMap(), q.getParameterArray(), buildJsonParameterValues(q)), namedParameters);
        }
        String queryString = q.getQuery().trim();
        if (queryString.isEmpty()) {
            return Filter.ALL;
        }
        String upper = queryString.toUpperCase();
        if (upper.startsWith("DELETE")) {
            return parseFilterFromDeleteStatement(queryString, q.getParameterArray(), namedParameters);
        }
        if (upper.startsWith("SELECT")) {
            return parseFilterFromSelectStatement(queryString, q.getParameterArray(), namedParameters);
        }
        if (upper.startsWith("UPDATE")) {
            return parseFilterFromUpdateStatement(queryString, reorderParamsForSql(q), namedParameters);
        }
        throw new IllegalStateException("Unsupported query format: " + queryString);
    }

    /**
     * Parses a filter from a DELETE statement.
     *
     * @param sql the SQL statement
     * @param params the query parameters
     * @param namedParameters the named parameters
     * @return the parsed filter
     */
    public Filter parseFilterFromDeleteStatement(final String sql, final Object[] params, final Map<String, Object> namedParameters) {
        int whereIdx = sql.toUpperCase().indexOf(" WHERE ");
        if (whereIdx < 0) {
            return Filter.ALL;
        }
        String where = sql.substring(whereIdx + 7);
        int orderByIdx = where.toUpperCase().indexOf(" ORDER BY");
        return parseWhereClause(orderByIdx >= 0 ? where.substring(0, orderByIdx) : where, params, namedParameters);
    }

    /**
     * Parses a filter from a SELECT statement.
     *
     * @param sql the SQL statement
     * @param params the query parameters
     * @param namedParameters the named parameters
     * @return the parsed filter
     */
    public Filter parseFilterFromSelectStatement(final String sql, final Object[] params, final Map<String, Object> namedParameters) {
        int whereIdx = sql.toUpperCase().indexOf(" WHERE ");
        if (whereIdx < 0) {
            return Filter.ALL;
        }
        String where = sql.substring(whereIdx + 7);
        int orderByIdx = where.toUpperCase().indexOf(" ORDER BY");
        String w = (orderByIdx >= 0 ? where.substring(0, orderByIdx) : where).trim();
        return parseWhereClause(w.startsWith("(") && w.endsWith(")") ? w.substring(1, w.length() - 1) : w, params, namedParameters);
    }

    private Object resolveParameterValue(Object value, Object[] jsonParams, Map<String, Object> namedParameters) {
        if (value instanceof String s) {
            Object resolved = null;
            boolean isPlaceholder = false;
            if (s.startsWith("$mn_qp:")) {
                isPlaceholder = true;
                try {
                    int idx = Integer.parseInt(s.substring(7));
                    if (jsonParams != null && idx >= 0 && idx < jsonParams.length) {
                        resolved = jsonParams[idx];
                    }
                } catch (Exception ignored) {
                }
            } else if (s.startsWith(":")) {
                isPlaceholder = true;
                String pname = s.substring(1);
                if (namedParameters.containsKey(pname)) {
                    resolved = namedParameters.get(pname);
                }
            }
            if (isPlaceholder) {
                return toFilterValue(resolved);
            }
        }
        if (value instanceof Map vm && vm.get("$mn_qp") instanceof Integer idx && idx >= 0 && idx < jsonParams.length) {
            return toFilterValue(jsonParams[idx]);
        }
        return value;
    }

    private Object[] reorderParamsForSql(final PreparedQuery<?, ?> q) {
        Object[] raw = q.getParameterArray();
        List<QueryParameterBinding> bindings = q.getQueryBindings();
        if (bindings == null || bindings.isEmpty() || raw == null) {
            return raw;
        }
        Object[] reordered = new Object[bindings.size()];
        for (QueryParameterBinding b : bindings) {
            if (b.getName() != null && b.getName().startsWith("p")) {
                try {
                    int pos = Integer.parseInt(b.getName().substring(1)) - 1;
                    if (pos >= 0 && pos < reordered.length && b.getParameterIndex() >= 0 && b.getParameterIndex() < raw.length) {
                        reordered[pos] = raw[b.getParameterIndex()];
                    }
                } catch (NumberFormatException ignored) {
                }
            }
        }
        return reordered;
    }

    /**
     * Parses a filter from an UPDATE statement.
     *
     * @param sql the SQL statement
     * @param params the query parameters
     * @param namedParameters the named parameters
     * @return the parsed filter
     */
    public Filter parseFilterFromUpdateStatement(final String sql, final Object[] params, final Map<String, Object> namedParameters) {
        int whereIdx = sql.toUpperCase().indexOf(" WHERE ");
        if (whereIdx < 0) {
            return Filter.ALL;
        }
        String w = sql.substring(whereIdx + 7).trim();
        String clause = w.startsWith("(") && w.endsWith(")") ? w.substring(1, w.length() - 1) : w;
        return parseWhereClause(clause, params, namedParameters);
    }

    /**
     * Parses a WHERE clause into a filter.
     *
     * @param where the WHERE clause
     * @param params the query parameters
     * @param namedParameters the named parameters
     * @return the parsed filter
     */
    public Filter parseWhereClause(String where, final Object[] params, final Map<String, Object> namedParameters) {
        List<Filter> filters = new ArrayList<>();
        String emptyPat = "(?:\\w+\\.)?(\\w+)\\s+IS\\s+NULL\\s+OR\\s+(?:\\w+\\.)?\\1\\s*=\\s*''";
        Matcher mEmpty = Pattern.compile("\\(" + emptyPat + "\\)", Pattern.CASE_INSENSITIVE).matcher(where);
        if (!mEmpty.find()) {
            mEmpty = Pattern.compile(emptyPat, Pattern.CASE_INSENSITIVE).matcher(where);
        }
        mEmpty.reset();
        while (mEmpty.find()) {
            filters.add(filterBuilder.buildFieldFilter(null, entityMapper.normalizeFieldName(mEmpty.group(1)), Collections.singletonMap("$empty", true), params, namedParameters));
            where = where.substring(0, mEmpty.start()) + "PROCESSED" + where.substring(mEmpty.end());
            mEmpty = Pattern.compile(emptyPat, Pattern.CASE_INSENSITIVE).matcher(where);
        }
        Matcher m = SQL_COMPARISON.matcher(where);
        while (m.find()) {
            String op = m.group(2);
            String filterOp = switch (op) {
                case "=" -> "$eq";
                case "!=", "<>" -> "$ne";
                case ">" -> "$gt";
                case ">=" -> "$gte";
                case "<" -> "$lt";
                case "<=" -> "$lte";
                default -> "$eq";
            };
            filters.add(filterBuilder.buildFieldFilter(null, entityMapper.normalizeFieldName(m.group(1)), Collections.singletonMap(filterOp, toFilterValue(resolveSqlParam(m.group(3), params, namedParameters))), params, namedParameters));
        }
        m = SQL_IS_NOT_NULL.matcher(where);
        while (m.find()) {
            filters.add(filterBuilder.buildFieldFilter(null, entityMapper.normalizeFieldName(m.group(1)), Collections.singletonMap("$notNull", true), params, namedParameters));
        }
        m = SQL_IS_NULL.matcher(where);
        while (m.find()) {
            filters.add(filterBuilder.buildFieldFilter(null, entityMapper.normalizeFieldName(m.group(1)), Collections.singletonMap("$null", true), params, namedParameters));
        }
        Matcher inMatcher = SQL_IN_CLAUSE.matcher(where);
        while (inMatcher.find()) {
            String fieldName = entityMapper.normalizeFieldName(inMatcher.group(1));
            boolean notIn = inMatcher.group(2) != null;
            String paramName = inMatcher.group(3);
            Object paramValue = namedParameters != null && namedParameters.containsKey(paramName) ? namedParameters.get(paramName) : resolveParam(":" + paramName, params);
            filters.add(filterBuilder.buildFieldFilter(null, fieldName, Collections.singletonMap(notIn ? "$nin" : "$in", paramValue), params, namedParameters));
        }
        return filters.isEmpty() ? Filter.ALL : filters.size() == 1 ? filters.get(0) : Filter.and(filters.toArray(new Filter[0]));
    }

    private Object resolveSqlParam(final String pname, final Object[] params, final Map<String, Object> namedParameters) {
        if (namedParameters != null && namedParameters.containsKey(pname)) {
            return namedParameters.get(pname);
        }
        return resolveParam(pname, params);
    }

    private Object resolveParam(final String pname, final Object[] params) {
        try {
            if (pname.startsWith("p")) {
                int idx = Integer.parseInt(pname.substring(1)) - 1;
                if (params != null && idx >= 0 && idx < params.length) {
                    return params[idx];
                }
            }
        } catch (NumberFormatException ignored) {
        }
        return null;
    }

    private Object[] ensureJsonParamsForFilter(final Map<String, Object> filterMap, final Object[] methodParams, final Object[] jsonParams) {
        int max = findMaxPlaceholderIndex(filterMap);
        if (max < 0) {
            return jsonParams;
        }
        Object[] out = new Object[Math.max(max + 1, jsonParams.length)];
        System.arraycopy(jsonParams, 0, out, 0, jsonParams.length);
        fillMissingParamsFromFilter(filterMap, methodParams, out);
        return out;
    }

    private int findMaxPlaceholderIndex(final Map<String, Object> filterMap) {
        int max = -1;
        for (Object v : filterMap.values()) {
            if (v instanceof Map m) {
                max = Math.max(max, findMaxPlaceholderIndex(m));
            } else {
                Integer idx = extractPlaceholderIndex(v);
                if (idx != null) {
                    max = Math.max(max, idx);
                }
            }
        }
        return max;
    }

    private Integer extractPlaceholderIndex(final Object value) {
        if (value instanceof String s && s.startsWith("$mn_qp:")) {
            try {
                return Integer.parseInt(s.substring(7));
            } catch (Exception ignored) {
            }
        }
        if (value instanceof Map m && m.get("$mn_qp") instanceof Integer idx) {
            return idx;
        }
        return null;
    }

    private void fillMissingParamsFromFilter(final Map<String, Object> filterMap, final Object[] methodParams, final Object[] out) {
        for (Map.Entry<String, Object> entry : filterMap.entrySet()) {
            if (entry.getValue() instanceof Map m) {
                fillMissingParamsFromFilter(m, methodParams, out);
            } else {
                Integer idx = extractPlaceholderIndex(entry.getValue());
                if (idx != null && idx >= 0 && idx < out.length && out[idx] == null) {
                    out[idx] = toFilterValue(extractPropertyFromSingleArg(methodParams, entry.getKey()));
                }
            }
        }
    }

    private Object extractPropertyFromSingleArg(final Object[] methodParams, final String property) {
        if (methodParams == null || methodParams.length != 1 || methodParams[0] == null) {
            return null;
        }
        Object arg = methodParams[0];
        try {
            return io.micronaut.core.beans.BeanIntrospection.getIntrospection(arg.getClass())
                .getProperty(property).map(p -> ((io.micronaut.core.beans.BeanProperty) p).get(arg)).orElse(null);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Fetch and populate joined associations for loaded entities.
     * For ONE_TO_MANY and MANY_TO_MANY associations, queries the associated collection
     * and populates the results on the parent entities.
     *
     * @param entities the loaded entities
     * @param joinPaths the join paths to fetch
     * @param entityType the entity type
     */
    private <R> void fetchJoins(List<R> entities, Set<JoinPath> joinPaths, Class<?> entityType) {
        if (entities == null || entities.isEmpty() || joinPaths == null || joinPaths.isEmpty()) {
            return;
        }

        RuntimePersistentEntity<?> persistentEntity = entityFactory.apply(entityType);

        for (JoinPath joinPath : joinPaths) {
            String path = joinPath.getPath();
            // For now, only handle single-level joins (e.g., "books")
            if (!path.contains(".")) {
                fetchSingleLevelJoin(entities, persistentEntity, path);
            }
            // Nested joins (e.g., "books.pages") would require recursive handling
        }
    }

    /**
     * Fetch a single-level join association.
     *
     * @param entities the parent entities
     * @param persistentEntity the parent entity metadata
     * @param associationName the association name to fetch
     */
    private void fetchSingleLevelJoin(List<?> entities, RuntimePersistentEntity<?> persistentEntity, String associationName) {
        // Find the association property
        var assocProp = persistentEntity.getPropertyByName(associationName);
        if (!(assocProp instanceof io.micronaut.data.model.runtime.RuntimeAssociation<?> association)) {
            return;
        }

        // Only handle ONE_TO_MANY and MANY_TO_MANY (mappedBy associations)
        var kind = association.getKind();
        if (kind != io.micronaut.data.annotation.Relation.Kind.ONE_TO_MANY &&
            kind != io.micronaut.data.annotation.Relation.Kind.MANY_TO_MANY) {
            return;
        }

        String mappedBy = association.getAnnotationMetadata()
            .stringValue(io.micronaut.data.annotation.Relation.class, "mappedBy").orElse(null);
        if (mappedBy == null) {
            return;
        }

        // Get parent IDs
        RuntimePersistentProperty<?> idProp = persistentEntity.getIdentity();
        if (idProp == null) {
            return;
        }

        List<Object> parentIds = new ArrayList<>();
        for (Object entity : entities) {
            Object idValue = ((io.micronaut.core.beans.BeanProperty<Object, Object>) idProp.getProperty()).get(entity);
            if (idValue != null) {
                parentIds.add(entityMapper.toFilterValue(idValue));
            }
        }

        if (parentIds.isEmpty()) {
            return;
        }

        // Query associated entities
        Class<?> associatedType = association.getAssociatedEntity().getIntrospection().getBeanType();
        NitriteCollection assocCollection = collectionFactory.apply(associatedType);
        RuntimePersistentEntity<?> associatedEntity = association.getAssociatedEntity();
        RuntimePersistentProperty<?> backProp = associatedEntity.getPropertyByName(mappedBy);

        if (backProp == null) {
            return;
        }

        String backFieldName = backProp.getPersistedName();
        if (associatedEntity.getIdentity() != null && associatedEntity.getIdentity().equals(backProp)) {
            backFieldName = "id";
        }
        final String finalBackFieldName = backFieldName;

        // Build filter: backFieldName contains any of parentIds
        Filter filter;
        Comparable<?>[] comparableIds = parentIds.stream()
            .map(id -> id instanceof Comparable<?> ? (Comparable<?>) id : id.toString())
            .toArray(Comparable<?>[]::new);

        if (kind == io.micronaut.data.annotation.Relation.Kind.MANY_TO_MANY) {
            // For MANY_TO_MANY, backFieldName is a collection in the document.
            // Standard Nitrite filters like 'eq' or 'in' might not consistently handle 'contains'
            // for non-indexed collection fields in all versions. A custom filter is more reliable.
            // In Nitrite 4.x, Filter receives a Pair<NitriteId, Document>.
            filter = pair -> {
                org.dizitart.no2.collection.Document doc = pair.getSecond();
                Object val = doc.get(finalBackFieldName);
                if (val instanceof Collection<?> coll) {
                    for (Object id : parentIds) {
                        if (coll.contains(id)) {
                            return true;
                        }
                    }
                }
                return false;
            };
        } else {
            // ONE_TO_MANY: backFieldName is a scalar
            if (parentIds.size() == 1) {
                filter = FluentFilter.where(finalBackFieldName).eq(parentIds.get(0));
            } else {
                filter = FluentFilter.where(finalBackFieldName).in(comparableIds);
            }
        }

        // Group results by parent ID
        // For ONE_TO_MANY: backRefValue is a scalar (single parent ID)
        // For MANY_TO_MANY: backRefValue is a collection of parent IDs
        Map<Object, List<Object>> resultsByParentId = new HashMap<>();
        for (Document doc : assocCollection.find(filter)) {
            Object backRefValue = doc.get(finalBackFieldName);
            if (backRefValue != null) {
                Object assocEntity = entityMapper.fromDocument(doc, associatedType);

                // Handle MANY_TO_MANY where backRefValue is a collection
                if (backRefValue instanceof Collection<?> collection) {
                    for (Object parentId : collection) {
                        Object filterParentId = entityMapper.toFilterValue(parentId);
                        if (!resultsByParentId.containsKey(filterParentId)) {
                            resultsByParentId.put(filterParentId, new ArrayList<>());
                        }
                        resultsByParentId.get(filterParentId).add(assocEntity);
                    }
                } else {
                    // ONE_TO_MANY: backRefValue is a scalar
                    Object filterParentId = entityMapper.toFilterValue(backRefValue);
                    if (!resultsByParentId.containsKey(filterParentId)) {
                        resultsByParentId.put(filterParentId, new ArrayList<>());
                    }
                    resultsByParentId.get(filterParentId).add(assocEntity);
                }
            }
        }

        // Set associations on parent entities
        var beanProperty = (io.micronaut.core.beans.BeanProperty<Object, Object>) association.getProperty();
        for (Object entity : entities) {
            Object parentId = ((io.micronaut.core.beans.BeanProperty<Object, Object>) idProp.getProperty()).get(entity);
            if (parentId != null) {
                Object filterParentId = entityMapper.toFilterValue(parentId);
                List<Object> children = resultsByParentId.get(filterParentId);
                if (children != null) {
                    beanProperty.set(entity, conversionService.convert(children, beanProperty.asArgument()).orElse(null));
                }
            }
        }
    }
}
