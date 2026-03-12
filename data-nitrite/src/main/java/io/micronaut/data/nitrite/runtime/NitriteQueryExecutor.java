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
import io.micronaut.data.model.runtime.PreparedQuery;
import io.micronaut.data.model.runtime.QueryParameterBinding;
import io.micronaut.data.model.runtime.RuntimePersistentEntity;
import io.micronaut.data.model.runtime.StoredQuery;
import io.micronaut.data.nitrite.runtime.mapping.NitriteEntityMapper;
import io.micronaut.data.nitrite.runtime.query.NitriteFilterBuilder;
import io.micronaut.data.nitrite.runtime.query.NitritePreparedQuery;
import io.micronaut.data.nitrite.runtime.query.NitriteQueryParser;
import io.micronaut.data.nitrite.runtime.query.NitriteStoredQuery;
import io.micronaut.data.nitrite.runtime.query.NitriteUpdateExecutor;
import io.micronaut.data.runtime.event.DefaultEntityEventContext;
import org.dizitart.no2.collection.Document;
import org.dizitart.no2.common.RecordStream;
import org.dizitart.no2.collection.FindOptions;
import org.dizitart.no2.collection.NitriteCollection;
import org.dizitart.no2.collection.UpdateOptions;
import org.dizitart.no2.filters.Filter;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
    }

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
        if (value instanceof Instant instant) {
            return instant.toString();
        }
        if (value instanceof LocalDate localDate) {
            return localDate.toString();
        }
        if (value instanceof LocalDateTime localDateTime) {
            return localDateTime.toString();
        }
        if (value instanceof LocalTime localTime) {
            return localTime.toString();
        }
        return entityMapper.toFilterValue(value);
    }

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

        if (Number.class.isAssignableFrom(nq.getResultType())) {
            String methodName = q.getName();
            boolean isCountQuery = methodName.startsWith("count") ||
                (nq.getOperationType() != null && nq.getOperationType() == StoredQuery.OperationType.COUNT);
            if (isCountQuery) {
                return (R) Long.valueOf(coll.find(filter).size());
            }
        }

        boolean isProjection = !nq.getResultType().equals(nq.getRootEntity());
        if (isProjection) {
            List<String> projectedFields = queryParser.parseSelectClause(nq.getQuery());
            if (projectedFields == null || projectedFields.isEmpty()) {
                String projectField = queryParser.extractProjectionField(nq.getQuery());
                if (projectField != null) {
                    projectedFields = Collections.singletonList(projectField);
                }
            }

            if (projectedFields == null || projectedFields.isEmpty()) {
                String methodName = q.getName();
                java.util.regex.Pattern aggPattern = java.util.regex.Pattern.compile("^(?:find|get|read)(Max|Min|Sum|Avg)([A-Z][a-zA-Z0-9]*)By");
                java.util.regex.Matcher aggMatcher = aggPattern.matcher(methodName);
                if (aggMatcher.find()) {
                    String aggFunc = aggMatcher.group(1);
                    String fieldName = aggMatcher.group(2);
                    fieldName = Character.toLowerCase(fieldName.charAt(0)) + fieldName.substring(1);
                    List<Document> docs = coll.find(filter).toList();
                    if (docs.isEmpty()) {
                        return null;
                    }
                    List<Object> values = new ArrayList<>();
                    for (Document doc : docs) {
                        Object val = doc.get(fieldName);
                        if (val != null) {
                            values.add(val);
                        }
                    }
                    if (values.isEmpty()) {
                        return null;
                    }
                    Object result = executeAggregate(aggFunc, values);
                    return (R) convertValue(result, nq.getResultType());
                }

                if (!methodName.matches("^(find|get|read)(Count).*")) {
                    java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("^(?:find|get|read)([A-Z][a-z0-9]+)By");
                    java.util.regex.Matcher matcher = pattern.matcher(methodName);
                    if (matcher.find()) {
                        String fieldName = matcher.group(1);
                        fieldName = Character.toLowerCase(fieldName.charAt(0)) + fieldName.substring(1);
                        projectedFields = Collections.singletonList(fieldName);
                    }
                }
            }

            if (projectedFields != null && projectedFields.size() == 1) {
                Document doc = coll.find(filter).firstOrNull();
                if (doc == null) {
                    return null;
                }
                Object value = doc.get(projectedFields.get(0));
                return (R) convertValue(value, nq.getResultType());
            }
        }

        Document doc = coll.find(filter).firstOrNull();
        if (doc == null) {
            return null;
        }
        // postLoad event is triggered by entityMapper.fromDocument() for all entities
        return (R) entityMapper.fromDocument(doc, nq.getRootEntity());
    }

    private Object executeAggregate(String aggFunc, List<Object> values) {
        if (values.get(0) instanceof Number) {
            List<Number> numValues = values.stream().map(v -> (Number) v).toList();
            return switch (aggFunc) {
                case "Max" -> numValues.stream().mapToDouble(Number::doubleValue).max().orElse(0);
                case "Min" -> numValues.stream().mapToDouble(Number::doubleValue).min().orElse(0);
                case "Sum" -> numValues.stream().mapToDouble(Number::doubleValue).sum();
                case "Avg" -> numValues.stream().mapToDouble(Number::doubleValue).average().orElse(0);
                default -> 0;
            };
        } else if (values.get(0) instanceof Comparable) {
            if (aggFunc.equals("Max")) {
                return values.stream().max((a, b) -> ((Comparable) a).compareTo(b)).orElse(null);
            } else if (aggFunc.equals("Min")) {
                return values.stream().min((a, b) -> ((Comparable) a).compareTo(b)).orElse(null);
            }
        }
        return null;
    }

    public <T, R> Iterable<R> findAll(@NonNull PreparedQuery<T, R> q, NitritePreparedQuery<T, R> nq) {
        NitriteCollection coll = collectionFactory.apply(nq.getRootEntity());
        Filter filter = nq.getNitriteFilter();
        helper.logFind(coll.getName(), filter);

        if (Number.class.isAssignableFrom(nq.getResultType())) {
            String methodName = q.getName();
            boolean isCountQuery = methodName.startsWith("count") ||
                (nq.getOperationType() != null && nq.getOperationType() == StoredQuery.OperationType.COUNT);
            if (isCountQuery) {
                return Collections.singletonList((R) Long.valueOf(coll.find(filter).size()));
            }
        }
        Sort s = nq.getSort();
        // Parse sort from SQL/JSON if not already set
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
            java.util.regex.Pattern topPattern = java.util.regex.Pattern.compile("(?:Top|First)(\\d+)");
            java.util.regex.Matcher matcher = topPattern.matcher(methodName);
            if (matcher.find()) {
                limit = Limit.of(Integer.parseInt(matcher.group(1)), 0);
            }
        }

        List<String> projectedFields = null;
        boolean isProjection = !nq.getResultType().equals(nq.getRootEntity());
        if (isProjection) {
            projectedFields = queryParser.parseSelectClause(nq.getQuery());
            if (projectedFields == null || projectedFields.isEmpty()) {
                String projectField = queryParser.extractProjectionField(nq.getQuery());
                if (projectField != null) {
                    projectedFields = Collections.singletonList(projectField);
                }
            }
            if (projectedFields == null || projectedFields.isEmpty()) {
                String methodName = q.getName();
                if (!methodName.matches("^(find|get|read)(Max|Min|Sum|Avg|Count).*")) {
                    java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("^(?:find|get|read)([A-Z][a-z0-9]+)By");
                    java.util.regex.Matcher matcher = pattern.matcher(methodName);
                    if (matcher.find()) {
                        String fieldName = matcher.group(1);
                        fieldName = Character.toLowerCase(fieldName.charAt(0)) + fieldName.substring(1);
                        projectedFields = Collections.singletonList(fieldName);
                    }
                }
            }
        }

        FindOptions findOptions = findOptionsFactory.apply(nq.getPageable(), s);
        if (limit.maxResults() > 0) {
            findOptions.limit((long) limit.maxResults());
            findOptions.skip((long) limit.offset());
        }

        var cursor = coll.find(filter, findOptions);

        if (projectedFields != null && !projectedFields.isEmpty()) {
            Document projection = Document.createDocument();
            for (String field : projectedFields) {
                projection.put(field, null);
            }
            RecordStream<Document> projectedCursor = cursor.project(projection);
            return extractProjectedResults(projectedCursor, projectedFields, nq.getResultType());
        }

        List<R> results = new ArrayList<>();
        for (Document doc : cursor) {
            // postLoad event is triggered by entityMapper.fromDocument() for all entities
            R entity = (R) entityMapper.fromDocument(doc, nq.getRootEntity());
            results.add(entity);
        }
        return results;
    }

    private <R> List<R> extractProjectedResults(RecordStream<Document> cursor, List<String> fields, Class<R> resultType) {
        List<R> results = new ArrayList<>();
        for (Document doc : cursor) {
            if (fields.size() == 1) {
                Object value = doc.get(fields.get(0));
                results.add((R) convertValue(value, resultType));
            } else {
                results.add((R) doc);
            }
        }
        return results;
    }

    public Object convertValue(Object value, Class<?> targetType) {
        if (value == null) {
            return null;
        }
        if (targetType.isInstance(value)) {
            return value;
        }
        if (targetType == LocalDate.class && value instanceof String) {
            try {
                return LocalDate.parse((String) value);
            } catch (Exception ignored) {
            }
        }
        if (targetType == LocalDateTime.class && value instanceof String) {
            try {
                return LocalDateTime.parse((String) value);
            } catch (Exception ignored) {
            }
        }
        if (targetType == LocalTime.class && value instanceof String) {
            try {
                return LocalTime.parse((String) value);
            } catch (Exception ignored) {
            }
        }
        return ((Optional<Object>) conversionService.convert(value, targetType)).orElse(value);
    }

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
            filter = filterBuilder.buildFilterFromJson(entityFactory.apply(nq.getRootEntity()), nq.getFilterMap(), jsonParams, namedParameters);
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

    public Optional<Number> executeDelete(@NonNull PreparedQuery<?, Number> q, NitritePreparedQuery<?, Number> nq) {
        NitriteCollection coll = collectionFactory.apply(nq.getRootEntity());
        return Optional.of(coll.remove(nq.getNitriteFilter()).getAffectedCount());
    }

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

    public Filter buildFilterFromPreparedQuery(final PreparedQuery<?, ?> q, NitriteStoredQuery<?, ?> stored) {
        Map<String, Object> namedParameters = buildNamedParameterValues(q);
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

    public Filter parseFilterFromDeleteStatement(final String sql, final Object[] params, final Map<String, Object> namedParameters) {
        int whereIdx = sql.toUpperCase().indexOf(" WHERE ");
        if (whereIdx < 0) {
            return Filter.ALL;
        }
        String where = sql.substring(whereIdx + 7);
        int orderByIdx = where.toUpperCase().indexOf(" ORDER BY");
        return parseWhereClause(orderByIdx >= 0 ? where.substring(0, orderByIdx) : where, params, namedParameters);
    }

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

    public Filter parseFilterFromUpdateStatement(final String sql, final Object[] params, final Map<String, Object> namedParameters) {
        int whereIdx = sql.toUpperCase().indexOf(" WHERE ");
        if (whereIdx < 0) {
            return Filter.ALL;
        }
        String w = sql.substring(whereIdx + 7).trim();
        String clause = w.startsWith("(") && w.endsWith(")") ? w.substring(1, w.length() - 1) : w;
        return parseWhereClause(clause, params, namedParameters);
    }

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
}
