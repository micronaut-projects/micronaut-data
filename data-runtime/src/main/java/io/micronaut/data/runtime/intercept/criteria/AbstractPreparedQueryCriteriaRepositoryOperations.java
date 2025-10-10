/*
 * Copyright 2017-2024 original authors
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
package io.micronaut.data.runtime.intercept.criteria;

import io.micronaut.aop.MethodInvocationContext;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.data.model.Pageable;
import io.micronaut.data.model.jpa.criteria.impl.AbstractPersistentEntityCriteriaQuery;
import io.micronaut.data.model.jpa.criteria.impl.QueryResultPersistentEntityCriteriaQuery;
import io.micronaut.data.model.query.JoinPath;
import io.micronaut.data.model.query.builder.QueryBuilder;
import io.micronaut.data.model.query.builder.QueryResult;
import io.micronaut.data.model.runtime.PreparedQuery;
import io.micronaut.data.model.runtime.StoredQuery;
import io.micronaut.data.operations.RepositoryOperations;
import io.micronaut.data.runtime.query.DefaultPreparedQueryResolver;
import io.micronaut.data.runtime.query.MethodContextAwareStoredQueryDecorator;
import io.micronaut.data.runtime.query.PreparedQueryDecorator;
import io.micronaut.data.runtime.query.PreparedQueryResolver;
import io.micronaut.data.runtime.query.StoredQueryDecorator;
import io.micronaut.data.runtime.query.internal.QueryResultStoredQuery;
import jakarta.persistence.criteria.CriteriaDelete;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.CriteriaUpdate;
import jakarta.persistence.criteria.Selection;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The abstract criteria operation.
 *
 * @author Denis Stepanov
 * @since 4.10
 */
@Internal
public abstract class AbstractPreparedQueryCriteriaRepositoryOperations {

    private final MethodContextAwareStoredQueryDecorator storedQueryDecorator;
    private final PreparedQueryDecorator preparedQueryDecorator;
    private final PreparedQueryResolver preparedQueryResolver;
    private final MethodInvocationContext<?, ?> context;
    private final QueryBuilder queryBuilder;
    private final Class<?> entityRoot;
    private final Pageable pageable;

    protected AbstractPreparedQueryCriteriaRepositoryOperations(RepositoryOperations operations,
                                                                MethodInvocationContext<?, ?> context,
                                                                QueryBuilder queryBuilder,
                                                                Class<?> entityRoot,
                                                                Pageable pageable) {
        this.context = context;
        this.queryBuilder = queryBuilder;
        this.entityRoot = entityRoot;
        this.pageable = pageable == null ? Pageable.unpaged() : pageable;
        if (operations instanceof MethodContextAwareStoredQueryDecorator) {
            storedQueryDecorator = (MethodContextAwareStoredQueryDecorator) operations;
        } else if (operations instanceof StoredQueryDecorator decorator) {
            storedQueryDecorator = new MethodContextAwareStoredQueryDecorator() {
                @Override
                public <E, K> StoredQuery<E, K> decorate(MethodInvocationContext<?, ?> context, StoredQuery<E, K> storedQuery) {
                    return decorator.decorate(storedQuery);
                }
            };
        } else {
            storedQueryDecorator = new MethodContextAwareStoredQueryDecorator() {
                @Override
                public <E, K> StoredQuery<E, K> decorate(MethodInvocationContext<?, ?> context, StoredQuery<E, K> storedQuery) {
                    return storedQuery;
                }
            };
        }
        preparedQueryDecorator = operations instanceof PreparedQueryDecorator decorator ? decorator : new PreparedQueryDecorator() {
            @Override
            public <E, K> PreparedQuery<E, K> decorate(PreparedQuery<E, K> preparedQuery) {
                return preparedQuery;
            }
        };
        this.preparedQueryResolver = operations instanceof PreparedQueryResolver resolver ? resolver : new DefaultPreparedQueryResolver() {
            @Override
            protected ConversionService getConversionService() {
                return operations.getConversionService();
            }
        };
    }

    protected final PreparedQuery<Object, Boolean> createExists(CriteriaQuery<?> query) {
        return toPreparedQuery(buildExists(query));
    }

    protected final <K> PreparedQuery<Object, K> createFindOne(CriteriaQuery<K> query) {
        return toPreparedQuery(buildFind(query, true));
    }

    protected final <K> PreparedQuery<Object, K> createFindAll(CriteriaQuery<K> query) {
        return toPreparedQuery(buildFind(query, false));
    }

    protected final PreparedQuery<?, Number> createUpdateAll(CriteriaUpdate<Number> query) {
        return toPreparedQuery(buildUpdateAll(query));
    }

    protected final PreparedQuery<?, Number> createDeleteAll(CriteriaDelete<Number> query) {
        return toPreparedQuery(buildDeleteAll(query));
    }

    private <E, QR> PreparedQuery<E, QR> toPreparedQuery(StoredQuery<E, ?> storedQuery) {
        storedQuery = storedQueryDecorator.decorate(context, storedQuery);
        PreparedQuery<E, QR> preparedQuery = (PreparedQuery<E, QR>) preparedQueryResolver.resolveQuery(context, storedQuery, pageable);
        PreparedQuery<E, QR> decorated = preparedQueryDecorator.decorate(preparedQuery);
        context.setAttribute(AbstractSpecificationInterceptor.PREPARED_QUERY_KEY, decorated);
        return decorated;
    }

    private <E, T> StoredQuery<E, T> buildFind(CriteriaQuery<T> criteriaQuery,
                                               boolean isSingle) {

        QueryResult queryResult = ((QueryResultPersistentEntityCriteriaQuery) criteriaQuery).buildQuery(context, queryBuilder);
        Collection<JoinPath> joinPaths = queryResult.getJoinPaths();
        Selection<?> selection = ((AbstractPersistentEntityCriteriaQuery<?>) criteriaQuery).getSelection();
        boolean isCompoundSelection = selection != null && selection.isCompoundSelection();
        if (isSingle) {
            return QueryResultStoredQuery.single(StoredQuery.OperationType.QUERY, context.getName(), context.getAnnotationMetadata(),
                queryResult, (Class<E>) entityRoot, criteriaQuery.getResultType(), isCompoundSelection, joinPaths);
        }
        return QueryResultStoredQuery.many(context.getName(), context.getAnnotationMetadata(), queryResult, (Class<E>) entityRoot,
            criteriaQuery.getResultType(), !pageable.isUnpaged(), isCompoundSelection, joinPaths);
    }

    private <E> StoredQuery<E, ?> buildExists(CriteriaQuery<?> criteriaQuery) {
        QueryResult queryResult = ((QueryResultPersistentEntityCriteriaQuery) criteriaQuery).buildQuery(context, queryBuilder);

        return QueryResultStoredQuery.single(StoredQuery.OperationType.EXISTS, context.getName(), context.getAnnotationMetadata(),
            queryResult, (Class<E>) entityRoot);
    }

    private <E> StoredQuery<E, ?> buildUpdateAll(CriteriaUpdate<E> criteriaUpdate) {
        QueryResult queryResult = ((QueryResultPersistentEntityCriteriaQuery) criteriaUpdate).buildQuery(context, queryBuilder);
        return QueryResultStoredQuery.single(StoredQuery.OperationType.UPDATE, context.getName(),
            context.getAnnotationMetadata(), queryResult, (Class<E>) criteriaUpdate.getRoot().getJavaType());
    }

    private <E> StoredQuery<E, ?> buildDeleteAll(CriteriaDelete<E> criteriaDelete) {
        QueryResult queryResult = ((QueryResultPersistentEntityCriteriaQuery) criteriaDelete).buildQuery(context, queryBuilder);
        return QueryResultStoredQuery.single(StoredQuery.OperationType.DELETE, context.getName(),
            context.getAnnotationMetadata(), queryResult, (Class<E>) criteriaDelete.getRoot().getJavaType());
    }

    private Set<JoinPath> mergeJoinPaths(Collection<JoinPath> joinPaths, Collection<JoinPath> additionalJoinPaths) {
        Set<JoinPath> resultPaths = CollectionUtils.newHashSet(joinPaths.size() + additionalJoinPaths.size());
        if (CollectionUtils.isNotEmpty(joinPaths)) {
            resultPaths.addAll(joinPaths);
        }
        if (CollectionUtils.isNotEmpty(additionalJoinPaths)) {
            Map<String, JoinPath> existingPathsByPath = resultPaths.stream().collect(Collectors.toMap(JoinPath::getPath, Function.identity()));
            resultPaths.addAll(additionalJoinPaths.stream().filter(jp -> !existingPathsByPath.containsKey(jp.getPath())).collect(Collectors.toSet()));
        }
        return resultPaths;
    }

}
