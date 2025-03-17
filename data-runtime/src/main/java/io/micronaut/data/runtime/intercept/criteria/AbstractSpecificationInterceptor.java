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
import io.micronaut.data.model.CursoredPageable;
import io.micronaut.data.model.Pageable;
import io.micronaut.data.model.Pageable.Mode;
import io.micronaut.data.model.PersistentEntity;
import io.micronaut.data.model.Sort;
import io.micronaut.data.model.jpa.criteria.PersistentEntityCriteriaQuery;
import io.micronaut.data.model.jpa.criteria.PersistentEntityFrom;
import io.micronaut.data.model.jpa.criteria.PersistentEntityRoot;
import io.micronaut.data.model.query.JoinPath;
import io.micronaut.data.model.query.builder.QueryBuilder;
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
import io.micronaut.data.runtime.operations.internal.sql.DefaultSqlPreparedQuery;
import jakarta.persistence.Tuple;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaDelete;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.CriteriaUpdate;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.From;
import jakarta.persistence.criteria.Order;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Selection;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

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
    protected final CriteriaBuilder criteriaBuilder;
    private final Map<RepositoryMethodKey, QueryBuilder> sqlQueryBuilderForRepositories = new ConcurrentHashMap<>();
    private final Map<RepositoryMethodKey, Set<JoinPath>> methodsJoinPaths = new ConcurrentHashMap<>();

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
        } else {
            criteriaRepositoryOperations = null;
            criteriaBuilder = operations.getApplicationContext().getBean(RuntimeCriteriaBuilder.class);
        }
    }

    final CriteriaRepositoryOperations getCriteriaRepositoryOperations(RepositoryMethodKey methodKey,
                                                                       MethodInvocationContext<?, ?> context,
                                                                       Pageable pageable) {
        if (criteriaRepositoryOperations != null) {
            return criteriaRepositoryOperations;
        }
        QueryBuilder sqlQueryBuilder = getQueryBuilder(methodKey, context);
        return new PreparedQueryCriteriaRepositoryOperations(
            criteriaBuilder,
            operations,
            context,
            sqlQueryBuilder,
            getRequiredRootEntity(context),
            pageable
        );
    }

    protected final <T> List<T> findAll(RepositoryMethodKey methodKey, MethodInvocationContext<?, ?> context, Pageable pageable, CriteriaQuery<T> criteriaQuery) {
        pageable = applyPaginationAndSort(pageable, criteriaQuery, false);
        if (criteriaRepositoryOperations != null) {
            if (pageable != null) {
                if (pageable.getMode() != Mode.OFFSET) {
                    throw new UnsupportedOperationException("Pageable mode " + pageable.getMode() + " is not supported by hibernate operations");
                }
                return criteriaRepositoryOperations.findAll(criteriaQuery, (int) pageable.getOffset(), pageable.getSize());
            }
            int offset = getOffset(context);
            int limit = getLimit(context);
            if (offset > 0 || limit > 0) {
                return criteriaRepositoryOperations.findAll(criteriaQuery, offset, limit);
            }
            return criteriaRepositoryOperations.findAll(criteriaQuery);
        }
        return getCriteriaRepositoryOperations(methodKey, context, pageable).findAll(criteriaQuery);
    }

    protected final Set<JoinPath> getMethodJoinPaths(RepositoryMethodKey methodKey, MethodInvocationContext<?, ?> context) {
        return methodsJoinPaths.computeIfAbsent(methodKey, repositoryMethodKey ->
            AssociationUtils.getJoinPaths(context));
    }

    @NonNull
    @Override
    protected Pageable getPageable(MethodInvocationContext<?, ?> context) {
        for (Object param : context.getParameterValues()) {
            if (param instanceof Pageable pageable) {
                return pageable;
            }
        }
        for (Object param : context.getParameterValues()) {
            if (param instanceof Sort sort) {
                return Pageable.UNPAGED.orders(sort.getOrderBy());
            }
        }
        return Pageable.UNPAGED;
    }

    @NonNull
    protected final QueryBuilder getQueryBuilder(RepositoryMethodKey methodKey, MethodInvocationContext<?, ?> context) {
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

    @NonNull
    protected final <E> CriteriaQuery<E> buildExistsQuery(RepositoryMethodKey methodKey, MethodInvocationContext<?, ?> context) {
        return this.<E>getCriteriaQueryBuilder(context, getMethodJoinPaths(methodKey, context)).build(criteriaBuilder);
    }

    @NonNull
    protected final <E> CriteriaUpdate<E> buildUpdateQuery(MethodInvocationContext<?, ?> context) {
        return this.<E>getCriteriaUpdateBuilder(context).build(criteriaBuilder);
    }

    @NonNull
    protected final <E> CriteriaDelete<E> buildDeleteQuery(MethodInvocationContext<?, ?> context) {
        return this.<E>getCriteriaDeleteBuilder(context).build(criteriaBuilder);
    }

    @NonNull
    protected final CriteriaQuery<Long> buildCountQuery(RepositoryMethodKey methodKey, MethodInvocationContext<?, ?> context) {
        return getCountCriteriaQueryBuilder(context, getMethodJoinPaths(methodKey, context)).build(criteriaBuilder);
    }

    @NonNull
    protected final <N> CriteriaQuery<N> buildQuery(RepositoryMethodKey methodKey, MethodInvocationContext<?, ?> context) {
        return this.<N>getCriteriaQueryBuilder(context, getMethodJoinPaths(methodKey, context)).build(criteriaBuilder);
    }

    private <N> void appendSort(Sort sort, CriteriaQuery<N> criteriaQuery, Root<?> root) {
        if (sort.isSorted()) {
            criteriaQuery.orderBy(getOrders(sort, root, criteriaBuilder));
        }
    }

    protected final Pageable applyPaginationAndSort(Pageable pageable, CriteriaQuery<?> criteriaQuery, boolean singleResult) {
        Root<?> root = criteriaQuery.getRoots().stream().findFirst().orElseThrow(() -> new IllegalStateException("The root not found!"));
        if (pageable instanceof CursoredPageable cursored) {
            cursored = DefaultSqlPreparedQuery.enhancePageable(cursored, getPersistentEntity(root));
            pageable = cursored;
            buildCursorPagination(criteriaQuery, criteriaBuilder, cursored);
        }
        appendSort(pageable.getSort(), criteriaQuery, root);
        pageable = pageable.withoutSort();
        if (singleResult && pageable.getOffset() > 0) {
            pageable = Pageable.from(pageable.getNumber(), 1);
        }
        if (pageable.isUnpaged()) {
            return pageable;
        }
        if (criteriaQuery instanceof PersistentEntityCriteriaQuery<?> persistentEntityCriteriaQuery) {
            // For Micronaut Criteria we can create a direct query with pagination
            long offset = pageable.getMode() == Pageable.Mode.OFFSET ? pageable.getOffset() : 0;
            long limit = pageable.getSize();
            if (offset > 0) {
                persistentEntityCriteriaQuery.offset((int) offset);
            }
            if (limit > 0) {
                persistentEntityCriteriaQuery.limit((int) limit);
            }
            return Pageable.UNPAGED;
        }
        return pageable;
    }

    private void buildCursorPagination(CriteriaQuery<?> criteriaQuery,
                                       CriteriaBuilder criteriaBuilder,
                                       CursoredPageable cursoredPageable) {
        if (cursoredPageable.cursor().isEmpty()) {
            return;
        }
        Pageable.Cursor cursor = cursoredPageable.cursor().get();
        Sort sort = cursoredPageable.getSort();
        List<Sort.Order> orders = sort.getOrderBy();
        if (orders.size() != cursor.size()) {
            throw new IllegalArgumentException("The cursor must match the sorting size");
        }
        if (orders.isEmpty()) {
            throw new IllegalArgumentException("At least one sorting property must be supplied");
        }

        Root<?> root = criteriaQuery.getRoots().iterator().next();
        List<Predicate> orPredicates = new ArrayList<>(orders.size());
        for (int i = 0; i < orders.size(); ++i) {
            List<Predicate> andPredicates = new ArrayList<>(orders.size());
            for (int j = 0; j <= i; ++j) {
                String propertyName = orders.get(j).getProperty();
                Predicate predicate;
                Object value = cursor.get(i);
                if (orders.get(i).isAscending()) {
                    if (i == j) {
                        predicate = criteriaBuilder.greaterThan(root.<Comparable>get(propertyName), (Comparable) value);
                    } else {
                        predicate = criteriaBuilder.equal(root.get(propertyName), value);
                    }
                } else {
                    if (i == j) {
                        predicate = criteriaBuilder.lessThan(root.<Comparable>get(propertyName), (Comparable) value);
                    } else {
                        predicate = criteriaBuilder.equal(root.get(propertyName), value);
                    }
                }
                andPredicates.add(predicate);
            }
            orPredicates.add(
                criteriaBuilder.and(andPredicates.toArray(new Predicate[0]))
            );
        }
        Predicate predicate = criteriaBuilder.or(orPredicates.toArray(new Predicate[0]));
        Predicate restriction = criteriaQuery.getRestriction();
        if (restriction == null) {
            criteriaQuery.where(predicate);
        } else {
            criteriaQuery.where(criteriaBuilder.and(restriction, predicate));
        }
    }

    protected final CriteriaQuery<Tuple> buildIdsQuery(RepositoryMethodKey methodKey,
                                                       MethodInvocationContext<?, ?> context,
                                                       Sort sort) {
        return getIdsCriteriaQueryBuilder(context, getMethodJoinPaths(methodKey, context), sort).build(criteriaBuilder);
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
        if (parameterValue instanceof QuerySpecification<?> querySpecification) {
            return (QuerySpecification<K>) querySpecification;
        }
        if (parameterValue instanceof PredicateSpecification<?> predicateSpecification) {
            return (QuerySpecification<K>) QuerySpecification.where(predicateSpecification);
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
    protected final <K> CriteriaQueryBuilder<K> getCriteriaQueryBuilder(MethodInvocationContext<?, ?> context, Set<JoinPath> joinPaths) {
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
                for (JoinPath joinPath : sortJoinPaths(joinPaths)) {
                    join(root, joinPath);
                }
            }
            return criteriaQuery;
        };
    }

    @NonNull
    protected final CriteriaQueryBuilder<Tuple> getIdsCriteriaQueryBuilder(MethodInvocationContext<?, ?> context,
                                                                           Set<JoinPath> joinPaths,
                                                                           Sort sort) {
        final Object parameterValue = context.getParameterValues()[0];
        if (parameterValue instanceof CriteriaQueryBuilder<?>) {
            throw new IllegalStateException("Criteria pagination doesn't support CriteriaQueryBuilder!");
        }
        return criteriaBuilder -> createSelectIdsCriteriaQuery(context, joinPaths, sort);
    }

    @NonNull
    private <K> CriteriaQuery<Tuple> createSelectIdsCriteriaQuery(MethodInvocationContext<?, ?> context,
                                                                  Set<JoinPath> joinPaths,
                                                                  Sort sort) {
        Class<K> rootEntity = getRequiredRootEntity(context);
        QuerySpecification<K> specification = getQuerySpecification(context);
        CriteriaQuery<Tuple> criteriaQuery = criteriaBuilder.createTupleQuery();
        Root<K> root = criteriaQuery.from(rootEntity);
        List<Selection<?>> selection = new ArrayList<>();
        selection.add(getIdExpression(root));
        // We need to select all ordered properties from ORDER BY for DISTINCT to work properly
        for (Sort.Order order : sort.getOrderBy()) {
            Path<?> path = root;
            for (Iterator<String> iterator = StringUtils.splitOmitEmptyStrings(order.getProperty(), '.').iterator(); iterator.hasNext(); ) {
                String next = iterator.next();
                if (iterator.hasNext()) {
                    path = ((From<?, ?>) path).join(next);
                } else {
                    path = path.get(next);
                }
            }
            selection.add(path);
        }
        criteriaQuery.multiselect(selection).distinct(true);
        if (specification != null) {
            Predicate predicate = specification.toPredicate(root, criteriaQuery, criteriaBuilder);
            if (predicate != null) {
                criteriaQuery.where(predicate);
            }
        }
        if (CollectionUtils.isNotEmpty(joinPaths)) {
            for (JoinPath joinPath : sortJoinPaths(joinPaths)) {
                join(root, joinPath);
            }
        }
        return criteriaQuery;
    }

    /**
     * Find {@link io.micronaut.data.repository.jpa.criteria.CriteriaQueryBuilder}
     * or {@link io.micronaut.data.repository.jpa.criteria.QuerySpecification} in context.
     *
     * @param context   The context
     * @param joinPaths The join fetch paths
     * @return found specification
     */
    @NonNull
    private CriteriaQueryBuilder<Long> getCountCriteriaQueryBuilder(MethodInvocationContext<?, ?> context, Set<JoinPath> joinPaths) {
        final Object parameterValue = context.getParameterValues()[0];
        if (parameterValue instanceof CriteriaQueryBuilder<?> providedCriteriaQueryBuilder) {
            return new CriteriaQueryBuilder<Long>() {
                @Override
                public CriteriaQuery<Long> build(CriteriaBuilder criteriaBuilder) {
                    CriteriaQuery<?> criteriaQuery = providedCriteriaQueryBuilder.build(criteriaBuilder);
                    Root<?> root = criteriaQuery.getRoots().iterator().next();
                    Expression countExpression;
                    if (!root.getJoins().isEmpty() || !joinPaths.isEmpty()) {
                        countExpression = criteriaBuilder.countDistinct(getIdExpression(root));
                    } else {
                        countExpression = criteriaBuilder.count(getIdExpression(root));
                    }
                    return criteriaQuery.select(countExpression);
                }
            };
        }
        return criteriaBuilder -> createPageCountCriteriaQuery(context, criteriaBuilder, joinPaths);
    }

    private <E> CriteriaQuery createPageCountCriteriaQuery(MethodInvocationContext<?, ?> context,
                                                           CriteriaBuilder criteriaBuilder,
                                                           Set<JoinPath> joinPaths) {
        Class<E> rootEntity = getRequiredRootEntity(context);
        QuerySpecification<E> specification = getQuerySpecification(context);
        CriteriaQuery criteriaQuery = criteriaBuilder.createQuery(Long.class);
        Root<E> root = criteriaQuery.from(rootEntity);
        if (specification != null) {
            Predicate predicate = specification.toPredicate(root, criteriaQuery, criteriaBuilder);
            if (predicate != null) {
                criteriaQuery.where(predicate);
            }
        }
        Expression countExpression;
        if (!root.getJoins().isEmpty() || !joinPaths.isEmpty()) {
            countExpression = criteriaBuilder.countDistinct(getIdExpression(root));
        } else {
            countExpression = criteriaBuilder.count(getIdExpression(root));
        }
        return criteriaQuery.select(countExpression);
    }

    protected final Expression<?> getIdExpression(Root<?> root) {
        if (root instanceof PersistentEntityRoot<?> persistentEntityRoot) {
            return persistentEntityRoot.id();
        } else {
            return root.get(getPersistentEntity(root).getIdentity().getName());
        }
    }

    final PersistentEntity getPersistentEntity(Root<?> root) {
        if (root instanceof PersistentEntityRoot<?> persistentEntityRoot) {
            return persistentEntityRoot.getPersistentEntity();
        } else {
            return operations.getEntity(root.getModel().getJavaType());
        }
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
            for (Iterator<String> iterator = StringUtils.splitOmitEmptyStrings(order.getProperty(), '.').iterator(); iterator.hasNext(); ) {
                String next = iterator.next();
                if (iterator.hasNext()) {
                    path = ((From<?, ?>) path).join(next);
                } else {
                    path = path.get(next);
                }
            }
            Expression<?> expression = order.isIgnoreCase() ? cb.lower(path.type().as(String.class)) : path;
            orders.add(order.isAscending() ? cb.asc(expression) : cb.desc(expression));
        }
        return orders;
    }

    protected enum Type {
        COUNT, FIND_ONE, FIND_PAGE, FIND_ALL, DELETE_ALL, UPDATE_ALL, EXISTS
    }

    private List<JoinPath> sortJoinPaths(Collection<JoinPath> joinPaths) {
        List<JoinPath> sortedJoinPaths = new ArrayList<>(joinPaths);
        sortedJoinPaths.sort((o1, o2) -> Comparator.comparingInt(String::length).thenComparing(String::compareTo).compare(o1.getPath(), o2.getPath()));
        return sortedJoinPaths;
    }
}
