/*
 * Copyright 2017-2022 original authors
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
package io.micronaut.data.runtime.query.internal;

import io.micronaut.context.ApplicationContextProvider;
import io.micronaut.context.env.Environment;
import io.micronaut.context.env.PropertyPlaceholderResolver;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.reflect.ReflectionUtils;
import io.micronaut.core.util.ArrayUtils;
import io.micronaut.core.util.StringUtils;
import io.micronaut.data.annotation.*;
import io.micronaut.data.intercept.annotation.DataMethod;
import io.micronaut.data.intercept.annotation.DataMethodQuery;
import io.micronaut.data.intercept.annotation.DataMethodQueryParameter;
import io.micronaut.data.model.AssociationUtils;
import io.micronaut.data.model.DataType;
import io.micronaut.data.model.JsonDataType;
import io.micronaut.data.model.query.JoinPath;
import io.micronaut.data.model.query.builder.sql.SqlQueryBuilder;
import io.micronaut.data.model.runtime.DefaultStoredDataOperation;
import io.micronaut.data.model.runtime.QueryParameterBinding;
import io.micronaut.data.model.runtime.StoredQuery;
import io.micronaut.data.operations.HintsCapableRepository;
import io.micronaut.inject.ExecutableMethod;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static io.micronaut.data.intercept.annotation.DataMethod.META_MEMBER_LIMIT;
import static io.micronaut.data.intercept.annotation.DataMethod.META_MEMBER_PAGE_SIZE;

/**
 * Represents a prepared query.
 *
 * @param <E>  The entity type
 * @param <RT> The result type
 */
@Internal
public final class DefaultStoredQuery<E, RT> extends DefaultStoredDataOperation<RT> implements StoredQuery<E, RT> {
    private static final String DATA_METHOD_ANN_NAME = DataMethod.class.getName();
    @NonNull
    private final Class<RT> resultType;
    private final DataType resultDataType;
    @NonNull
    private final Class<E> rootEntity;
    @NonNull
    private final String query;
    private final String[] queryParts;
    private final ExecutableMethod<?, ?> method;
    private final boolean isDto;
    private final boolean isOptimisticLock;
    private final boolean isNative;
    private final boolean isProcedure;
    private final boolean isNumericPlaceHolder;
    private final boolean hasPageable;
    private final AnnotationMetadata annotationMetadata;
    private final boolean isCount;
    private final boolean hasResultConsumer;
    private Map<String, Object> queryHints;
    private Set<JoinPath> joinPaths = null;
    private Set<JoinPath> joinFetchPaths = null;
    private final List<QueryParameterBinding> queryParameters;
    private final boolean rawQuery;
    private final boolean jsonEntity;
    private final OperationType operationType;
    private final Map<String, AnnotationValue<?>> parameterExpressions;
    private final int limit;
    private final int offset;
    private final Function<Object, Object> stringsEnvResolverValueMapper;

    /**
     * The default constructor.
     *
     * @param method               The target method
     * @param isCount              Is the query a count query
     * @param repositoryOperations The repositoryOperations
     */
    public DefaultStoredQuery(
        @NonNull ExecutableMethod<?, ?> method,
        boolean isCount,
        HintsCapableRepository repositoryOperations) {
        this(method, method.getAnnotation(DataMethod.NAME), isCount, repositoryOperations);
    }

    /**
     * The default constructor.
     *
     * @param method               The target method
     * @param dataMethodQuery      The data method query annotation
     * @param repositoryOperations The repositoryOperations
     */
    public DefaultStoredQuery(
        @NonNull ExecutableMethod<?, ?> method,
        AnnotationValue<Annotation> dataMethodQuery,
        HintsCapableRepository repositoryOperations) {
        this(method, dataMethodQuery, false, repositoryOperations);
    }

    /**
     * The default constructor.
     *
     * @param method               The target method
     * @param dataMethodQuery      The data method query annotation
     * @param isCount              Is the query a count query
     * @param repositoryOperations The repositoryOperations
     */
    public DefaultStoredQuery(
        @NonNull ExecutableMethod<?, ?> method,
        AnnotationValue<Annotation> dataMethodQuery,
        boolean isCount,
        HintsCapableRepository repositoryOperations) {
        super(method);

        if (repositoryOperations instanceof ApplicationContextProvider applicationContextProvider) {
            Environment environment = applicationContextProvider.getApplicationContext().getEnvironment();
            stringsEnvResolverValueMapper = createEnvResolverValueMapper(environment);
        } else {
            stringsEnvResolverValueMapper = null;
        }

        this.rootEntity = getRequiredRootEntity(method);
        this.annotationMetadata = method.getAnnotationMetadata();
        this.isProcedure = dataMethodQuery.isTrue(DataMethodQuery.META_MEMBER_PROCEDURE);
        this.hasResultConsumer = method.stringValue(DATA_METHOD_ANN_NAME, "sqlMappingFunction").isPresent();
        this.isNumericPlaceHolder = method
                .classValue(RepositoryConfiguration.class, "queryBuilder")
                .map(c -> c == SqlQueryBuilder.class).orElse(false);
        this.hasPageable = dataMethodQuery.stringValue(TypeRole.PAGEABLE).isPresent() ||
            dataMethodQuery.stringValue(TypeRole.SORT).isPresent() ||
            dataMethodQuery.intValue(META_MEMBER_LIMIT).orElse(-1) > -1 ||
            dataMethodQuery.intValue(META_MEMBER_PAGE_SIZE).orElse(-1) > -1;
        String query;
        if (isCount) {
            // Legacy count definition
            AnnotationValue<Annotation> queryAnnotation = method.getAnnotation(Query.class.getName());
            query = queryAnnotation.stringValue(DataMethod.META_MEMBER_COUNT_QUERY)
                .orElseGet(() -> queryAnnotation.stringValue()
                    .orElseThrow(() -> new IllegalStateException("No query present in method")));
            Optional<String> rawCountQueryString = method.stringValue(Query.class, DataMethod.META_MEMBER_RAW_COUNT_QUERY);
            this.rawQuery = rawCountQueryString.isPresent();
            this.query = rawCountQueryString.orElse(query);
            String[] countQueryParts = method.stringValues(DataMethod.class, DataMethod.META_MEMBER_EXPANDABLE_COUNT_QUERY);
            // for countBy queries this is empty, and we should use DataMethod.META_MEMBER_EXPANDABLE_QUERY value
            if (ArrayUtils.isNotEmpty(countQueryParts)) {
                this.queryParts = countQueryParts;
            } else {
                this.queryParts = method.stringValues(DataMethodQuery.class, DataMethodQuery.META_MEMBER_EXPANDABLE_QUERY);
            }
            this.isNative = queryAnnotation.isTrue(DataMethodQuery.META_MEMBER_NATIVE);
            //noinspection unchecked
            this.resultType = (Class<RT>) Long.class;
            this.resultDataType = DataType.LONG;
        } else {
            Optional<String> q = dataMethodQuery.stringValue();
            if (q.isPresent()) {
                // Query defined by DataMethodQuery
                Optional<String> rawQueryString = dataMethodQuery.stringValue(DataMethodQuery.META_MEMBER_RAW_QUERY);
                this.isNative = dataMethodQuery.isTrue(DataMethodQuery.META_MEMBER_NATIVE);
                this.rawQuery = rawQueryString.isPresent();
                this.query = rawQueryString.orElseGet(q::get);
            } else {
                AnnotationValue<Annotation> queryAnnotation = method.getAnnotation(Query.class.getName());
                query = queryAnnotation.stringValue().orElseThrow(() ->
                    new IllegalStateException("No query present in method")
                );
                Optional<String> rawQueryString = queryAnnotation.stringValue(DataMethodQuery.META_MEMBER_RAW_QUERY);
                this.isNative = queryAnnotation.isTrue(DataMethodQuery.META_MEMBER_NATIVE);
                this.rawQuery = rawQueryString.isPresent();
                this.query = rawQueryString.orElse(query);
            }
            this.resultDataType = dataMethodQuery.enumValue(DataMethodQuery.META_MEMBER_RESULT_DATA_TYPE, DataType.class).orElse(DataType.OBJECT);
            this.queryParts = getQueryParts(dataMethodQuery, DataMethodQuery.META_MEMBER_EXPANDABLE_QUERY);
            //noinspection unchecked
            this.resultType = dataMethodQuery.classValue(DataMethodQuery.META_MEMBER_RESULT_TYPE)
                .map(type -> (Class<RT>) ReflectionUtils.getWrapperType(type))
                .orElse((Class<RT>) rootEntity);
        }
        this.method = method;
        this.isDto = dataMethodQuery.isTrue(DataMethodQuery.META_MEMBER_DTO);
        this.isOptimisticLock = dataMethodQuery.isTrue(DataMethodQuery.META_MEMBER_OPTIMISTIC_LOCK);
        this.operationType = dataMethodQuery.enumValue(DataMethodQuery.META_MEMBER_OPERATION_TYPE, DataMethodQuery.OperationType.class)
            .map(op -> OperationType.valueOf(op.name()))
            .orElse(OperationType.QUERY);
        this.isCount = isCount || operationType == OperationType.COUNT;
        if (method.hasAnnotation(QueryHint.class)) {
            List<AnnotationValue<QueryHint>> values = method.getAnnotationValuesByType(QueryHint.class);
            this.queryHints = new HashMap<>(values.size());
            for (AnnotationValue<QueryHint> value : values) {
                String n = value.stringValue("name").orElse(null);
                String v = value.stringValue("value").orElse(null);
                if (StringUtils.isNotEmpty(n) && StringUtils.isNotEmpty(v)) {
                    queryHints.put(n, v);
                }
            }
        }
        Map<String, Object> queryHints = repositoryOperations.getQueryHints(this);
        if (queryHints != Collections.EMPTY_MAP) {
            if (this.queryHints != null) {
                this.queryHints.putAll(queryHints);
            } else {
                this.queryHints = queryHints;
            }
        }

        this.queryParameters = getQueryParameters(
            dataMethodQuery.getAnnotations(DataMethodQuery.META_MEMBER_PARAMETERS, DataMethodQueryParameter.class),
            isNumericPlaceHolder
        );
        this.jsonEntity = DataAnnotationUtils.hasJsonEntityRepresentationAnnotation(annotationMetadata);
        this.parameterExpressions = annotationMetadata.getAnnotationValuesByType(ParameterExpression.class).stream()
            .collect(Collectors.toMap(av -> av.stringValue("name").orElseThrow(), av -> av));
        this.limit = dataMethodQuery.intValue(DataMethodQuery.META_MEMBER_LIMIT).orElse(-1);
        this.offset = dataMethodQuery.intValue(DataMethodQuery.META_MEMBER_OFFSET).orElse(0);
    }

    private static <E> Class<E> getRequiredRootEntity(ExecutableMethod<?, ?> context) {
        Class aClass = context.classValue(DataMethod.NAME, DataMethod.META_MEMBER_ROOT_ENTITY).orElse(null);
        if (aClass != null) {
            return aClass;
        } else {
            final AnnotationValue<Annotation> ann = context.getDeclaredAnnotation(DataMethod.NAME);
            if (ann != null) {
                aClass = ann.classValue(DataMethod.META_MEMBER_ROOT_ENTITY).orElse(null);
                if (aClass != null) {
                    return aClass;
                }
            }
            throw new IllegalStateException("No root entity present in method");
        }
    }

    private static List<QueryParameterBinding> getQueryParameters(List<AnnotationValue<DataMethodQueryParameter>> params,
                                                                  boolean isNumericPlaceHolder) {
        List<QueryParameterBinding> queryParameters = new ArrayList<>(params.size());
        for (AnnotationValue<DataMethodQueryParameter> av : params) {
            String[] propertyPath = av.stringValues(DataMethodQueryParameter.META_MEMBER_PROPERTY_PATH);
            Object value = null;
            if (av.getValues().containsKey(AnnotationMetadata.VALUE_MEMBER)) {
                value = av;
            }
            if (propertyPath.length == 0) {
                propertyPath = av.stringValue(DataMethodQueryParameter.META_MEMBER_PROPERTY)
                        .map(property -> new String[]{property})
                        .orElse(null);
            }
            String[] parameterBindingPath = av.stringValues(DataMethodQueryParameter.META_MEMBER_PARAMETER_BINDING_PATH);
            if (parameterBindingPath.length == 0) {
                parameterBindingPath = null;
            }
            DataType dataType = isNumericPlaceHolder ? av.enumValue(DataMethodQueryParameter.META_MEMBER_DATA_TYPE, DataType.class).orElse(DataType.OBJECT) : null;
            JsonDataType jsonDataType = dataType != null ? av.enumValue(DataMethodQueryParameter.META_MEMBER_JSON_DATA_TYPE, JsonDataType.class).orElse(JsonDataType.DEFAULT) : null;
            queryParameters.add(
                    new StoredQueryParameter(
                            av.stringValue(DataMethodQueryParameter.META_MEMBER_NAME).orElse(null),
                            dataType,
                            jsonDataType,
                            av.intValue(DataMethodQueryParameter.META_MEMBER_PARAMETER_INDEX).orElse(-1),
                            parameterBindingPath,
                            propertyPath,
                            av.booleanValue(DataMethodQueryParameter.META_MEMBER_AUTO_POPULATED).orElse(false),
                            av.booleanValue(DataMethodQueryParameter.META_MEMBER_REQUIRES_PREVIOUS_POPULATED_VALUES).orElse(false),
                            av.classValue(DataMethodQueryParameter.META_MEMBER_CONVERTER).orElse(null),
                            av.booleanValue(DataMethodQueryParameter.META_MEMBER_EXPANDABLE).orElse(false),
                            av.booleanValue(DataMethodQueryParameter.META_MEMBER_EXPRESSION).orElse(false),
                            value,
                            av.stringValue(DataMethodQueryParameter.META_MEMBER_ROLE).orElse(null),
                            av.stringValue(DataMethodQueryParameter.META_MEMBER_TABLE_ALIAS).orElse(null),
                            queryParameters
                    ));
        }
        return queryParameters;
    }

    @Override
    public int getLimit() {
        return limit;
    }

    @Override
    public int getOffset() {
        return offset;
    }

    @Override
    public List<QueryParameterBinding> getQueryBindings() {
        return queryParameters;
    }

    @NonNull
    @Override
    public Set<JoinPath> getJoinFetchPaths() {
        if (joinFetchPaths == null) {
            this.joinFetchPaths = Collections.unmodifiableSet(AssociationUtils.getJoinFetchPaths(method));
        }
        return joinFetchPaths;
    }

    @Override
    public Set<JoinPath> getJoinPaths() {
        if (joinPaths == null) {
            joinPaths = Collections.unmodifiableSet(AssociationUtils.getJoinPaths(method));
        }
        return joinPaths;
    }

    /**
     * @return The method
     */
    public ExecutableMethod<?, ?> getMethod() {
        return method;
    }

    @Override
    public boolean isSingleResult() {
        return !isCount() && getJoinFetchPaths().isEmpty();
    }

    @Override
    public boolean hasResultConsumer() {
        return this.hasResultConsumer;
    }

    @Override
    public boolean isCount() {
        return isCount;
    }

    @NonNull
    @Override
    public Map<String, Object> getQueryHints() {
        if (queryHints != null) {
            return queryHints;
        }
        return Collections.emptyMap();
    }

    @Override
    public boolean isNative() {
        return isNative;
    }

    @Override
    public boolean isProcedure() {
        return isProcedure;
    }

    @Override
    public OperationType getOperationType() {
        return operationType;
    }

    /**
     * Is this a raw SQL query.
     *
     * @return The raw sql query.
     */
    @Override
    public boolean useNumericPlaceholders() {
        return isNumericPlaceHolder;
    }

    /**
     * @return Whether the query is a DTO query
     */
    @Override
    public boolean isDtoProjection() {
        return isDto;
    }

    /**
     * @return The result type
     */
    @Override
    @NonNull
    public Class<RT> getResultType() {
        return resultType;
    }

    @NonNull
    @Override
    public DataType getResultDataType() {
        return resultDataType;
    }

    /**
     * @return The ID type
     */
    @SuppressWarnings("unchecked")
    @Override
    public Optional<Class<?>> getEntityIdentifierType() {
        Optional o = annotationMetadata.classValue(DATA_METHOD_ANN_NAME, DataMethod.META_MEMBER_ID_TYPE);
        return o;
    }

    /**
     * @return The root entity type
     */
    @Override
    @NonNull
    public Class<E> getRootEntity() {
        return rootEntity;
    }

    @Override
    public boolean hasPageable() {
        return hasPageable;
    }

    @Override
    @NonNull
    public String getQuery() {
        return query;
    }

    @Override
    public String[] getExpandableQueryParts() {
        return queryParts;
    }

    @NonNull
    @Override
    public String getName() {
        return method.getMethodName();
    }

    @Override
    @NonNull
    public Class<?>[] getArgumentTypes() {
        return method.getArgumentTypes();
    }

    @Override
    public boolean isOptimisticLock() {
        return isOptimisticLock;
    }

    @Override
    public boolean isRawQuery() {
        return this.rawQuery;
    }

    @Override
    public boolean isJsonEntity() {
        return jsonEntity;
    }

    @Override
    public Map<String, AnnotationValue<?>> getParameterExpressions() {
        return parameterExpressions;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        DefaultStoredQuery<?, ?> that = (DefaultStoredQuery<?, ?>) o;
        return resultType.equals(that.resultType) &&
                method.equals(that.method);
    }

    @Override
    public int hashCode() {
        return Objects.hash(resultType, method);
    }

    private String[] getQueryParts(@NonNull AnnotationValue annotationValue, @NonNull String member) {
        if (stringsEnvResolverValueMapper != null) {
            return annotationValue.stringValues(member, stringsEnvResolverValueMapper);
        }
        return annotationValue.stringValues(member);
    }

    private static Function<Object, Object> createEnvResolverValueMapper(Environment environment) {
        return o -> {
            PropertyPlaceholderResolver resolver = environment.getPlaceholderResolver();
            if (o instanceof String[] values) {
                String[] resolvedValues = Arrays.copyOf(values, values.length);
                for (int i = 0; i < values.length; i++) {
                    String value = values[i];
                    if (value.contains(resolver.getPrefix())) {
                        value = resolver.resolveRequiredPlaceholders(value);
                    }
                    resolvedValues[i] = value;
                }
               return resolvedValues;
            }
            return o;
        };
    }
}
