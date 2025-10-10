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
package io.micronaut.data.runtime.query;

import io.micronaut.aop.MethodInvocationContext;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.type.Argument;
import io.micronaut.data.annotation.DataAnnotationUtils;
import io.micronaut.data.annotation.Query;
import io.micronaut.data.annotation.RepositoryConfiguration;
import io.micronaut.data.intercept.annotation.DataMethod;
import io.micronaut.data.model.DataType;
import io.micronaut.data.model.query.builder.sql.SqlQueryBuilder;
import io.micronaut.data.model.runtime.QueryParameterBinding;
import io.micronaut.data.model.runtime.StoredQuery;
import io.micronaut.data.operations.HintsCapableRepository;
import io.micronaut.data.runtime.query.internal.DefaultStoredQuery;
import io.micronaut.inject.ExecutableMethod;

import java.lang.annotation.Annotation;
import java.util.List;

import static io.micronaut.data.model.runtime.StoredQuery.OperationType;

/**
 * Default stored query resolver.
 *
 * @author Denis Stepanov
 * @since 3.3
 */
@Internal
public abstract class DefaultStoredQueryResolver implements StoredQueryResolver {

    @Override
    public <E, R> StoredQuery<E, R> resolveQuery(MethodInvocationContext<?, ?> context) {
        return new DefaultStoredQuery<>(
            context.getExecutableMethod(),
            false,
            getHintsCapableRepository()
        );
    }

    @Override
    public <E, R> StoredQuery<E, R> resolveCountQuery(MethodInvocationContext<?, ?> context) {
        AnnotationValue<Annotation> dataMethodQuery = context.getAnnotation(DataMethod.NAME);
        AnnotationValue<Annotation> countQuery = dataMethodQuery.getAnnotation(DataMethod.META_MEMBER_COUNT_QUERY).orElse(null);
        if (countQuery != null) {
            return new DefaultStoredQuery<>(
                context.getExecutableMethod(),
                countQuery,
                getHintsCapableRepository()
            );
        }
        // Previous way
        return new DefaultStoredQuery<>(
            context.getExecutableMethod(),
            true,
            getHintsCapableRepository()
        );
    }

    @Override
    public <E, QR> StoredQuery<E, QR> createStoredQuery(ExecutableMethod<?, ?> executableMethod,
                                                        OperationType operationType,
                                                        String name,
                                                        AnnotationMetadata annotationMetadata,
                                                        Class<Object> rootEntity,
                                                        String query,
                                                        String update,
                                                        String[] queryParts,
                                                        List<QueryParameterBinding> queryParameters,
                                                        boolean pageable,
                                                        boolean isSingleResult) {
        if (queryParts == null) {
            queryParts = new String[0];
        }
        String[] finalQueryParts = queryParts;
        Class<QR> resultType = annotationMetadata.classValue(DataMethod.class, DataMethod.META_MEMBER_RESULT_DATA_TYPE).orElse(rootEntity);
        DataType resultDataType = annotationMetadata.enumValue(DataMethod.class, DataMethod.META_MEMBER_RESULT_DATA_TYPE, DataType.class).orElse(DataType.ENTITY);
        boolean rawQuery = annotationMetadata.stringValue(Query.class, DataMethod.META_MEMBER_RAW_QUERY).isPresent();
        boolean jsonEntity = DataAnnotationUtils.hasJsonEntityRepresentationAnnotation(annotationMetadata);
        return new StoredQuery<>() {

            @Override
            public OperationType getOperationType() {
                return operationType;
            }

            @Override
            public Class<E> getRootEntity() {
                return (Class<E>) rootEntity;
            }

            @Override
            public boolean hasPageable() {
                return pageable;
            }

            @Override
            public String getQuery() {
                return query;
            }

            @Override
            public String[] getExpandableQueryParts() {
                return finalQueryParts;
            }

            @Override
            public List<QueryParameterBinding> getQueryBindings() {
                return queryParameters;
            }

            @Override
            public Class<QR> getResultType() {
                return resultType;
            }

            @Override
            public Argument<QR> getResultArgument() {
                return Argument.of(getResultType());
            }

            @Override
            public DataType getResultDataType() {
                return resultDataType;
            }

            @Override
            public boolean useNumericPlaceholders() {
                return annotationMetadata.classValue(RepositoryConfiguration.class, "queryBuilder")
                    .map(c -> c == SqlQueryBuilder.class).orElse(false);
            }

            @Override
            public boolean isCount() {
                return false;
            }

            @Override
            public boolean isSingleResult() {
                return isSingleResult;
            }

            @Override
            public boolean hasResultConsumer() {
                return false;
            }

            @Override
            public boolean isRawQuery() {
                return rawQuery;
            }

            @Override
            public boolean isJsonEntity() {
                return jsonEntity;
            }

            @Override
            public String getName() {
                return name;
            }
        };
    }

    @Override
    public StoredQuery<Object, Long> createCountStoredQuery(ExecutableMethod<?, ?> executableMethod,
                                                            OperationType operationType,
                                                            String name,
                                                            AnnotationMetadata annotationMetadata,
                                                            Class<Object> rootEntity,
                                                            String query,
                                                            String[] queryParts,
                                                            List<QueryParameterBinding> queryParameters) {
        if (queryParts == null) {
            queryParts = new String[0];
        }
        String[] finalQueryParts = queryParts;
        boolean rawCountQuery = annotationMetadata.stringValue(Query.class, DataMethod.META_MEMBER_RAW_COUNT_QUERY).isPresent();
        return new StoredQuery<>() {

            @Override
            public OperationType getOperationType() {
                return operationType;
            }

            @Override
            public Class<Object> getRootEntity() {
                return rootEntity;
            }

            @Override
            public boolean hasPageable() {
                return false;
            }

            @Override
            public String getQuery() {
                return query;
            }

            @Override
            public String[] getExpandableQueryParts() {
                return finalQueryParts;
            }

            @Override
            public List<QueryParameterBinding> getQueryBindings() {
                return queryParameters;
            }

            @Override
            public Class<Long> getResultType() {
                return Long.class;
            }

            @Override
            public Argument<Long> getResultArgument() {
                return Argument.LONG;
            }

            @Override
            public DataType getResultDataType() {
                return DataType.LONG;
            }

            @Override
            public boolean useNumericPlaceholders() {
                return annotationMetadata
                    .classValue(RepositoryConfiguration.class, "queryBuilder")
                    .map(c -> c == SqlQueryBuilder.class).orElse(false);
            }

            @Override
            public boolean isCount() {
                return true;
            }

            @Override
            public boolean isSingleResult() {
                return true;
            }

            @Override
            public boolean hasResultConsumer() {
                return false;
            }

            @Override
            public String getName() {
                return name;
            }

            @Override
            public boolean isRawQuery() {
                return rawCountQuery;
            }
        };
    }

    protected abstract HintsCapableRepository getHintsCapableRepository();
}
