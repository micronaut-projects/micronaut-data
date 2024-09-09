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
import io.micronaut.core.util.StringUtils;
import io.micronaut.data.annotation.RepositoryConfiguration;
import io.micronaut.data.intercept.RepositoryMethodKey;
import io.micronaut.data.model.AssociationUtils;
import io.micronaut.data.model.Pageable;
import io.micronaut.data.model.Pageable.Mode;
import io.micronaut.data.model.Sort;
import io.micronaut.data.model.jpa.criteria.PersistentEntityFrom;
import io.micronaut.data.model.jpa.criteria.impl.QueryResultPersistentEntityCriteriaQuery;
import io.micronaut.data.model.query.JoinPath;
import io.micronaut.data.model.query.builder.QueryBuilder;
import io.micronaut.data.model.query.builder.QueryResult;
import io.micronaut.data.model.runtime.PreparedQuery;
import io.micronaut.data.model.runtime.StoredQuery;
import io.micronaut.data.operations.BlockingCriteriaCapableRepository;
import io.micronaut.data.operations.CriteriaRepositoryOperations;
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
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Order;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;

import static io.micronaut.data.model.runtime.StoredQuery.OperationType;

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

    protected static final String PREPARED_QUERY_KEY = "PREPARED_QUERY";

    protected final CriteriaRepositoryOperations criteriaRepositoryOperations;
    private final Map<RepositoryMethodKey, QueryBuilder> sqlQueryBuilderForRepositories = new ConcurrentHashMap<>();
    private final Map<RepositoryMethodKey, Set<JoinPath>> methodsJoinPaths = new ConcurrentHashMap<>();
    private final CriteriaBuilder criteriaBuilder;
    private final MethodContextAwareStoredQueryDecorator storedQueryDecorator;
    private final PreparedQueryDecorator preparedQueryDecorator;

    /**
     * Default constructor.
     *
     * @param operations The operations
     */
    protected AbstractSpecificationInterceptor(RepositoryOperations operations) {
        super(operations);
        if (operations instanceof CriteriaRepositoryOperations criteriaOps) {
            criteriaRepositoryOperations = criteriaOps;
            criteriaBuilder = criteriaRepositoryOperations.getCriteriaBuilder();
        } else if (operations instanceof BlockingCriteriaCapableRepository repository) {
            criteriaRepositoryOperations = repository.blocking();
            criteriaBuilder = criteriaRepositoryOperations.getCriteriaBuilder();
        } else {
            criteriaRepositoryOperations = null;
            criteriaBuilder = operations.getApplicationContext().getBean(RuntimeCriteriaBuilder.class);
        }
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
    }

    @NonNull
    protected final Iterable<?> findAll(RepositoryMethodKey methodKey, MethodInvocationContext<T, R> context, Type type) {
        Set<JoinPath> methodJoinPaths = getMethodJoinPaths(methodKey, context);
        if (criteriaRepositoryOperations != null) {
            CriteriaQuery<Object> query = buildQuery(context, type, methodJoinPaths);
            Pageable pageable = getPageable(context);
            if (pageable != null) {
                if (pageable.getMode() != Mode.OFFSET) {
                    throw new UnsupportedOperationException("Pageable mode " + pageable.getMode() + " is not supported with specifications");
                }
                return criteriaRepositoryOperations.findAll(query, (int) pageable.getOffset(), pageable.getSize());
            }
            return criteriaRepositoryOperations.findAll(query);
        }
        PreparedQuery<?, ?> preparedQuery = preparedQueryForCriteria(methodKey, context, type, methodJoinPaths);
        context.setAttribute(PREPARED_QUERY_KEY, preparedQuery);
        return operations.findAll(preparedQuery);
    }

    @NonNull
    protected final Object findOne(RepositoryMethodKey methodKey, MethodInvocationContext<T, R> context, Type type) {
        Set<JoinPath> methodJoinPaths = getMethodJoinPaths(methodKey, context);
        if (criteriaRepositoryOperations != null) {
            return criteriaRepositoryOperations.findOne(buildQuery(context, type, methodJoinPaths));
        }
        return operations.findOne(preparedQueryForCriteria(methodKey, context, type, methodJoinPaths));
    }

    protected final Set<JoinPath> getMethodJoinPaths(RepositoryMethodKey methodKey, MethodInvocationContext<T, R> context) {
        return methodsJoinPaths.computeIfAbsent(methodKey, repositoryMethodKey ->
            AssociationUtils.getJoinPaths(context));
    }

    @NonNull
    protected final Long count(RepositoryMethodKey methodKey, MethodInvocationContext<T, R> context) {
        Set<JoinPath> methodJoinPaths = getMethodJoinPaths(methodKey, context);
        Long count;
        if (criteriaRepositoryOperations != null) {
            count = criteriaRepositoryOperations.findOne(buildCountQuery(context, methodJoinPaths));
        } else {
            count = operations.findOne(preparedQueryForCriteria(methodKey, context, Type.COUNT, methodJoinPaths));
        }
        return count == null ? 0 : count;
    }

    protected final boolean exists(RepositoryMethodKey methodKey, MethodInvocationContext<T, R> context) {
        Set<JoinPath> methodJoinPaths = getMethodJoinPaths(methodKey, context);
        if (criteriaRepositoryOperations != null) {
            return criteriaRepositoryOperations.findOne(buildExistsQuery(context, methodJoinPaths));
        }
        Object one = operations.findOne(preparedQueryForCriteria(methodKey, context, Type.EXISTS, methodJoinPaths));
        return one instanceof Boolean aBoolean ? aBoolean : one != null;
    }

    protected final Optional<Number> deleteAll(RepositoryMethodKey methodKey, MethodInvocationContext<T, R> context) {
        Set<JoinPath> methodJoinPaths = getMethodJoinPaths(methodKey, context);
        if (criteriaRepositoryOperations != null) {
            return criteriaRepositoryOperations.deleteAll(buildDeleteQuery(context));
        }
        return operations.executeDelete(preparedQueryForCriteria(methodKey, context, Type.DELETE_ALL, methodJoinPaths));
    }

    protected final Optional<Number> updateAll(RepositoryMethodKey methodKey, MethodInvocationContext<T, R> context) {
        Set<JoinPath> methodJoinPaths = getMethodJoinPaths(methodKey, context);
        if (criteriaRepositoryOperations != null) {
            return criteriaRepositoryOperations.updateAll(buildUpdateQuery(context));
        }
        return operations.executeUpdate(preparedQueryForCriteria(methodKey, context, Type.UPDATE_ALL, methodJoinPaths));
    }

    @NonNull
    protected final <E, QR> PreparedQuery<E, QR> preparedQueryForCriteria(RepositoryMethodKey methodKey,
                                                                          MethodInvocationContext<T, R> context,
                                                                          Type type,
                                                                          Set<JoinPath> methodJoinPaths) {

        Pageable pageable = findPageable(context);
        QueryBuilder sqlQueryBuilder = getQueryBuilder(methodKey, context);
        StoredQuery<E, ?> storedQuery = switch (type) {
            case FIND_ALL, FIND_ONE, FIND_PAGE -> buildFind(methodKey, context, type, methodJoinPaths);
            case COUNT -> buildCount(methodKey, context, methodJoinPaths);
            case DELETE_ALL -> buildDeleteAll(context, sqlQueryBuilder);
            case UPDATE_ALL -> buildUpdateAll(context, sqlQueryBuilder);
            case EXISTS -> buildExists(context, sqlQueryBuilder, methodJoinPaths);
            default -> throw new IllegalStateException("Unknown criteria type: " + type);
        };
        storedQuery = storedQueryDecorator.decorate(context, storedQuery);
        PreparedQuery<E, QR> preparedQuery = (PreparedQuery<E, QR>) preparedQueryResolver.resolveQuery(context, storedQuery, pageable);
        return preparedQueryDecorator.decorate(preparedQuery);
    }

    @NonNull
    private Pageable findPageable(MethodInvocationContext<T, R> context) {
        Pageable pageable = Pageable.UNPAGED;
        for (Object param : context.getParameterValues()) {
            if (param instanceof Pageable pageableParam) {
                pageable = pageableParam;
                break;
            }
        }
        return pageable;
    }

    @NonNull
    private QueryBuilder getQueryBuilder(RepositoryMethodKey methodKey, MethodInvocationContext<T, R> context) {
        return sqlQueryBuilderForRepositories.computeIfAbsent(methodKey, repositoryMethodKey -> {
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
    }

    private <E> StoredQuery<E, ?> buildExists(MethodInvocationContext<T, R> context, QueryBuilder sqlQueryBuilder, Set<JoinPath> annotationJoinPaths) {
        CriteriaQuery<E> criteriaQuery = buildExistsQuery(context, annotationJoinPaths);
        QueryResult queryResult = ((QueryResultPersistentEntityCriteriaQuery) criteriaQuery).buildQuery(context, sqlQueryBuilder);

        return QueryResultStoredQuery.single(OperationType.EXISTS, context.getName(), context.getAnnotationMetadata(),
            queryResult, getRequiredRootEntity(context));
    }

    protected final <E> CriteriaQuery<E> buildExistsQuery(MethodInvocationContext<T, R> context, Set<JoinPath> annotationJoinPaths) {
        return this.<E>getCriteriaQueryBuilder(context, annotationJoinPaths).build(criteriaBuilder);
    }

    private <E> StoredQuery<E, ?> buildUpdateAll(MethodInvocationContext<T, R> context, QueryBuilder sqlQueryBuilder) {
        CriteriaUpdate<E> criteriaUpdate = buildUpdateQuery(context);
        QueryResult queryResult = ((QueryResultPersistentEntityCriteriaQuery) criteriaUpdate).buildQuery(context, sqlQueryBuilder);
        return QueryResultStoredQuery.single(OperationType.UPDATE, context.getName(),
            context.getAnnotationMetadata(), queryResult, (Class<E>) criteriaUpdate.getRoot().getJavaType());
    }

    protected final <E> CriteriaUpdate<E> buildUpdateQuery(MethodInvocationContext<T, R> context) {
        return this.<E>getCriteriaUpdateBuilder(context).build(criteriaBuilder);
    }

    private <E> StoredQuery<E, ?> buildDeleteAll(MethodInvocationContext<T, R> context, QueryBuilder sqlQueryBuilder) {
        CriteriaDelete<E> criteriaDelete = buildDeleteQuery(context);
        QueryResult queryResult = ((QueryResultPersistentEntityCriteriaQuery) criteriaDelete).buildQuery(context, sqlQueryBuilder);
        return QueryResultStoredQuery.single(OperationType.DELETE, context.getName(),
            context.getAnnotationMetadata(), queryResult, (Class<E>) criteriaDelete.getRoot().getJavaType());
    }

    protected final <E> CriteriaDelete<E> buildDeleteQuery(MethodInvocationContext<T, R> context) {
        return this.<E>getCriteriaDeleteBuilder(context).build(criteriaBuilder);
    }

    private <E> StoredQuery<E, ?> buildCount(RepositoryMethodKey methodKey,
                                             MethodInvocationContext<T, R> context,
                                             Set<JoinPath> methodJoinPaths) {
        CriteriaQuery<Long> criteriaQuery = buildCountQuery(context, methodJoinPaths);
        QueryBuilder sqlQueryBuilder = getQueryBuilder(methodKey, context);
        QueryResult queryResult = ((QueryResultPersistentEntityCriteriaQuery) criteriaQuery).buildQuery(context, sqlQueryBuilder);
        return QueryResultStoredQuery.count(context.getName(), context.getAnnotationMetadata(), queryResult, getRequiredRootEntity(context));
    }

    @NonNull
    protected final CriteriaQuery<Long> buildCountQuery(MethodInvocationContext<T, R> context, Set<JoinPath> methodJoinPaths) {
        CriteriaQueryBuilder<Long> criteriaQueryBuilder = getCountCriteriaQueryBuilder(context, methodJoinPaths);
        return criteriaQueryBuilder.build(criteriaBuilder);
    }

    private <E> StoredQuery<E, Object> buildFind(RepositoryMethodKey methodKey,
                                                 MethodInvocationContext<T, R> context,
                                                 Type type,
                                                 Set<JoinPath> methodJoinPaths) {

        CriteriaQuery<Object> criteriaQuery = buildInternalQuery(context, type, methodJoinPaths);
        QueryBuilder sqlQueryBuilder = getQueryBuilder(methodKey, context);
        QueryResult queryResult = ((QueryResultPersistentEntityCriteriaQuery) criteriaQuery).buildQuery(context, sqlQueryBuilder);
        Set<JoinPath> joinPaths = mergeJoinPaths(methodJoinPaths, queryResult.getJoinPaths());
        Class<E> rootEntity = getRequiredRootEntity(context);
        if (type == Type.FIND_ONE) {
            return QueryResultStoredQuery.single(OperationType.QUERY, context.getName(), context.getAnnotationMetadata(),
                queryResult, rootEntity, criteriaQuery.getResultType(), joinPaths);
        }
        Pageable pageable = findPageable(context);
        return QueryResultStoredQuery.many(context.getName(), context.getAnnotationMetadata(), queryResult, rootEntity,
            criteriaQuery.getResultType(), !pageable.isUnpaged(), joinPaths);
    }

    private <N> CriteriaQuery<N> buildInternalQuery(MethodInvocationContext<T, R> context, Type type, Set<JoinPath> methodJoinPaths) {
        CriteriaQueryBuilder<N> builder = getCriteriaQueryBuilder(context, methodJoinPaths);
        CriteriaQuery<N> criteriaQuery = builder.build(criteriaBuilder);

        if (type == Type.FIND_ALL) {
            Pageable pageable = findPageable(context);
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
        return criteriaQuery;
    }

    protected final <N> CriteriaQuery<N> buildQuery(MethodInvocationContext<T, R> context, Type type, Set<JoinPath> methodJoinPaths) {
        CriteriaQueryBuilder<N> builder = getCriteriaQueryBuilder(context, methodJoinPaths);
        CriteriaQuery<N> criteriaQuery = builder.build(criteriaBuilder);

        for (Object param : context.getParameterValues()) {
            if (param instanceof Sort sort) {
                if (sort.isSorted()) {
                    Root<?> root = criteriaQuery.getRoots().stream().findFirst().orElseThrow(() -> new IllegalStateException("The root not found!"));
                    criteriaQuery.orderBy(getOrders(sort, root, criteriaBuilder));
                    break;
                }
            }
        }
        return criteriaQuery;
    }

    /**
     * Find {@link io.micronaut.data.repository.jpa.criteria.QuerySpecification} in context.
     *
     * @param context The context
     * @param <K>     the specification entity root type
     * @return found specification
     */
    @Nullable
    protected <K> QuerySpecification<K> getQuerySpecification(MethodInvocationContext<?, ?> context) {
        final Object parameterValue = context.getParameterValues()[0];
        if (parameterValue instanceof QuerySpecification querySpecification) {
            return querySpecification;
        }
        if (parameterValue instanceof PredicateSpecification predicateSpecification) {
            return QuerySpecification.where(predicateSpecification);
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
     * @param context   The context
     * @param joinPaths The join fetch paths
     * @param <K>       the result type
     * @return found specification
     */
    @NonNull
    protected <K> CriteriaQueryBuilder<K> getCriteriaQueryBuilder(MethodInvocationContext<?, ?> context, Set<JoinPath> joinPaths) {
        final Object parameterValue = context.getParameterValues()[0];
        if (parameterValue instanceof CriteriaQueryBuilder criteriaQueryBuilder) {
            return criteriaQueryBuilder;
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
                for (JoinPath joinPath : joinPaths) {
                    join(root, joinPath);
                }
            }
            return criteriaQuery;
        };
    }

    /**
     * Find {@link io.micronaut.data.repository.jpa.criteria.CriteriaQueryBuilder}
     * or {@link io.micronaut.data.repository.jpa.criteria.QuerySpecification} in context.
     *
     * @param context   The context
     * @param joinPaths The join fetch paths
     * @param <E>       the entity type
     * @return found specification
     */
    @NonNull
    private <E> CriteriaQueryBuilder<Long> getCountCriteriaQueryBuilder(MethodInvocationContext<?, ?> context, Set<JoinPath> joinPaths) {
        final Object parameterValue = context.getParameterValues()[0];
        if (parameterValue instanceof CriteriaQueryBuilder providedCriteriaQueryBuilder) {
            return new CriteriaQueryBuilder<Long>() {

                @Override
                public CriteriaQuery<Long> build(CriteriaBuilder criteriaBuilder) {
                    CriteriaQuery<?> criteriaQuery = providedCriteriaQueryBuilder.build(criteriaBuilder);
                    Root<?> root = criteriaQuery.getRoots().iterator().next();
                    if (criteriaQuery.isDistinct()) {
                        Expression longExpression = criteriaBuilder.countDistinct(root);
                        return criteriaQuery.select(longExpression);
                    } else {
                        Expression count = criteriaBuilder.count(root);
                        return criteriaQuery.select(count);
                    }
                }
            };
        }
        return criteriaBuilder -> {
            Class<E> rootEntity = getRequiredRootEntity(context);
            QuerySpecification<E> specification = getQuerySpecification(context);
            CriteriaQuery<Long> criteriaQuery = criteriaBuilder.createQuery(Long.class);
            Root<E> root = criteriaQuery.from(rootEntity);
            if (specification != null) {
                Predicate predicate = specification.toPredicate(root, criteriaQuery, criteriaBuilder);
                if (predicate != null) {
                    criteriaQuery.where(predicate);
                }
            }
            if (criteriaQuery.isDistinct()) {
                return criteriaQuery.select(criteriaBuilder.countDistinct(root));
            } else {
                return criteriaQuery.select(criteriaBuilder.count(root));
            }
        };
    }

    private void join(Root<?> root, JoinPath joinPath) {
        if (root instanceof PersistentEntityFrom<?, ?> persistentEntityFrom) {
            Optional<String> optAlias = joinPath.getAlias();
            if (optAlias.isPresent()) {
                persistentEntityFrom.join(joinPath.getPath(), joinPath.getJoinType(), optAlias.get());
            } else {
                persistentEntityFrom.join(joinPath.getPath(), joinPath.getJoinType());
            }
        }
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

    /**
     * Find {@link io.micronaut.data.repository.jpa.criteria.DeleteSpecification} in context.
     *
     * @param context The context
     * @param <K>     the specification entity root type
     * @return found specification
     */
    @Nullable
    protected <K> DeleteSpecification<K> getDeleteSpecification(MethodInvocationContext<?, ?> context) {
        final Object parameterValue = context.getParameterValues()[0];
        if (parameterValue instanceof DeleteSpecification deleteSpecification) {
            return deleteSpecification;
        }
        if (parameterValue instanceof PredicateSpecification predicateSpecification) {
            return DeleteSpecification.where(predicateSpecification);
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
     * @param <K>     the result type
     * @return found specification
     */
    @NonNull
    protected <K> CriteriaDeleteBuilder<K> getCriteriaDeleteBuilder(MethodInvocationContext<?, ?> context) {
        final Object parameterValue = context.getParameterValues()[0];
        if (parameterValue instanceof CriteriaDeleteBuilder criteriaDeleteBuilder) {
            return criteriaDeleteBuilder;
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
     * @param <K>     the specification entity root type
     * @return found specification
     */
    @Nullable
    protected <K> UpdateSpecification<K> getUpdateSpecification(MethodInvocationContext<?, ?> context) {
        final Object parameterValue = context.getParameterValues()[0];
        if (parameterValue instanceof UpdateSpecification updateSpecification) {
            return updateSpecification;
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
     * @param <K>     the result type
     * @return found specification
     */
    @NonNull
    protected <K> CriteriaUpdateBuilder<K> getCriteriaUpdateBuilder(MethodInvocationContext<?, ?> context) {
        final Object parameterValue = context.getParameterValues()[0];
        if (parameterValue instanceof CriteriaUpdateBuilder criteriaUpdateBuilder) {
            return criteriaUpdateBuilder;
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
            Path<?> path = root;
            for (String property : StringUtils.splitOmitEmptyStrings(order.getProperty(), '.')) {
                path = path.get(property);
            }
            Expression<?> expression = order.isIgnoreCase() ? cb.lower(path.type().as(String.class)) : path;
            orders.add(order.isAscending() ? cb.asc(expression) : cb.desc(expression));
        }
        return orders;
    }

    protected enum Type {
        COUNT, FIND_ONE, FIND_PAGE, FIND_ALL, DELETE_ALL, UPDATE_ALL, EXISTS
    }

}
