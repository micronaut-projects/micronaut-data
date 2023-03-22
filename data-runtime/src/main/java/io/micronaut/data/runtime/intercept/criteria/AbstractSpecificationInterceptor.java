/*
 * Copyright 2017-2021 original authors
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
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.beans.BeanIntrospection;
import io.micronaut.core.type.Argument;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.data.annotation.RepositoryConfiguration;
import io.micronaut.data.intercept.RepositoryMethodKey;
import io.micronaut.data.intercept.annotation.DataMethod;
import io.micronaut.data.model.AssociationUtils;
import io.micronaut.data.model.Pageable;
import io.micronaut.data.model.Sort;
import io.micronaut.data.model.jpa.criteria.PersistentEntityCriteriaQuery;
import io.micronaut.data.model.jpa.criteria.PersistentEntityFrom;
import io.micronaut.data.model.jpa.criteria.impl.QueryResultPersistentEntityCriteriaQuery;
import io.micronaut.data.model.query.JoinPath;
import io.micronaut.data.model.query.QueryModel;
import io.micronaut.data.model.query.builder.QueryBuilder;
import io.micronaut.data.model.query.builder.QueryResult;
import io.micronaut.data.model.runtime.PreparedQuery;
import io.micronaut.data.model.runtime.StoredQuery;
import io.micronaut.data.operations.RepositoryOperations;
import io.micronaut.data.repository.jpa.criteria.CriteriaDeleteBuilder;
import io.micronaut.data.repository.jpa.criteria.CriteriaQueryBuilder;
import io.micronaut.data.repository.jpa.criteria.CriteriaUpdateBuilder;
import io.micronaut.data.repository.jpa.criteria.DeleteSpecification;
import io.micronaut.data.repository.jpa.criteria.PredicateSpecification;
import io.micronaut.data.repository.jpa.criteria.QuerySpecification;
import io.micronaut.data.repository.jpa.criteria.UpdateSpecification;
import io.micronaut.data.runtime.criteria.RuntimeCriteriaBuilder;
import io.micronaut.data.runtime.intercept.AbstractQueryInterceptor;
import io.micronaut.data.runtime.query.MethodContextAwareStoredQueryDecorator;
import io.micronaut.data.runtime.query.PreparedQueryDecorator;
import io.micronaut.data.runtime.query.StoredQueryDecorator;
import io.micronaut.data.runtime.query.internal.QueryResultStoredQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaDelete;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.CriteriaUpdate;
import jakarta.persistence.criteria.Order;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Abstract specification interceptor.
 *
 * @param <T> The declaring type
 * @param <R> The return type
 * @author Denis Stepanov
 * @since 3.2
 */
@Internal
public abstract class AbstractSpecificationInterceptor<T, R> extends AbstractQueryInterceptor<T, R> {

    private final Map<RepositoryMethodKey, QueryBuilder> sqlQueryBuilderForRepositories = new ConcurrentHashMap<>();
    private final Map<RepositoryMethodKey, Set<JoinPath>> methodsJoinPaths = new ConcurrentHashMap<>();
    private final RuntimeCriteriaBuilder criteriaBuilder;
    private final MethodContextAwareStoredQueryDecorator storedQueryDecorator;
    private final PreparedQueryDecorator preparedQueryDecorator;

    /**
     * Default constructor.
     *
     * @param operations The operations
     */
    protected AbstractSpecificationInterceptor(RepositoryOperations operations) {
        super(operations);
        this.criteriaBuilder = operations.getApplicationContext().getBean(RuntimeCriteriaBuilder.class);
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
        preparedQueryDecorator = operations instanceof PreparedQueryDecorator ? (PreparedQueryDecorator) operations : new PreparedQueryDecorator() {
            @Override
            public <E, K> PreparedQuery<E, K> decorate(PreparedQuery<E, K> preparedQuery) {
                return preparedQuery;
            }
        };
    }

    @NonNull
    protected final <E, QR> PreparedQuery<E, QR> preparedQueryForCriteria(RepositoryMethodKey methodKey,
                                                                          MethodInvocationContext<T, R> context,
                                                                          Type type) {

        Pageable pageable = Pageable.UNPAGED;
        for (Object param : context.getParameterValues()) {
            if (param instanceof Pageable) {
                pageable = (Pageable) param;
                break;
            }
        }

        QueryBuilder sqlQueryBuilder = sqlQueryBuilderForRepositories.computeIfAbsent(methodKey, repositoryMethodKey -> {
                    Class<QueryBuilder> builder = context.getAnnotationMetadata().classValue(RepositoryConfiguration.class, "queryBuilder")
                            .orElseThrow(() -> new IllegalStateException("Cannot determine QueryBuilder"));
                    BeanIntrospection<QueryBuilder> introspection = BeanIntrospection.getIntrospection(builder);
                    if (introspection.getConstructorArguments().length == 1
                            && introspection.getConstructorArguments()[0].getType() == AnnotationMetadata.class) {
                        return introspection.instantiate(context.getAnnotationMetadata());
                    }
                    return introspection.instantiate();
                }
        );

        StoredQuery<E, ?> storedQuery;
        if (type == Type.FIND_ALL || type == Type.FIND_ONE || type == Type.FIND_PAGE) {
            Set<JoinPath> joinPaths = methodsJoinPaths.computeIfAbsent(methodKey, repositoryMethodKey ->
                AssociationUtils.getJoinFetchPaths(context));
            storedQuery = buildFind(context, type, pageable, sqlQueryBuilder, joinPaths);
        } else if (type == Type.COUNT) {
            storedQuery = buildCount(context, sqlQueryBuilder);
        } else if (type == Type.DELETE_ALL) {
            storedQuery = buildDeleteAll(context, sqlQueryBuilder);
        } else if (type == Type.UPDATE_ALL) {
            storedQuery = buildUpdateAll(context, sqlQueryBuilder);
        } else if (type == Type.EXISTS) {
            Set<JoinPath> joinPaths = methodsJoinPaths.computeIfAbsent(methodKey, repositoryMethodKey ->
                AssociationUtils.getJoinFetchPaths(context));
          storedQuery = buildExists(context, sqlQueryBuilder, joinPaths);
        } else {
            throw new IllegalStateException("Unknown criteria type: " + type);
        }
        storedQuery = storedQueryDecorator.decorate(context, storedQuery);
        PreparedQuery<E, QR> preparedQuery = (PreparedQuery<E, QR>) preparedQueryResolver.resolveQuery(context, storedQuery, pageable);
        return preparedQueryDecorator.decorate(preparedQuery);
    }

    private <E> StoredQuery<E, ?> buildExists(MethodInvocationContext<T, R> context, QueryBuilder sqlQueryBuilder, Set<JoinPath> annotationJoinPaths) {
        Class<E> rootEntity = getRequiredRootEntity(context);
        CriteriaQueryBuilder<E> builder = getCriteriaQueryBuilder(context, annotationJoinPaths);
        CriteriaQuery<E> criteriaQuery = builder.build(criteriaBuilder);
        QueryResult queryResult = ((QueryResultPersistentEntityCriteriaQuery) criteriaQuery).buildQuery(sqlQueryBuilder);

        return QueryResultStoredQuery.single(DataMethod.OperationType.EXISTS, context.getName(), context.getAnnotationMetadata(),
            queryResult, rootEntity);
    }

    private <E> StoredQuery<E, ?> buildUpdateAll(MethodInvocationContext<T, R> context, QueryBuilder sqlQueryBuilder) {
        CriteriaUpdateBuilder<E> criteriaUpdateBuilder = getCriteriaUpdateBuilder(context);
        CriteriaUpdate<E> criteriaUpdate = criteriaUpdateBuilder.build(criteriaBuilder);
        QueryResult queryResult = ((QueryResultPersistentEntityCriteriaQuery) criteriaUpdate).buildQuery(sqlQueryBuilder);
        return QueryResultStoredQuery.single(DataMethod.OperationType.UPDATE, context.getName(),
            context.getAnnotationMetadata(), queryResult, (Class<E>) criteriaUpdate.getRoot().getJavaType());
    }

    private <E> StoredQuery<E, ?> buildDeleteAll(MethodInvocationContext<T, R> context, QueryBuilder sqlQueryBuilder) {
        CriteriaDeleteBuilder<E> criteriaDeleteBuilder = getCriteriaDeleteBuilder(context);
        CriteriaDelete<E> criteriaDelete = criteriaDeleteBuilder.build(criteriaBuilder);
        QueryResult queryResult = ((QueryResultPersistentEntityCriteriaQuery) criteriaDelete).buildQuery(sqlQueryBuilder);
        return QueryResultStoredQuery.single(DataMethod.OperationType.DELETE, context.getName(),
            context.getAnnotationMetadata(), queryResult, (Class<E>) criteriaDelete.getRoot().getJavaType());
    }

    private <E> StoredQuery<E, ?> buildCount(MethodInvocationContext<T, R> context, QueryBuilder sqlQueryBuilder) {
        StoredQuery<E, ?> storedQuery;
        Class<E> rootEntity = getRequiredRootEntity(context);
        QuerySpecification<E> specification = getQuerySpecification(context);
        PersistentEntityCriteriaQuery<Long> criteriaQuery = criteriaBuilder.createQuery(Long.class);
        Root<E> root = criteriaQuery.from(rootEntity);

        if (specification != null) {
            Predicate predicate = specification.toPredicate(root, criteriaQuery, criteriaBuilder);
            if (predicate != null) {
                criteriaQuery.where(predicate);
            }
        }
        criteriaQuery.select(criteriaBuilder.count(root));
        QueryResult queryResult = ((QueryResultPersistentEntityCriteriaQuery) criteriaQuery).buildQuery(sqlQueryBuilder);
        storedQuery = QueryResultStoredQuery.count(context.getName(), context.getAnnotationMetadata(), queryResult, rootEntity);
        return storedQuery;
    }

    private <E> StoredQuery<E, Object> buildFind(MethodInvocationContext<T, R> context, Type type, Pageable pageable, QueryBuilder sqlQueryBuilder,
                                                 Set<JoinPath> annotationJoinPaths) {
        Class<E> rootEntity = getRequiredRootEntity(context);
        CriteriaQueryBuilder<Object> builder = getCriteriaQueryBuilder(context, annotationJoinPaths);
        CriteriaQuery<Object> criteriaQuery = builder.build(criteriaBuilder);

        if (type == Type.FIND_ALL) {
            for (Object param : context.getParameterValues()) {
                if (param instanceof Sort sort && param != pageable) {
                    if (sort.isSorted()) {
                        Root<?> root = criteriaQuery.getRoots().stream().findFirst().orElseThrow(() -> new IllegalStateException("The root not found!"));
                        criteriaQuery.orderBy(getOrders(sort, root, criteriaBuilder));
                        break;
                    }
                }
            }
        }
        QueryResultPersistentEntityCriteriaQuery queryModelCriteriaQuery = (QueryResultPersistentEntityCriteriaQuery) criteriaQuery;
        QueryModel queryModel = queryModelCriteriaQuery.getQueryModel();
        Collection<JoinPath> queryJoinPaths = queryModel.getJoinPaths();
        QueryResult queryResult = sqlQueryBuilder.buildQuery(queryModel);
        Set<JoinPath> joinPaths = mergeJoinPaths(annotationJoinPaths, queryJoinPaths).stream().filter(jp -> jp.getJoinType().isFetch()).collect(Collectors.toSet());
        if (type == Type.FIND_ONE) {
            return QueryResultStoredQuery.single(DataMethod.OperationType.QUERY, context.getName(), context.getAnnotationMetadata(),
                queryResult, rootEntity, criteriaQuery.getResultType(), joinPaths);
        }
        return QueryResultStoredQuery.many(context.getName(), context.getAnnotationMetadata(), queryResult, rootEntity,
            criteriaQuery.getResultType(), !pageable.isUnpaged(), joinPaths);
    }

    /**
     * Find {@link io.micronaut.data.repository.jpa.criteria.QuerySpecification} in context.
     *
     * @param context The context
     * @return found specification
     * @param <K> the specification entity root type
     */
    @Nullable
    protected <K> QuerySpecification<K> getQuerySpecification(MethodInvocationContext<?, ?> context) {
        final Object parameterValue = context.getParameterValues()[0];
        if (parameterValue instanceof QuerySpecification) {
            return (QuerySpecification) parameterValue;
        }
        if (parameterValue instanceof PredicateSpecification) {
            return QuerySpecification.where((PredicateSpecification) parameterValue);
        }
        Argument<?> parameterArgument = context.getArguments()[0];
        if (parameterArgument.isAssignableFrom(QuerySpecification.class) || parameterArgument.isAssignableFrom(PredicateSpecification.class)) {
            return null;
        }
        throw new IllegalArgumentException("Argument must be an instance of: " + QuerySpecification.class + " or " + PredicateSpecification.class);
    }

    /**
     * Find {@link io.micronaut.data.repository.jpa.criteria.CriteriaQueryBuilder}
     * or {@link io.micronaut.data.repository.jpa.criteria.QuerySpecification} in context.
     *
     * @param context The context
     * @param joinPaths The join fetch paths
     * @return found specification
     * @param <K> the result type
     */
    @NonNull
    protected <K> CriteriaQueryBuilder<K> getCriteriaQueryBuilder(MethodInvocationContext<?, ?> context, Set<JoinPath> joinPaths) {
        final Object parameterValue = context.getParameterValues()[0];
        if (parameterValue instanceof CriteriaQueryBuilder) {
            return (CriteriaQueryBuilder) parameterValue;
        }
        return criteriaBuilder -> {
            Class<K> rootEntity = getRequiredRootEntity(context);
            QuerySpecification<K> specification = getQuerySpecification(context);
            CriteriaQuery<K> criteriaQuery = criteriaBuilder.createQuery(rootEntity);
            Root<K> root = criteriaQuery.from(rootEntity);
            if (specification != null) {
                Predicate predicate = specification.toPredicate(root, criteriaQuery, criteriaBuilder);
                if (predicate != null) {
                    criteriaQuery.where(predicate);
                }
            }
            if (CollectionUtils.isNotEmpty(joinPaths)) {
                PersistentEntityFrom<K, ?> criteriaEntity = ((PersistentEntityFrom<K, ?>) root);
                for (JoinPath joinPath : joinPaths) {
                    join(criteriaEntity, joinPath);
                }
            }
            return criteriaQuery;
        };
    }

    private <K> void join(PersistentEntityFrom<K, ?> criteriaEntity, JoinPath joinPath) {
        Optional<String> optAlias = joinPath.getAlias();
        if (optAlias.isPresent()) {
            criteriaEntity.join(joinPath.getPath(), joinPath.getJoinType(), optAlias.get());
        } else {
            criteriaEntity.join(joinPath.getPath(), joinPath.getJoinType());
        }
    }

    private Set<JoinPath> mergeJoinPaths(Set<JoinPath> joinPaths, Collection<JoinPath> additionalJoinPaths) {
        Set<JoinPath> resultPaths = new HashSet<>(5);
        if (CollectionUtils.isNotEmpty(joinPaths)) {
            resultPaths.addAll(joinPaths);
        }
        if (CollectionUtils.isNotEmpty(additionalJoinPaths)) {
            Map<String, JoinPath> existingPathsByPath = resultPaths.stream().collect(Collectors.toMap(JoinPath::getPath, Function.identity()));
            resultPaths.addAll(additionalJoinPaths.stream().filter(jp -> !existingPathsByPath.containsKey(jp.getPath())).collect(Collectors.toSet()));
        }
        return resultPaths;
    }

    /**
     * Find {@link io.micronaut.data.repository.jpa.criteria.DeleteSpecification} in context.
     *
     * @param context The context
     * @return found specification
     * @param <K> the specification entity root type
     */
    @Nullable
    protected <K> DeleteSpecification<K> getDeleteSpecification(MethodInvocationContext<?, ?> context) {
        final Object parameterValue = context.getParameterValues()[0];
        if (parameterValue instanceof DeleteSpecification) {
            return (DeleteSpecification) parameterValue;
        }
        if (parameterValue instanceof PredicateSpecification) {
            return DeleteSpecification.where((PredicateSpecification) parameterValue);
        }
        Argument<?> parameterArgument = context.getArguments()[0];
        if (parameterArgument.isAssignableFrom(DeleteSpecification.class) || parameterArgument.isAssignableFrom(PredicateSpecification.class)) {
            return null;
        }
        throw new IllegalArgumentException("Argument must be an instance of: " + DeleteSpecification.class + " or " + PredicateSpecification.class);
    }

    /**
     * Find {@link io.micronaut.data.repository.jpa.criteria.CriteriaDeleteBuilder}
     * or {@link io.micronaut.data.repository.jpa.criteria.QuerySpecification} in context.
     *
     * @param context The context
     * @return found specification
     * @param <K> the result type
     */
    @NonNull
    protected <K> CriteriaDeleteBuilder<K> getCriteriaDeleteBuilder(MethodInvocationContext<?, ?> context) {
        final Object parameterValue = context.getParameterValues()[0];
        if (parameterValue instanceof CriteriaDeleteBuilder) {
            return (CriteriaDeleteBuilder) parameterValue;
        }
        return criteriaBuilder -> {
            Class<K> rootEntity = getRequiredRootEntity(context);
            DeleteSpecification<K> specification = getDeleteSpecification(context);
            CriteriaDelete<K> criteriaDelete = criteriaBuilder.createCriteriaDelete(rootEntity);
            Root<K> root = criteriaDelete.from(rootEntity);
            if (specification != null) {
                Predicate predicate = specification.toPredicate(root, criteriaDelete, criteriaBuilder);
                if (predicate != null) {
                    criteriaDelete.where(predicate);
                }
            }
            return criteriaDelete;
        };
    }

    /**
     * Find {@link io.micronaut.data.repository.jpa.criteria.UpdateSpecification} in context.
     *
     * @param context The context
     * @return found specification
     * @param <K> the specification entity root type
     */
    @Nullable
    protected <K> UpdateSpecification<K> getUpdateSpecification(MethodInvocationContext<?, ?> context) {
        final Object parameterValue = context.getParameterValues()[0];
        if (parameterValue instanceof UpdateSpecification) {
            return (UpdateSpecification) parameterValue;
        }
        Argument<?> parameterArgument = context.getArguments()[0];
        if (parameterArgument.isAssignableFrom(UpdateSpecification.class) || parameterArgument.isAssignableFrom(PredicateSpecification.class)) {
            return null;
        }
        throw new IllegalArgumentException("Argument must be an instance of: " + UpdateSpecification.class);
    }

    /**
     * Find {@link io.micronaut.data.repository.jpa.criteria.CriteriaUpdateBuilder}
     * or {@link io.micronaut.data.repository.jpa.criteria.QuerySpecification} in context.
     *
     * @param context The context
     * @return found specification
     * @param <K> the result type
     */
    @NonNull
    protected <K> CriteriaUpdateBuilder<K> getCriteriaUpdateBuilder(MethodInvocationContext<?, ?> context) {
        final Object parameterValue = context.getParameterValues()[0];
        if (parameterValue instanceof CriteriaUpdateBuilder) {
            return (CriteriaUpdateBuilder) parameterValue;
        }
        return criteriaBuilder -> {
            Class<K> rootEntity = getRequiredRootEntity(context);
            UpdateSpecification<K> specification = getUpdateSpecification(context);
            CriteriaUpdate<K> criteriaUpdate = criteriaBuilder.createCriteriaUpdate(rootEntity);
            Root<K> root = criteriaUpdate.from(rootEntity);
            if (specification != null) {
                Predicate predicate = specification.toPredicate(root, criteriaUpdate, criteriaBuilder);
                if (predicate != null) {
                    criteriaUpdate.where(predicate);
                }
            }
            return criteriaUpdate;
        };
    }

    private List<Order> getOrders(Sort sort, Root<?> root, CriteriaBuilder cb) {
        List<Order> orders = new ArrayList<>();
        for (Sort.Order order : sort.getOrderBy()) {
            Path<Object> propertyPath = root.get(order.getProperty());
            orders.add(order.isAscending() ? cb.asc(propertyPath) : cb.desc(propertyPath));
        }
        return orders;
    }

    protected enum Type {
        COUNT, FIND_ONE, FIND_PAGE, FIND_ALL, DELETE_ALL, UPDATE_ALL, EXISTS
    }

}
