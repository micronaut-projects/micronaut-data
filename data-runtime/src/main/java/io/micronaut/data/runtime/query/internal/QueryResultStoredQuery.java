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
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.reflect.ClassUtils;
import io.micronaut.data.intercept.annotation.DataMethod;
import io.micronaut.data.model.DataType;
import io.micronaut.data.model.query.builder.QueryResult;
import io.micronaut.data.model.runtime.QueryParameterBinding;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * The basic {@link io.micronaut.data.model.runtime.StoredQuery} created from {@link QueryResult}.
 *
 * @param <E> The entity type
 * @param <R> The result type
 * @author Denis Stepanov
 * @since 3.5.0
 */
@Internal
public final class QueryResultStoredQuery<E, R> extends BasicStoredQuery<E, R> {

    private final DataMethod.OperationType operationType;
    private final QueryResult queryResult;

    public QueryResultStoredQuery(String name,
                                  AnnotationMetadata annotationMetadata,
                                  QueryResult queryResult,
                                  Class<E> rootEntity,
                                  Class<R> resultType,
                                  boolean pageable,
                                  boolean isSingleResult,
                                  boolean isCount,
                                  DataMethod.OperationType operationType) {
        super(name,
                annotationMetadata,
                queryResult.getQuery(),
                queryResult.getParameterBindings().stream()
                        .anyMatch(io.micronaut.data.model.query.builder.QueryParameterBinding::isExpandable) ? queryResult.getQueryParts().toArray(new String[0]) : null,
                map(queryResult.getParameterBindings()),
                rootEntity,
                resultType,
                pageable,
                isSingleResult,
                isCount
        );
        this.queryResult = queryResult;
        this.operationType = operationType;
    }

    public static <T> QueryResultStoredQuery<T, T> single(DataMethod.OperationType operationType,
                                                          String name,
                                                          AnnotationMetadata annotationMetadata,
                                                          QueryResult queryResult,
                                                          Class<T> rootEntity) {
        return new QueryResultStoredQuery<>(name, annotationMetadata, queryResult, rootEntity, rootEntity, false, true, false, operationType);
    }

    public static <T, R> QueryResultStoredQuery<T, R> single(DataMethod.OperationType operationType,
                                                          String name,
                                                          AnnotationMetadata annotationMetadata,
                                                          QueryResult queryResult,
                                                          Class<T> rootEntity,
                                                          Class<R> resultType) {
        return new QueryResultStoredQuery<>(name, annotationMetadata, queryResult, rootEntity, resultType == Object.class ? (Class<R>) rootEntity : resultType,false, true, false, operationType);
    }

    public static <T> QueryResultStoredQuery<T, T> many(String name,
                                                           AnnotationMetadata annotationMetadata,
                                                           QueryResult queryResult,
                                                           Class<T> rootEntity,
                                                           boolean pageable) {
        return new QueryResultStoredQuery<>(name, annotationMetadata, queryResult, rootEntity, rootEntity, pageable, false, false, DataMethod.OperationType.QUERY);
    }

    public static <T, R> QueryResultStoredQuery<T, R> many(String name,
                                                           AnnotationMetadata annotationMetadata,
                                                           QueryResult queryResult,
                                                           Class<T> rootEntity,
                                                           Class<R> resultType,
                                                           boolean pageable) {
        return new QueryResultStoredQuery<>(name, annotationMetadata, queryResult, rootEntity, resultType == Object.class ? (Class<R>) rootEntity : resultType, pageable, false, false, DataMethod.OperationType.QUERY);
    }

    public static <T> QueryResultStoredQuery<T, Long> count(String name,
                                                            AnnotationMetadata annotationMetadata,
                                                            QueryResult queryResult,
                                                            Class<T> rootEntity) {
        return new QueryResultStoredQuery<>(name, annotationMetadata, queryResult, rootEntity, Long.class, false, true, true, DataMethod.OperationType.COUNT);
    }

    private static List<QueryParameterBinding> map(List<io.micronaut.data.model.query.builder.QueryParameterBinding> parameterBindings) {
        List<QueryParameterBinding> queryParameters = new ArrayList<>(parameterBindings.size());
        for (io.micronaut.data.model.query.builder.QueryParameterBinding p : parameterBindings) {
            queryParameters.add(
                    new QueryResultParameterBinding(p, queryParameters)
            );
        }
        return queryParameters;
    }

    public QueryResult getQueryResult() {
        return queryResult;
    }

    public DataMethod.OperationType getOperationType() {
        return operationType;
    }

    private static class QueryResultParameterBinding implements QueryParameterBinding {
        private final io.micronaut.data.model.query.builder.QueryParameterBinding p;
        private final List<QueryParameterBinding> all;

        private boolean previousInitialized;
        private QueryParameterBinding previousPopulatedValueParameter;

        public QueryResultParameterBinding(io.micronaut.data.model.query.builder.QueryParameterBinding p, List<QueryParameterBinding> all) {
            this.p = p;
            this.all = all;
        }

        @Override
        public String getName() {
            return p.getKey();
        }

        @Override
        public DataType getDataType() {
            return p.getDataType();
        }

        @Override
        public Class<?> getParameterConverterClass() {
            if (p.getConverterClassName() == null) {
                return null;
            }
            return ClassUtils.forName(p.getConverterClassName(), null).orElseThrow(IllegalStateException::new);
        }

        @Override
        public int getParameterIndex() {
            return p.getParameterIndex();
        }

        @Override
        public String[] getParameterBindingPath() {
            return p.getParameterBindingPath();
        }

        @Override
        public String[] getPropertyPath() {
            return p.getPropertyPath();
        }

        @Override
        public boolean isAutoPopulated() {
            return p.isAutoPopulated();
        }

        @Override
        public boolean isRequiresPreviousPopulatedValue() {
            return p.isRequiresPreviousPopulatedValue();
        }

        @Override
        public QueryParameterBinding getPreviousPopulatedValueParameter() {
            if (!previousInitialized) {
                for (QueryParameterBinding it : all) {
                    if (it != this && it.getParameterIndex() != -1 && Arrays.equals(getPropertyPath(), it.getPropertyPath())) {
                        previousPopulatedValueParameter = it;
                        break;
                    }
                }
                previousInitialized = true;
            }
            return previousPopulatedValueParameter;
        }

        @Override
        public boolean isExpandable() {
            return p.isExpandable();
        }
    }
}
