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

import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.reflect.ReflectionUtils;
import io.micronaut.core.util.StringUtils;
import io.micronaut.data.annotation.Query;
import io.micronaut.data.annotation.QueryHint;
import io.micronaut.data.annotation.RepositoryConfiguration;
import io.micronaut.data.annotation.TypeRole;
import io.micronaut.data.intercept.annotation.DataMethod;
import io.micronaut.data.intercept.annotation.DataMethodQueryParameter;
import io.micronaut.data.model.AssociationUtils;
import io.micronaut.data.model.DataType;
import io.micronaut.data.model.query.JoinPath;
import io.micronaut.data.model.query.builder.sql.SqlQueryBuilder;
import io.micronaut.data.model.runtime.DefaultStoredDataOperation;
import io.micronaut.data.model.runtime.QueryParameterBinding;
import io.micronaut.data.model.runtime.StoredQuery;
import io.micronaut.data.operations.HintsCapableRepository;
import io.micronaut.inject.ExecutableMethod;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

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
    private static final int[] EMPTY_INT_ARRAY = new int[0];
    @NonNull
    private final Class<RT> resultType;
    @NonNull
    private final Class<E> rootEntity;
    @NonNull
    private final String query;
    private final String[] queryParts;
    private final ExecutableMethod<?, ?> method;
    private final boolean isDto;
    private final boolean isOptimisticLock;
    private final boolean isNative;
    private final boolean isNumericPlaceHolder;
    private final boolean hasPageable;
    private final AnnotationMetadata annotationMetadata;
    private final boolean isCount;
    private final DataType[] indexedDataTypes;
    private final boolean hasResultConsumer;
    private Map<String, Object> queryHints;
    private Set<JoinPath> joinFetchPaths = null;
    private final List<StoredQueryParameter> queryParameters;
    private final boolean rawQuery;

    /**
     * The default constructor.
     *
     * @param method               The target method
     * @param resultType           The result type of the query
     * @param rootEntity           The root entity of the query
     * @param query                The query itself
     * @param isCount              Is the query a count query
     * @param repositoryOperations The repositoryOperations
     */
    public DefaultStoredQuery(
            @NonNull ExecutableMethod<?, ?> method,
            @NonNull Class<RT> resultType,
            @NonNull Class<E> rootEntity,
            @NonNull String query,
            boolean isCount,
            HintsCapableRepository repositoryOperations) {
        super(method);
        //noinspection unchecked
        this.resultType = (Class<RT>) ReflectionUtils.getWrapperType(resultType);
        this.rootEntity = rootEntity;
        this.annotationMetadata = method.getAnnotationMetadata();
        this.isNative = method.isTrue(Query.class, "nativeQuery");
        this.hasResultConsumer = method.stringValue(DATA_METHOD_ANN_NAME, "sqlMappingFunction").isPresent();
        this.isNumericPlaceHolder = method
                .classValue(RepositoryConfiguration.class, "queryBuilder")
                .map(c -> c == SqlQueryBuilder.class).orElse(false);
        this.hasPageable = method.stringValue(DATA_METHOD_ANN_NAME, TypeRole.PAGEABLE).isPresent() ||
                method.stringValue(DATA_METHOD_ANN_NAME, TypeRole.SORT).isPresent() ||
                method.intValue(DATA_METHOD_ANN_NAME, META_MEMBER_PAGE_SIZE).orElse(-1) > -1;

        if (isCount) {
            Optional<String> rawCountQueryString = method.stringValue(Query.class, DataMethod.META_MEMBER_RAW_COUNT_QUERY);
            this.rawQuery = rawCountQueryString.isPresent();
            this.query = rawCountQueryString.orElse(query);
            this.queryParts = method.stringValues(DataMethod.class, DataMethod.META_MEMBER_EXPANDABLE_COUNT_QUERY);
        } else {
            Optional<String> rawQueryString = method.stringValue(Query.class, DataMethod.META_MEMBER_RAW_QUERY);
            this.rawQuery = rawQueryString.isPresent();
            this.query = rawQueryString.orElse(query);
            this.queryParts = method.stringValues(DataMethod.class, DataMethod.META_MEMBER_EXPANDABLE_QUERY);
        }
        this.method = method;
        this.isDto = method.isTrue(DATA_METHOD_ANN_NAME, DataMethod.META_MEMBER_DTO);
        this.isOptimisticLock = method.isTrue(DATA_METHOD_ANN_NAME, DataMethod.META_MEMBER_OPTIMISTIC_LOCK);

        this.isCount = isCount;
        AnnotationValue<DataMethod> annotation = annotationMetadata.getAnnotation(DataMethod.class);
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

        if (isNumericPlaceHolder) {
            this.indexedDataTypes = annotationMetadata
                    .getValue(DataMethod.class, DataMethod.META_MEMBER_PARAMETER_TYPE_DEFS, DataType[].class)
                    .orElse(DataType.EMPTY_DATA_TYPE_ARRAY);
        } else {
            this.indexedDataTypes = null;
        }

        if (annotation == null) {
            queryParameters = Collections.emptyList();
        } else {
            List<AnnotationValue<DataMethodQueryParameter>> params = annotation.getAnnotations(DataMethod.META_MEMBER_PARAMETERS, DataMethodQueryParameter.class);
            List<StoredQueryParameter> queryParameters = new ArrayList<>(params.size());
            for (AnnotationValue<DataMethodQueryParameter> av : params) {
                String[] propertyPath = av.stringValues(DataMethodQueryParameter.META_MEMBER_PROPERTY_PATH);
                if (propertyPath.length == 0) {
                    propertyPath = av.stringValue(DataMethodQueryParameter.META_MEMBER_PROPERTY)
                            .map(property -> new String[]{property})
                            .orElse(null);
                }
                String[] parameterBindingPath = av.stringValues(DataMethodQueryParameter.META_MEMBER_PARAMETER_BINDING_PATH);
                if (parameterBindingPath.length == 0) {
                    parameterBindingPath = null;
                }
                queryParameters.add(
                        new StoredQueryParameter(
                                av.stringValue(DataMethodQueryParameter.META_MEMBER_NAME).orElse(null),
                                isNumericPlaceHolder ? av.enumValue(DataMethodQueryParameter.META_MEMBER_DATA_TYPE, DataType.class).orElse(DataType.OBJECT) : null,
                                av.intValue(DataMethodQueryParameter.META_MEMBER_PARAMETER_INDEX).orElse(-1),
                                parameterBindingPath,
                                propertyPath,
                                av.booleanValue(DataMethodQueryParameter.META_MEMBER_AUTO_POPULATED).orElse(false),
                                av.booleanValue(DataMethodQueryParameter.META_MEMBER_REQUIRES_PREVIOUS_POPULATED_VALUES).orElse(false),
                                av.classValue(DataMethodQueryParameter.META_MEMBER_CONVERTER).orElse(null),
                                av.booleanValue(DataMethodQueryParameter.META_MEMBER_EXPANDABLE).orElse(false),
                                queryParameters
                        ));
            }
            this.queryParameters = queryParameters;
        }
    }

    @Override
    public List<QueryParameterBinding> getQueryBindings() {
        return (List) queryParameters;
    }

    @NonNull
    @Override
    public Set<JoinPath> getJoinFetchPaths() {
        if (joinFetchPaths == null) {
            Set<JoinPath> set = AssociationUtils.getJoinFetchPaths(method);
            this.joinFetchPaths = set.isEmpty() ? Collections.emptySet() : Collections.unmodifiableSet(set);
        }
        return joinFetchPaths;
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
    public DataType[] getIndexedParameterTypes() {
        if (indexedDataTypes == null) {
            return DataType.EMPTY_DATA_TYPE_ARRAY;
        }
        return this.indexedDataTypes;
    }

    @NonNull
    @Override
    public int[] getIndexedParameterBinding() {
        return EMPTY_INT_ARRAY;
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
        if (isCount) {
            return DataType.LONG;
        }
        return annotationMetadata.enumValue(DATA_METHOD_ANN_NAME, DataMethod.META_MEMBER_RESULT_DATA_TYPE, DataType.class)
                .orElse(DataType.OBJECT);
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
}
