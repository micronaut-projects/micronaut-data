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
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.Internal;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import io.micronaut.core.beans.BeanIntrospection;
import io.micronaut.core.type.Argument;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.core.util.StringUtils;
import io.micronaut.data.annotation.First;
import io.micronaut.data.annotation.OrderBy;
import io.micronaut.data.annotation.Projection;
import io.micronaut.data.annotation.RepositoryConfiguration;
import io.micronaut.data.annotation.TypeRole;
import io.micronaut.data.intercept.RepositoryMethodKey;
import io.micronaut.data.intercept.annotation.DataMethod;
import io.micronaut.data.intercept.annotation.DataMethodQuery;
import io.micronaut.data.model.AssociationUtils;
import io.micronaut.data.model.CursoredPageable;
import io.micronaut.data.model.Limit;
import io.micronaut.data.model.Embedded;
import io.micronaut.data.model.Pageable;
import io.micronaut.data.model.Pageable.Mode;
import io.micronaut.data.model.PersistentEntity;
import io.micronaut.data.model.Sort;
import io.micronaut.data.model.jd.SpecificationConstraint;
import io.micronaut.data.model.jpa.criteria.PersistentEntityCriteriaQuery;
import io.micronaut.data.model.jpa.criteria.PersistentEntityFrom;
import io.micronaut.data.model.jpa.criteria.PersistentEntityRoot;
import io.micronaut.data.model.jpa.criteria.PersistentPropertyPath;
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
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Order;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Selection;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Iterator;
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

    @Nullable
    protected final CriteriaRepositoryOperations criteriaRepositoryOperations;
    protected CriteriaBuilder criteriaBuilder;
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

    @Override
    protected Pageable getPageable(MethodInvocationContext<?, ?> context) {
        Pageable pageable = super.getPageable(context);
        List<Sort.Order> orders = getOrders(context);
        if (!orders.isEmpty()) {
            pageable = pageable.orders(orders);
        }
        return pageable;
    }

    private List<Sort.Order> getOrders(MethodInvocationContext<?, ?> context) {
        List<Sort.Order> orders =
            context.getExecutableMethod().getAnnotationValuesByStereotype(OrderBy.class.getName())
                .stream()
                .map(av -> new Sort.Order(
                    av.stringValue().orElseThrow(),
                    av.booleanValue("descending").orElse(false) ? Sort.Order.Direction.DESC : Sort.Order.Direction.ASC,
                    av.booleanValue("ignoreCase").orElse(false)
                ))
                .toList();
        return orders;
    }

    final CriteriaRepositoryOperations getCriteriaRepositoryOperations(RepositoryMethodKey methodKey,
                                                                       MethodInvocationContext<?, ?> context,
                                                                       @Nullable
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

    protected final <B> List<B> findAll(RepositoryMethodKey methodKey, MethodInvocationContext<?, ?> context, Pageable pageable, CriteriaQuery<B> criteriaQuery) {
        pageable = applyPaginationAndSort(pageable, criteriaQuery, false);
        if (criteriaRepositoryOperations != null) {
            Limit limit = Limit.UNLIMITED;
            if (pageable != null) {
                limit = pageable.getLimit();
                if (pageable.getMode() != Mode.OFFSET) {
                    throw new UnsupportedOperationException("Pageable mode " + pageable.getMode() + " is not supported by hibernate operations");
                }
            }
            if (!limit.isLimited()) {
                limit = getParameterInRole(context, TypeRole.LIMIT, Limit.class).orElse(limit);
            }
            if (!limit.isLimited()) {
                int offset = getOffset(context);
                int maxResults = getLimit(context);
                limit = Limit.of(maxResults, offset);
            }
            if (!limit.isLimited()) {
                AnnotationValue<First> annotation = context.getAnnotationMetadata().getAnnotation(First.class);
                if (annotation != null) {
                    limit = Limit.of(annotation.intValue().orElse(1), 0);
                }
            }
            if (limit.isLimited()) {
                return criteriaRepositoryOperations.findAll(criteriaQuery, (int) limit.offset(), limit.maxResults());
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
    protected final CriteriaQuery<Object> buildExistsQuery(RepositoryMethodKey methodKey, MethodInvocationContext<?, ?> context) {
        return this.getCriteriaQueryBuilder(context, getMethodJoinPaths(methodKey, context), getRequiredRootEntity(context)).build(criteriaBuilder);
    }

    @NonNull
    protected final <E> CriteriaUpdate<E> buildUpdateQuery(MethodInvocationContext<?, ?> context) {
        return this.<E>getCriteriaUpdateBuilder(context, getRequiredRootEntity(context)).build(criteriaBuilder);
    }

    @NonNull
    protected final <E> CriteriaDelete<E> buildDeleteQuery(MethodInvocationContext<?, ?> context) {
        return this.<E>getCriteriaDeleteBuilder(context, getRequiredRootEntity(context)).build(criteriaBuilder);
    }

    @NonNull
    protected final CriteriaQuery<Long> buildCountQuery(RepositoryMethodKey methodKey, MethodInvocationContext<?, ?> context) {
        return getCountCriteriaQueryBuilder(context, getMethodJoinPaths(methodKey, context), getRequiredRootEntity(context)).build(criteriaBuilder);
    }

    @NonNull
    protected final CriteriaQuery<Object> buildQuery(RepositoryMethodKey methodKey, MethodInvocationContext<?, ?> context) {
        return this.getCriteriaQueryBuilder(context, getMethodJoinPaths(methodKey, context), getRequiredRootEntity(context)).build(criteriaBuilder);
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
        return getIdsCriteriaQueryBuilder(context, getMethodJoinPaths(methodKey, context), sort, getRequiredRootEntity(context)).build(criteriaBuilder);
    }

    /**
     * Find {@link io.micronaut.data.repository.jpa.criteria.QuerySpecification} in context.
     *
     * @param context    The context
     * @param rootEntity The root entity
     * @param <K>        the specification entity root type
     * @return found specification
     */
    @Nullable
    protected <K> QuerySpecification<K> getQuerySpecification(MethodInvocationContext<?, ?> context, Class<?> rootEntity) {
        Optional<PredicateSpecification> predicateSpecification = getPredicateSpecification(context, rootEntity);
        if (predicateSpecification.isPresent()) {
            return (QuerySpecification<K>) QuerySpecification.where(predicateSpecification.get());
        }
        Optional<QuerySpecification> spec = getParameterInRole(context, TypeRole.SPECIFICATION_PREDICATE, Argument.of(QuerySpecification.class, rootEntity));
        if (spec.isPresent()) {
            return spec.get();
        }
        spec = getParameterInRole(context, TypeRole.SPECIFICATION_QUERY, Argument.of(QuerySpecification.class, rootEntity));
        if (spec.isPresent()) {
            return spec.get();
        }
        if (context.getArguments()[0].isNullable()) {
            return null;
        }
        throw new IllegalArgumentException("Specification may not be null.");
    }

    private Optional<PredicateSpecification> getPredicateSpecification(MethodInvocationContext<?, ?> context, Class<?> rootEntity) {
        List<PredicateSpecification> predicates =
            CollectionUtils.concat(
                getParametersInRole(context, TypeRole.SPECIFICATION_PREDICATE, Argument.of(PredicateSpecification.class, rootEntity)),
                getSpecificationConstraints(context)
            );
        if (predicates.isEmpty()) {
            return Optional.empty();
        }
        if (predicates.size() == 1) {
            return Optional.of(predicates.get(0));
        }
        List<PredicateSpecification> finalPredicates = predicates;
        return Optional.of(new PredicateSpecification() {
            @Override
            public Predicate toPredicate(Root root, CriteriaBuilder criteriaBuilder) {
                List<Predicate> andPredicates = new ArrayList<>(finalPredicates.size());
                for (PredicateSpecification<?> predicate : finalPredicates) {
                    andPredicates.add(predicate.toPredicate(root, criteriaBuilder));
                }
                return criteriaBuilder.and(andPredicates.toArray(new Predicate[0]));
            }
        });
    }

    @NonNull
    private List<PredicateSpecification> getSpecificationConstraints(@NonNull MethodInvocationContext<?, ?> methodContext) {
        AnnotationValue<Annotation> annotation = methodContext.getAnnotation(DataMethod.NAME);
        if (annotation == null) {
            return List.of();
        }
        List<AnnotationValue<Annotation>> roles = annotation.getAnnotations(DataMethodQuery.META_MEMBER_PARAMETERS_TYPE_ROLES);
        return roles.stream()
            .filter(a -> a.stringValue().orElseThrow().equals(TypeRole.SPECIFICATION_CONSTRAINT))
            .flatMap(a -> {
                int parameterIndex = a.intValue("parameterIndex").orElseThrow();
                Argument<?> argument = methodContext.getArguments()[parameterIndex];
                Object value = methodContext.getParameterValues()[parameterIndex];
                return conversionService.convert(new SpecificationConstraint(argument, value), PredicateSpecification.class).stream();
            }).toList();
    }

    /**
     * Find {@link io.micronaut.data.repository.jpa.criteria.CriteriaQueryBuilder}
     * or {@link io.micronaut.data.repository.jpa.criteria.QuerySpecification} in context.
     *
     * @param context    The context
     * @param joinPaths  The join fetch paths
     * @param rootEntity The join fetch paths
     * @param <K>        the result type
     * @return found specification
     */
    @NonNull
    protected final <K> CriteriaQueryBuilder<Object> getCriteriaQueryBuilder(MethodInvocationContext<?, ?> context, Set<JoinPath> joinPaths, Class<K> rootEntity) {
        Optional<CriteriaQueryBuilder> queryBuilder = getParameterInRole(context, TypeRole.SPECIFICATION_QUERY, Argument.of(CriteriaQueryBuilder.class, rootEntity));
        if (queryBuilder.isPresent()) {
            return queryBuilder.get();
        }
        return criteriaBuilder -> {
            List<AnnotationValue<Projection>> projections = context.getAnnotationValuesByType(Projection.class);
            AnnotationValue<DataMethod> dataAnnotation = context.getAnnotation(DataMethod.class);
            boolean isDto = dataAnnotation != null && dataAnnotation.isTrue(DataMethod.META_MEMBER_DTO);
            QuerySpecification<K> specification = getQuerySpecification(context, rootEntity);
            CriteriaQuery<Object> criteriaQuery;
            if (isDto || !projections.isEmpty()) {
                Class<?> dtoClass = dataAnnotation != null ? dataAnnotation.classValue(DataMethod.META_MEMBER_RESULT_TYPE).orElse(Object.class) : Object.class;
                criteriaQuery = (CriteriaQuery<Object>) criteriaBuilder.createQuery(dtoClass);
            } else {
                criteriaQuery = (CriteriaQuery<Object>) criteriaBuilder.createQuery(rootEntity);
            }
            //
            Root<K> root = criteriaQuery.from(rootEntity);
            if (CollectionUtils.isNotEmpty(joinPaths)) {
                for (JoinPath joinPath : sortJoinPaths(joinPaths)) {
                    join(root, joinPath);
                }
            }
            if (specification != null) {
                Predicate predicate = specification.toPredicate(root, criteriaQuery, criteriaBuilder);
                if (predicate != null) {
                    criteriaQuery.where(predicate);
                }
            }
            if (!projections.isEmpty()) {
                if (projections.size() == 1) {
                    criteriaQuery.select(
                        root.get(projections.getFirst().stringValue().orElseThrow())
                    );
                } else {
                    criteriaQuery.multiselect(
                        projections.stream()
                            .map(av -> root.get(av.stringValue().orElseThrow()))
                            .toArray(Selection[]::new)
                    );
                }
            }
            List<Sort.Order> orders = getOrders(context);
            if (!orders.isEmpty()) {
                criteriaQuery.orderBy(getOrders(Sort.of(orders), root, criteriaBuilder));
            }
            return criteriaQuery;
        };
    }

    @NonNull
    protected final CriteriaQueryBuilder<Tuple> getIdsCriteriaQueryBuilder(MethodInvocationContext<?, ?> context,
                                                                           Set<JoinPath> joinPaths,
                                                                           Sort sort,
                                                                           Class<?> rootEntity) {
        Optional<CriteriaQueryBuilder> queryBuilder = getParameterInRole(context, TypeRole.SPECIFICATION_QUERY, Argument.of(CriteriaQueryBuilder.class, rootEntity));
        if (queryBuilder.isPresent()) {
            throw new IllegalStateException("Criteria pagination doesn't support CriteriaQueryBuilder!");
        }
        return criteriaBuilder -> createSelectIdsCriteriaQuery(context, joinPaths, sort, rootEntity);
    }

    @NonNull
    private <K> CriteriaQuery<Tuple> createSelectIdsCriteriaQuery(MethodInvocationContext<?, ?> context,
                                                                  Set<JoinPath> joinPaths,
                                                                  Sort sort,
                                                                  Class<K> rootEntity) {
        CriteriaQuery<Tuple> criteriaQuery = criteriaBuilder.createTupleQuery();
        Root<K> root = criteriaQuery.from(rootEntity);
        if (CollectionUtils.isNotEmpty(joinPaths)) {
            for (JoinPath joinPath : sortJoinPaths(joinPaths)) {
                join(root, joinPath);
            }
        }
        QuerySpecification<K> specification = getQuerySpecification(context, rootEntity);
        if (specification != null) {
            Predicate predicate = specification.toPredicate(root, criteriaQuery, criteriaBuilder);
            if (predicate != null) {
                criteriaQuery.where(predicate);
            }
        }

        criteriaQuery.multiselect(
            createSelectionForSelectingIds(sort, root)
        ).distinct(true);
        return criteriaQuery;
    }

    private <K> List<Selection<?>> createSelectionForSelectingIds(Sort sort, Root<K> root) {
        // We select IDs and all ordered properties from ORDER BY for DISTINCT to work properly
        List<Selection<?>> selection = new ArrayList<>();
        selection.add(getIdExpression(root));
        List<Sort.Order> orders = new ArrayList<>(sort.getOrderBy());
        for (Join<K, ?> join : root.getJoins()) {
            // First, we check if the order is for one of the joined entities
            for (Sort.Order order : sort.getOrderBy()) {
                Iterator<String> orderIterator = StringUtils.splitOmitEmptyStrings(order.getProperty(), '.').iterator();
                if (!orderIterator.hasNext()) {
                    continue;
                }
                String firstPath = orderIterator.next();
                if (join instanceof PersistentPropertyPath<?> persistentPropertyPath) {
                    if (!persistentPropertyPath.getProperty().getName().equals(firstPath)) {
                        continue;
                    }
                } else if (!join.getAttribute().getName().equals(firstPath)) {
                    continue;
                }
                orders.remove(order);
                Path<?> path = join;
                while (orderIterator.hasNext()) {
                    path = path.get(orderIterator.next());
                }
                selection.add(path);
            }
        }
        for (Sort.Order order : orders) {
            // Remaining orders must be for the root entity
            Path<?> path = root;
            for (String orderPath : StringUtils.splitOmitEmptyStrings(order.getProperty(), '.')) {
                path = path.get(orderPath);
            }
            selection.add(path);
        }
        return selection;
    }

    /**
     * Find {@link io.micronaut.data.repository.jpa.criteria.CriteriaQueryBuilder}
     * or {@link io.micronaut.data.repository.jpa.criteria.QuerySpecification} in context.
     *
     * @param context    The context
     * @param joinPaths  The join fetch paths
     * @param rootEntity The root entity
     * @return found specification
     */
    @NonNull
    protected CriteriaQueryBuilder<Long> getCountCriteriaQueryBuilder(MethodInvocationContext<?, ?> context, Set<JoinPath> joinPaths, Class<?> rootEntity) {
        Optional<CriteriaQueryBuilder> queryBuilder = getParameterInRole(context, TypeRole.SPECIFICATION_QUERY, Argument.of(CriteriaQueryBuilder.class, rootEntity));
        if (queryBuilder.isPresent()) {
            return new CriteriaQueryBuilder<Long>() {
                @Override
                public CriteriaQuery<Long> build(CriteriaBuilder criteriaBuilder) {
                    CriteriaQuery<?> criteriaQuery = queryBuilder.get().build(criteriaBuilder);
                    Root<?> root = criteriaQuery.getRoots().iterator().next();
                    Expression countExpression;
                    PersistentEntity entity = getPersistentEntity(root);
                    boolean countOnRoot = entity.hasCompositeIdentity() || (entity.hasIdentity() && entity.getIdentity() instanceof Embedded);
                    if (!root.getJoins().isEmpty() || !root.getFetches().isEmpty() || !joinPaths.isEmpty()) {
                        countExpression = criteriaBuilder.countDistinct(countOnRoot ? root : getIdExpression(root));
                    } else {
                        countExpression = criteriaBuilder.count(countOnRoot ? root : getIdExpression(root));
                    }
                    return criteriaQuery.select(countExpression);
                }
            };
        }
        return criteriaBuilder -> createPageCountCriteriaQuery(context, criteriaBuilder, joinPaths, rootEntity);
    }

    private <E> CriteriaQuery<Long> createPageCountCriteriaQuery(MethodInvocationContext<?, ?> context,
                                                                 CriteriaBuilder criteriaBuilder,
                                                                 Set<JoinPath> joinPaths,
                                                                 Class<E> rootEntity) {
        QuerySpecification<E> specification = getQuerySpecification(context, rootEntity);
        CriteriaQuery<Long> criteriaQuery = criteriaBuilder.createQuery(Long.class);
        Root<E> root = criteriaQuery.from(rootEntity);
        if (specification != null) {
            Predicate predicate = specification.toPredicate(root, criteriaQuery, criteriaBuilder);
            if (predicate != null) {
                criteriaQuery.where(predicate);
            }
        }
        Expression<Long> countExpression;
        PersistentEntity entity = getPersistentEntity(root);
        boolean countOnRoot = entity.hasCompositeIdentity() || (entity.hasIdentity() && entity.getIdentity() instanceof Embedded);
        if (!root.getJoins().isEmpty() || !root.getFetches().isEmpty() || !joinPaths.isEmpty()) {
            countExpression = criteriaBuilder.countDistinct(countOnRoot ? root : getIdExpression(root));
        } else {
            countExpression = criteriaBuilder.count(countOnRoot ? root : getIdExpression(root));
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

    private PersistentEntity getPersistentEntity(Root<?> root) {
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
        } else {
            root.join(joinPath.getPath(), toJoinType(joinPath.getJoinType()));
        }
    }

    private JoinType toJoinType(io.micronaut.data.annotation.Join.Type joinType) {
        return switch (joinType) {
            case DEFAULT, FETCH, LEFT, LEFT_FETCH -> JoinType.LEFT;
            case RIGHT, RIGHT_FETCH -> JoinType.RIGHT;
            case INNER, INNER_FETCH -> JoinType.INNER;
            default -> throw new IllegalArgumentException("Unsupported join type: " + joinType);
        };
    }

    /**
     * Find {@link io.micronaut.data.repository.jpa.criteria.DeleteSpecification} in context.
     *
     * @param context    The context
     * @param rootEntity The rootEntity
     * @param <K>        the specification entity root type
     * @return found specification
     */
    @Nullable
    protected <K> DeleteSpecification<K> getDeleteSpecification(MethodInvocationContext<?, ?> context, Class<K> rootEntity) {
        Optional<PredicateSpecification> predicateSpecification = getPredicateSpecification(context, rootEntity);
        if (predicateSpecification.isPresent()) {
            return (DeleteSpecification<K>) DeleteSpecification.where(predicateSpecification.get());
        }
        Optional<DeleteSpecification> spec = getParameterInRole(context, TypeRole.SPECIFICATION_PREDICATE, Argument.of(DeleteSpecification.class, rootEntity));
        if (spec.isPresent()) {
            return spec.get();
        }
        spec = getParameterInRole(context, TypeRole.SPECIFICATION_DELETE, Argument.of(DeleteSpecification.class, rootEntity));
        if (spec.isPresent()) {
            return spec.get();
        }
        Argument<?> parameterArgument = context.getArguments()[0];
        if (parameterArgument.isNullable()) {
            return null;
        }
        throw new IllegalArgumentException("Specification may not be null.");
    }

    /**
     * Find {@link io.micronaut.data.repository.jpa.criteria.CriteriaDeleteBuilder}
     * or {@link io.micronaut.data.repository.jpa.criteria.QuerySpecification} in context.
     *
     * @param context    The context
     * @param rootEntity The rootEntity
     * @param <K>        the result type
     * @return found specification
     */
    @NonNull
    protected <K> CriteriaDeleteBuilder<K> getCriteriaDeleteBuilder(MethodInvocationContext<?, ?> context, Class<K> rootEntity) {
        Optional<CriteriaDeleteBuilder> criteriaDeleteBuilder = getParameterInRole(context, TypeRole.SPECIFICATION_DELETE, CriteriaDeleteBuilder.class);
        if (criteriaDeleteBuilder.isPresent()) {
            return criteriaDeleteBuilder.get();
        }
        return criteriaBuilder -> {
            DeleteSpecification<K> specification = getDeleteSpecification(context, rootEntity);
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
     * @param context    The context
     * @param rootEntity The root entity
     * @param <K>        the specification entity root type
     * @return found specification
     */
    @Nullable
    protected <K> UpdateSpecification<K> getUpdateSpecification(MethodInvocationContext<?, ?> context, Class<K> rootEntity) {
        Optional<UpdateSpecification> spec = getParameterInRole(context, TypeRole.SPECIFICATION_UPDATE, Argument.of(UpdateSpecification.class, rootEntity));
        if (spec.isPresent()) {
            return spec.get();
        }
//        PredicateSpecification<?> predicateSpecification = findPredicateSpecification(parameterValue);
//        if (predicateSpecification != null) {
//            return (UpdateSpecification<K>) UpdateSpecification.where(predicateSpecification);
//        }
        Argument<?> parameterArgument = context.getArguments()[0];
        if (parameterArgument.isNullable()) {
            return null;
        }
        throw new IllegalArgumentException("Specification may not be null.");
    }

    /**
     * Find {@link io.micronaut.data.repository.jpa.criteria.CriteriaUpdateBuilder}
     * or {@link io.micronaut.data.repository.jpa.criteria.QuerySpecification} in context.
     *
     * @param context    The context
     * @param rootEntity The rootEntity
     * @param <K>        the result type
     * @return found specification
     */
    @NonNull
    protected <K> CriteriaUpdateBuilder<K> getCriteriaUpdateBuilder(MethodInvocationContext<?, ?> context, Class<K> rootEntity) {
        Optional<CriteriaUpdateBuilder> criteriaUpdateBuilder = getParameterInRole(context, TypeRole.SPECIFICATION_DELETE, Argument.of(CriteriaUpdateBuilder.class, rootEntity));
        if (criteriaUpdateBuilder.isPresent()) {
            return criteriaUpdateBuilder.get();
        }
        return criteriaBuilder -> {
            UpdateSpecification<K> specification = getUpdateSpecification(context, rootEntity);
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
            for (String orderPath : StringUtils.splitOmitEmptyStrings(order.getProperty(), '.')) {
                path = path.get(orderPath);

            }
            Expression<?> expression = order.isIgnoreCase() ? cb.lower((Expression<String>) path) : path;
            orders.add(order.isAscending() ? cb.asc(expression) : cb.desc(expression));
        }
        return orders;
    }

    private List<JoinPath> sortJoinPaths(Collection<JoinPath> joinPaths) {
        List<JoinPath> sortedJoinPaths = new ArrayList<>(joinPaths);
        sortedJoinPaths.sort((o1, o2) -> Comparator.comparingInt(String::length).thenComparing(String::compareTo).compare(o1.getPath(), o2.getPath()));
        return sortedJoinPaths;
    }
}
