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
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.beans.BeanIntrospection;
import io.micronaut.core.reflect.ClassUtils;
import io.micronaut.core.type.Argument;
import io.micronaut.data.annotation.RepositoryConfiguration;
import io.micronaut.data.intercept.RepositoryMethodKey;
import io.micronaut.data.intercept.annotation.DataMethod;
import io.micronaut.data.model.DataType;
import io.micronaut.data.model.Pageable;
import io.micronaut.data.model.Sort;
import io.micronaut.data.model.jpa.criteria.PersistentEntityCriteriaDelete;
import io.micronaut.data.model.jpa.criteria.PersistentEntityCriteriaQuery;
import io.micronaut.data.model.jpa.criteria.PersistentEntityCriteriaUpdate;
import io.micronaut.data.model.jpa.criteria.impl.QueryResultPersistentEntityCriteriaQuery;
import io.micronaut.data.model.query.builder.QueryBuilder;
import io.micronaut.data.model.query.builder.QueryResult;
import io.micronaut.data.model.runtime.PreparedQuery;
import io.micronaut.data.model.runtime.QueryParameterBinding;
import io.micronaut.data.model.runtime.RuntimeEntityRegistry;
import io.micronaut.data.model.runtime.StoredQuery;
import io.micronaut.data.operations.HintsCapableRepository;
import io.micronaut.data.operations.RepositoryOperations;
import io.micronaut.data.repository.jpa.criteria.DeleteSpecification;
import io.micronaut.data.repository.jpa.criteria.PredicateSpecification;
import io.micronaut.data.repository.jpa.criteria.QuerySpecification;
import io.micronaut.data.repository.jpa.criteria.UpdateSpecification;
import io.micronaut.data.runtime.criteria.RuntimeCriteriaBuilder;
import io.micronaut.data.runtime.intercept.AbstractQueryInterceptor;
import io.micronaut.data.runtime.query.DefaultStoredQueryResolver;
import io.micronaut.data.runtime.query.StoredQueryResolver;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Order;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;

import java.util.ArrayList;
import java.util.Arrays;
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
    private final StoredQueryResolver storedQueryResolver;

    /**
     * Default constructor.
     *
     * @param operations The operations
     */
    protected AbstractSpecificationInterceptor(RepositoryOperations operations) {
        super(operations);
        RuntimeEntityRegistry runtimeEntityRegistry = operations.getApplicationContext().getBean(RuntimeEntityRegistry.class);
        this.criteriaBuilder = new RuntimeCriteriaBuilder(runtimeEntityRegistry);
        storedQueryResolver = operations instanceof StoredQueryResolver ? (StoredQueryResolver) operations : new DefaultStoredQueryResolver() {
            @Override
            protected HintsCapableRepository getHintsCapableRepository() {
                return operations;
            }
        };
    }

    protected final <E, QR> PreparedQuery<E, QR> preparedQueryForCriteria(RepositoryMethodKey methodKey,
                                                                          MethodInvocationContext<T, R> context,
                                                                          Type type) {

        Class<Object> rootEntity = getRequiredRootEntity(context);
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

        QueryResult queryResult;

        if (type == Type.COUNT || type == Type.FIND_ALL || type == Type.FIND_ONE || type == Type.FIND_PAGE) {
            QuerySpecification<Object> specification = getQuerySpecification(context);
            PersistentEntityCriteriaQuery<Object> criteriaQuery = criteriaBuilder.createQuery();
            Root<Object> root = criteriaQuery.from(rootEntity);

            if (specification != null) {
                Predicate predicate = specification.toPredicate(root, criteriaQuery, criteriaBuilder);
                if (predicate != null) {
                    criteriaQuery.where(predicate);
                }
            }

            if (type == Type.FIND_ALL) {
                for (Object param : context.getParameterValues()) {
                    if (param instanceof Sort) {
                        Sort sort = (Sort) param;
                        if (sort.isSorted()) {
                            criteriaQuery.orderBy(getOrders(sort, root, criteriaBuilder));
                            break;
                        }
                    }
                }
            } else if (type == Type.COUNT) {
                criteriaQuery.select(criteriaBuilder.count(root));
            }

            queryResult = ((QueryResultPersistentEntityCriteriaQuery) criteriaQuery).buildQuery(sqlQueryBuilder);
        } else if (type == Type.DELETE_ALL) {
            DeleteSpecification<Object> specification = getDeleteSpecification(context);
            PersistentEntityCriteriaDelete<Object> criteriaDelete = criteriaBuilder.createCriteriaDelete(rootEntity);
            Root<Object> root = criteriaDelete.from(rootEntity);
            if (specification != null) {
                Predicate predicate = specification.toPredicate(root, criteriaDelete, criteriaBuilder);
                if (predicate != null) {
                    criteriaDelete.where(predicate);
                }
            }
            queryResult = ((QueryResultPersistentEntityCriteriaQuery) criteriaDelete).buildQuery(sqlQueryBuilder);
        } else if (type == Type.UPDATE_ALL) {
            UpdateSpecification<Object> specification = getUpdateSpecification(context);
            PersistentEntityCriteriaUpdate<Object> criteriaUpdate = criteriaBuilder.createCriteriaUpdate(rootEntity);
            Root<Object> root = criteriaUpdate.from(rootEntity);
            if (specification != null) {
                Predicate predicate = specification.toPredicate(root, criteriaUpdate, criteriaBuilder);
                if (predicate != null) {
                    criteriaUpdate.where(predicate);
                }
            }
            queryResult = ((QueryResultPersistentEntityCriteriaQuery) criteriaUpdate).buildQuery(sqlQueryBuilder);
        } else {
            throw new IllegalStateException("Unknown criteria type: " + type);
        }

        String query = queryResult.getQuery();
        String update = queryResult.getUpdate();
        List<io.micronaut.data.model.query.builder.QueryParameterBinding> parameterBindings = queryResult.getParameterBindings();

        List<QueryParameterBinding> queryParameters = new ArrayList<>(parameterBindings.size());
        for (io.micronaut.data.model.query.builder.QueryParameterBinding p : parameterBindings) {
            queryParameters.add(
                    new QueryResultParameterBinding(p, queryParameters)
            );
        }

        String[] queryParts = queryParameters.stream().anyMatch(QueryParameterBinding::isExpandable) ? queryResult.getQueryParts().toArray(new String[0]) : null;

        StoredQuery<E, QR> storedQuery;
        if (type == Type.COUNT) {
            storedQuery = (StoredQuery<E, QR>) storedQueryResolver.createCountStoredQuery(context.getExecutableMethod(), DataMethod.OperationType.COUNT, context.getName(), context.getAnnotationMetadata(),
                    rootEntity, query, queryParts, queryParameters);
        } else if (type == Type.FIND_ALL) {
            storedQuery = storedQueryResolver.createStoredQuery(context.getExecutableMethod(), DataMethod.OperationType.QUERY, context.getName(), context.getAnnotationMetadata(), rootEntity,
                    query, null, queryParts, queryParameters, !pageable.isUnpaged(), false);
        } else {
            DataMethod.OperationType operationType;
            switch (type) {
                case COUNT:
                    operationType = DataMethod.OperationType.COUNT;
                    break;
                case DELETE_ALL:
                    operationType = DataMethod.OperationType.DELETE;
                    break;
                case UPDATE_ALL:
                    operationType = DataMethod.OperationType.UPDATE;
                    break;
                case FIND_ALL:
                case FIND_ONE:
                case FIND_PAGE:
                    operationType = DataMethod.OperationType.QUERY;
                    break;
                default:
                    throw new IllegalStateException("Unknown value: " + type);
            }
            storedQuery = storedQueryResolver.createStoredQuery(context.getExecutableMethod(), operationType, context.getName(), context.getAnnotationMetadata(), rootEntity,
                    query, update, queryParts, queryParameters, false, true);
        }
        return preparedQueryResolver.resolveQuery(context, storedQuery, pageable);
    }

    /**
     * Find {@link io.micronaut.data.repository.jpa.criteria.QuerySpecification} in context.
     *
     * @param context The context
     * @return found specification
     */
    @Nullable
    protected QuerySpecification<Object> getQuerySpecification(MethodInvocationContext<?, ?> context) {
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
     */
    @Nullable
    protected DeleteSpecification<Object> getDeleteSpecification(MethodInvocationContext<?, ?> context) {
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
     */
    @Nullable
    protected UpdateSpecification<Object> getUpdateSpecification(MethodInvocationContext<?, ?> context) {
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
            return ClassUtils.forName(p.getConverterClassName(), null).get();
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
