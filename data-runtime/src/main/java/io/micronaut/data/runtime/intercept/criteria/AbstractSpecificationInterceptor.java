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
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.beans.BeanIntrospection;
import io.micronaut.core.type.Argument;
import io.micronaut.data.annotation.RepositoryConfiguration;
import io.micronaut.data.intercept.RepositoryMethodKey;
import io.micronaut.data.intercept.annotation.DataMethod;
import io.micronaut.data.model.Pageable;
import io.micronaut.data.model.Sort;
import io.micronaut.data.model.jpa.criteria.PersistentEntityCriteriaDelete;
import io.micronaut.data.model.jpa.criteria.PersistentEntityCriteriaQuery;
import io.micronaut.data.model.jpa.criteria.PersistentEntityCriteriaUpdate;
import io.micronaut.data.model.jpa.criteria.impl.QueryResultPersistentEntityCriteriaQuery;
import io.micronaut.data.model.query.builder.QueryBuilder;
import io.micronaut.data.model.query.builder.QueryResult;
import io.micronaut.data.model.runtime.PreparedQuery;
import io.micronaut.data.model.runtime.RuntimeEntityRegistry;
import io.micronaut.data.model.runtime.StoredQuery;
import io.micronaut.data.operations.RepositoryOperations;
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
import jakarta.persistence.criteria.Order;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Abstract specification interceptor.
 *
 * @param <T> The declaring type
 * @param <R> The return type
 * @author Denis Stepanov
 * @since 3.2
 */
public abstract class AbstractSpecificationInterceptor<T, R> extends AbstractQueryInterceptor<T, R> {

    private final Map<RepositoryMethodKey, QueryBuilder> sqlQueryBuilderForRepositories = new ConcurrentHashMap<>();
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
        RuntimeEntityRegistry runtimeEntityRegistry = operations.getApplicationContext().getBean(RuntimeEntityRegistry.class);
        this.criteriaBuilder = new RuntimeCriteriaBuilder(runtimeEntityRegistry);
        if (operations instanceof MethodContextAwareStoredQueryDecorator) {
            storedQueryDecorator = (MethodContextAwareStoredQueryDecorator) operations;
        } else if (operations instanceof StoredQueryDecorator) {
            StoredQueryDecorator decorator = (StoredQueryDecorator) operations;
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
            storedQuery = buildFind(context, type, pageable, sqlQueryBuilder);
        } else if (type == Type.COUNT) {
            storedQuery = buildCount(context, sqlQueryBuilder);
        } else if (type == Type.DELETE_ALL) {
            storedQuery = buildDeleteAll(context, sqlQueryBuilder);
        } else if (type == Type.UPDATE_ALL) {
            storedQuery = buildUpdateAll(context, sqlQueryBuilder);
        } else {
            throw new IllegalStateException("Unknown criteria type: " + type);
        }
        storedQuery = storedQueryDecorator.decorate(context, storedQuery);
        PreparedQuery<E, QR> preparedQuery = (PreparedQuery<E, QR>) preparedQueryResolver.resolveQuery(context, storedQuery, pageable);
        return preparedQueryDecorator.decorate(preparedQuery);
    }

    private <E> StoredQuery<E, ?> buildUpdateAll(MethodInvocationContext<T, R> context, QueryBuilder sqlQueryBuilder) {
        StoredQuery<E, ?> storedQuery;
        Class<E> rootEntity = getRequiredRootEntity(context);
        UpdateSpecification<E> specification = getUpdateSpecification(context);
        PersistentEntityCriteriaUpdate<E> criteriaUpdate = criteriaBuilder.createCriteriaUpdate(rootEntity);
        Root<E> root = criteriaUpdate.from(rootEntity);
        if (specification != null) {
            Predicate predicate = specification.toPredicate(root, criteriaUpdate, criteriaBuilder);
            if (predicate != null) {
                criteriaUpdate.where(predicate);
            }
        }
        QueryResult queryResult = ((QueryResultPersistentEntityCriteriaQuery) criteriaUpdate).buildQuery(sqlQueryBuilder);
        storedQuery = QueryResultStoredQuery.single(DataMethod.OperationType.UPDATE, context.getName(), context.getAnnotationMetadata(), queryResult, rootEntity);
        return storedQuery;
    }

    private <E> StoredQuery<E, ?> buildDeleteAll(MethodInvocationContext<T, R> context, QueryBuilder sqlQueryBuilder) {
        StoredQuery<E, ?> storedQuery;
        Class<E> rootEntity = getRequiredRootEntity(context);
        DeleteSpecification<E> specification = getDeleteSpecification(context);
        PersistentEntityCriteriaDelete<E> criteriaDelete = criteriaBuilder.createCriteriaDelete(rootEntity);
        Root<E> root = criteriaDelete.from(rootEntity);
        if (specification != null) {
            Predicate predicate = specification.toPredicate(root, criteriaDelete, criteriaBuilder);
            if (predicate != null) {
                criteriaDelete.where(predicate);
            }
        }
        QueryResult queryResult = ((QueryResultPersistentEntityCriteriaQuery) criteriaDelete).buildQuery(sqlQueryBuilder);
        storedQuery = QueryResultStoredQuery.single(DataMethod.OperationType.DELETE, context.getName(), context.getAnnotationMetadata(), queryResult, rootEntity);
        return storedQuery;
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

    private <E> StoredQuery<E, ?> buildFind(MethodInvocationContext<T, R> context, Type type, Pageable pageable, QueryBuilder sqlQueryBuilder) {
        Class<E> rootEntity = getRequiredRootEntity(context);
        QuerySpecification<E> specification = getQuerySpecification(context);
        PersistentEntityCriteriaQuery<E> criteriaQuery = criteriaBuilder.createQuery(rootEntity);
        Root<E> root = criteriaQuery.from(rootEntity);

        if (specification != null) {
            Predicate predicate = specification.toPredicate(root, criteriaQuery, criteriaBuilder);
            if (predicate != null) {
                criteriaQuery.where(predicate);
            }
        }

        if (type == Type.FIND_ALL) {
            for (Object param : context.getParameterValues()) {
                if (param instanceof Sort && param != pageable) {
                    Sort sort = (Sort) param;
                    if (sort.isSorted()) {
                        criteriaQuery.orderBy(getOrders(sort, root, criteriaBuilder));
                        break;
                    }
                }
            }
        }
        QueryResult queryResult = ((QueryResultPersistentEntityCriteriaQuery) criteriaQuery).buildQuery(sqlQueryBuilder);
        return QueryResultStoredQuery.many(context.getName(), context.getAnnotationMetadata(), queryResult, rootEntity, !pageable.isUnpaged());
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

    private List<Order> getOrders(Sort sort, Root<?> root, CriteriaBuilder cb) {
        List<Order> orders = new ArrayList<>();
        for (Sort.Order order : sort.getOrderBy()) {
            Path<Object> propertyPath = root.get(order.getProperty());
            orders.add(order.isAscending() ? cb.asc(propertyPath) : cb.desc(propertyPath));
        }
        return orders;
    }

    protected enum Type {
        COUNT, FIND_ONE, FIND_PAGE, FIND_ALL, DELETE_ALL, UPDATE_ALL
    }

}
