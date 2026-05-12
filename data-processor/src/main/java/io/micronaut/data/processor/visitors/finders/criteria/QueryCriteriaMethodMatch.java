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
package io.micronaut.data.processor.visitors.finders.criteria;

import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.naming.NameUtils;
import io.micronaut.core.util.StringUtils;
import io.micronaut.data.annotation.By;
import io.micronaut.data.annotation.First;
import io.micronaut.data.annotation.Join;
import io.micronaut.data.annotation.OrderBy;
import io.micronaut.data.annotation.Projection;
import io.micronaut.data.annotation.TypeRole;
import io.micronaut.data.intercept.annotation.DataMethod;
import io.micronaut.data.model.Embedded;
import io.micronaut.data.model.jpa.criteria.PersistentEntityCriteriaBuilder;
import io.micronaut.data.model.jpa.criteria.PersistentEntityCriteriaQuery;
import io.micronaut.data.model.jpa.criteria.PersistentEntityQuery;
import io.micronaut.data.model.jpa.criteria.PersistentEntityRoot;
import io.micronaut.data.model.jpa.criteria.PersistentEntitySubquery;
import io.micronaut.data.model.jpa.criteria.impl.AbstractPersistentEntityCriteriaQuery;
import io.micronaut.data.model.jpa.criteria.impl.AbstractPersistentEntityQuery;
import io.micronaut.data.model.query.builder.QueryResult;
import io.micronaut.data.model.query.builder.sql.AbstractSqlLikeQueryBuilder;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.model.query.builder.sql.SqlQueryBuilder;
import io.micronaut.data.processor.model.SourcePersistentEntity;
import io.micronaut.data.processor.model.SourcePersistentProperty;
import io.micronaut.data.processor.model.criteria.SourcePersistentEntityCriteriaQuery;
import io.micronaut.data.processor.model.criteria.impl.MethodMatchSourcePersistentEntityCriteriaBuilderImpl;
import io.micronaut.data.processor.visitors.MatchFailedException;
import io.micronaut.data.processor.visitors.MethodMatchContext;
import io.micronaut.data.processor.visitors.finders.AbstractCriteriaMethodMatch;
import io.micronaut.data.processor.visitors.finders.FindersUtils;
import io.micronaut.data.processor.visitors.finders.MethodMatchInfo;
import io.micronaut.data.processor.visitors.finders.MethodNameParser;
import io.micronaut.data.processor.visitors.finders.MethodResult;
import io.micronaut.data.processor.visitors.finders.QueryMatchId;
import io.micronaut.data.processor.visitors.finders.TypeUtils;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.Element;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.ast.ParameterElement;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.From;
import jakarta.persistence.criteria.Order;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Selection;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Query criteria method match.
 *
 * @author Denis Stepanov
 * @since 3.2
 */
@Experimental
public class QueryCriteriaMethodMatch extends AbstractCriteriaMethodMatch {

    /**
     * Default constructor.
     *
     * @param matches The matches
     */
    public QueryCriteriaMethodMatch(List<MethodNameParser.Match> matches) {
        super(matches);
    }

    /**
     * Create a query from the method.
     *
     * @param matchContext The match context
     * @param cb           The criteria builder
     * @param joinSpecs    The joinSpecs
     * @return A new query
     */
    protected PersistentEntityCriteriaQuery<Object> createQuery(MethodMatchContext matchContext,
                                                                PersistentEntityCriteriaBuilder cb,
                                                                List<AnnotationValue<Join>> joinSpecs) {
        Element paginationParameter = matchContext.findParameterInRole(TypeRole.PAGEABLE);
        boolean isPageable = matchContext.hasParameterInRole(TypeRole.PAGEABLE);
        SourcePersistentEntity persistentEntity = matchContext.getRootEntity();
        PersistentEntityCriteriaQuery<Object> criteriaQuery;
        if (isPageable && isPageableWithJoins(persistentEntity, matchContext, joinSpecs)) {
            int pageableParameterIndex = List.of(matchContext.getParameters()).indexOf(paginationParameter);
            criteriaQuery = createQueryWithJoinsAndPagination(matchContext, cb, joinSpecs, pageableParameterIndex);
        } else {
            criteriaQuery = createDefaultQuery(matchContext, cb, joinSpecs);
            if (isPageable) {
                AbstractPersistentEntityQuery<?, ?> abstractPersistentEntityQuery = (AbstractPersistentEntityQuery<?, ?>) criteriaQuery;
                abstractPersistentEntityQuery.getParametersInRole().put(List.of(matchContext.getParameters()).indexOf(paginationParameter), TypeRole.PAGEABLE);
            } else if (matchContext.hasParameterInRole(TypeRole.SORT)) {
                Element sortParameter = matchContext.findParameterInRole(TypeRole.SORT);
                AbstractPersistentEntityQuery<?, ?> abstractPersistentEntityQuery = (AbstractPersistentEntityQuery<?, ?>) criteriaQuery;
                abstractPersistentEntityQuery.getParametersInRole().put(List.of(matchContext.getParameters()).indexOf(sortParameter), TypeRole.SORT);
            } else if (matchContext.hasParameterInRole(TypeRole.LIMIT)) {
                Element limitParameter = matchContext.findParameterInRole(TypeRole.LIMIT);
                AbstractPersistentEntityQuery<?, ?> abstractPersistentEntityQuery = (AbstractPersistentEntityQuery<?, ?>) criteriaQuery;
                abstractPersistentEntityQuery.getParametersInRole().put(List.of(matchContext.getParameters()).indexOf(limitParameter), TypeRole.LIMIT);
            }
        }
        return criteriaQuery;
    }

    private boolean isPageableWithJoins(SourcePersistentEntity persistentEntity, MethodMatchContext matchContext, List<AnnotationValue<Join>> joinSpecs) {
        return !joinSpecs.isEmpty()
            && matchContext.getQueryBuilder() instanceof AbstractSqlLikeQueryBuilder sqlQueryBuilder
            // MySQL doesn't support subquery with limits
            && (!(sqlQueryBuilder instanceof SqlQueryBuilder queryBuilder) || queryBuilder.getDialect() != Dialect.MYSQL)
            && !persistentEntity.hasCompositeIdentity()
            && persistentEntity.hasIdentity()
            && !(persistentEntity.getIdentity() instanceof Embedded);
    }

    private PersistentEntityCriteriaQuery<Object> createDefaultQuery(MethodMatchContext matchContext,
                                                                     PersistentEntityCriteriaBuilder cb,
                                                                     List<AnnotationValue<Join>> joinSpecs) {

        PersistentEntityCriteriaQuery<Object> query = cb.createQuery();
        PersistentEntityRoot<Object> root = query.from(matchContext.getRootEntity());
        applyJoinSpecs(root, joinSpecs);
        applyDistinct(query);
        applyProjection(matchContext, cb, root, query);
        applyPredicate(matchContext, cb, root, query);
        applyOrder(cb, root, query);
        applyOrderByAnnotation(cb, root, query, matchContext.getMethodElement());
        applyForUpdate(query);
        applyLimit(query, matchContext.getMethodElement());

        return query;
    }

    /**
     * Create a special query that supports using JOINs and pagination.
     *
     * @param matchContext The match context
     * @param cb           The criteria builder
     * @param joinSpecs    The joinSpecs
     * @return A new query
     */
    private PersistentEntityCriteriaQuery<Object> createQueryWithJoinsAndPagination(MethodMatchContext matchContext,
                                                                                    PersistentEntityCriteriaBuilder cb,
                                                                                    List<AnnotationValue<Join>> joinSpecs,
                                                                                    int pageableParameterIndex) {
        // SQL tabular results with JOINs cannot be property limited by LIMIT and OFFSET
        // Create a query that can be paginated with JOINs using a subquery
        //
        // SELECT mainEntity.* FROM MyEntity mainEntity JOIN ... WHERE mainEntity.id in (
        //     SELECT paginationEntity.id FROM MyEntity paginationEntity WHERE paginationEntity.id in (
        //        SELECT filteredEntity.id FROM MyEntity filteredEntity JOIN ... WHERE ... ;
        //     ) ORDER BY ... LIMIT ... OFFSET ...
        // ) ORDER BY ...
        //
        // NOTE: Joins might eliminate the entities so we need to include them (We might avoid them for LEFT JOINs)

        PersistentEntityCriteriaQuery<Object> mainQuery = cb.createQuery();
        PersistentEntityRoot<Object> mainRoot = mainQuery.from(matchContext.getRootEntity());

        PersistentEntitySubquery<Object> paginationSubquery = mainQuery.subquery(mainRoot.getExpressionType());
        PersistentEntityRoot<Object> paginationRoot = paginationSubquery.from(matchContext.getRootEntity());
        paginationSubquery.select(paginationRoot.id());

        // Apply pagination and sort to do subquery
        // NOTE: Sort shouldn't be applied if unpaged
        AbstractPersistentEntityQuery<?, ?> abstractPersistentEntityQuery = (AbstractPersistentEntityQuery<?, ?>) paginationSubquery;
        abstractPersistentEntityQuery.getParametersInRole().put(pageableParameterIndex, TypeRole.PAGEABLE_REQUIRED);

        PersistentEntitySubquery<Object> filteredSubquery = paginationSubquery.subquery(mainRoot.getExpressionType());
        PersistentEntityRoot<Object> filteredRoot = filteredSubquery.from(matchContext.getRootEntity());
        filteredSubquery.select(filteredRoot.id());

        paginationSubquery.where(paginationRoot.id().in(filteredSubquery));
        mainQuery.where(mainRoot.id().in(paginationSubquery));

        applyProjection(matchContext, cb, mainRoot, mainQuery);
        applyPredicate(matchContext, cb, filteredRoot, filteredSubquery);
//        applyOrder(cb, filteredRoot, filteredSubquery);
//        applyOrderByAnnotation(cb, filteredRoot, filteredSubquery, matchContext.getMethodElement());
        applyOrder(cb, mainRoot, mainQuery);
        applyOrderByAnnotation(cb, mainRoot, mainQuery, matchContext.getMethodElement());

        applyForUpdate(mainQuery);

        applyLimit(filteredSubquery, matchContext.getMethodElement());

        applyDistinct(mainQuery);

        applyJoinSpecs(filteredRoot, joinSpecs);
        applyJoinSpecs(mainRoot, joinSpecs);

        // Sort last query
        AbstractPersistentEntityQuery<?, ?> mainEntityQuery = (AbstractPersistentEntityQuery<?, ?>) mainQuery;
        mainEntityQuery.getParametersInRole().put(pageableParameterIndex, TypeRole.SORT);

        return mainQuery;
    }

    /**
     * Create a count query.
     *
     * @param matchContext The match context
     * @param cb           The criteria builder
     * @param joinSpecs    The joinSpecs
     * @return A new query
     */
    protected final PersistentEntityCriteriaQuery<Object> createDefaultCountQuery(MethodMatchContext matchContext,
                                                                                  PersistentEntityCriteriaBuilder cb,
                                                                                  List<AnnotationValue<Join>> joinSpecs) {

        PersistentEntityCriteriaQuery<Object> query = cb.createQuery();
        PersistentEntityRoot<Object> root = query.from(matchContext.getRootEntity());
        query.select(cb.count(root));

        applyPredicate(matchContext, cb, root, query);

        boolean distinct = !joinSpecs.isEmpty() || findMatchPart(matches, QueryMatchId.DISTINCT).isPresent();
        String projectionPart = findMatchPart(matches, QueryMatchId.PROJECTION).orElse(null);

        if (StringUtils.isNotEmpty(projectionPart)) {
            Expression<?> propertyPath = getProperty(root, Objects.requireNonNull(projectionPart));
            Expression<Long> count = distinct ? cb.countDistinct(propertyPath) : cb.count(propertyPath);
            query.select(count);
        } else {
            Expression<Long> count = distinct ? cb.countDistinct(root) : cb.count(root);
            query.select(count);
        }

        applyJoinSpecs(root, joinSpecs);

        return query;
    }

    private void applyForUpdate(PersistentEntityCriteriaQuery<Object> query) {
        findMatchPart(matches, QueryMatchId.FOR_UPDATE)
            .ifPresent(text -> query.forUpdate(true));
    }

    private void applyOrder(PersistentEntityCriteriaBuilder cb,
                            PersistentEntityRoot<Object> root,
                            PersistentEntityQuery<Object> query) {
        findMatchPart(matches, QueryMatchId.ORDER).ifPresent(text -> applyOrderBy(text, root, query, cb));
    }

    private void applyOrderByAnnotation(PersistentEntityCriteriaBuilder cb,
                                                PersistentEntityRoot<Object> root,
                                                PersistentEntityQuery<Object> query,
                                                AnnotationMetadata annotationMetadata) {
        List<Order> orders = new ArrayList<>();
        for (AnnotationValue<?> av : annotationMetadata.getAnnotationValuesByStereotype(OrderBy.class.getName())) {
            orders.add(cb.sort(
                findOrderProperty(root, av.stringValue().orElseThrow()),
                !av.booleanValue("descending").orElse(false),
                av.booleanValue("ignoreCase").orElse(false)
            ));
        }
        if (!orders.isEmpty()) {
            query.orderBy(orders);
        }
    }

    private <T> Expression<?> findOrderProperty(PersistentEntityRoot<T> root, String propertyName) {
        if (By.ID.equals(propertyName)) {
            return root.id();
        }
        if (root.getPersistentEntity().getPropertyByName(propertyName) != null) {
            return root.get(propertyName);
        }
        // Look at association paths
        io.micronaut.data.model.jpa.criteria.PersistentPropertyPath<?> property = findProperty(root, propertyName);
        if (property != null) {
            return property;
        }
        Path<?> path = root;
        for (Iterator<String> iterator = StringUtils.splitOmitEmptyStrings(propertyName, '.').iterator(); path != null && iterator.hasNext(); ) {
            String next = iterator.next();
            if (iterator.hasNext()) {
                path = ((From<?, ?>) path).join(next);
            } else {
                try {
                    path = path.get(next);
                } catch (Exception e) {
                    // Ignore
                    path = null;
                }
            }
        }
        if (path == null) {
            throw new MatchFailedException("Cannot order by non-existent property: " + propertyName);
        }
        return path;
    }

    private void applyDistinct(PersistentEntityCriteriaQuery<Object> mainQuery) {
        findMatchPart(matches, QueryMatchId.DISTINCT)
            .ifPresent(text -> setDistinct(mainQuery));
    }

    private void applyLimit(PersistentEntityQuery<Object> query, MethodElement methodElement) {
        Optional<String> limit = findMatchPart(matches, QueryMatchId.LIMIT);
        Optional<String> first = findMatchPart(matches, QueryMatchId.FIRST);
        if (limit.isPresent()) {
            String text = limit.get();
            try {
                int max = StringUtils.isNotEmpty(text) ? Integer.parseInt(text) : 1;
                if (max > -1) {
                    query.limit(max);
                }
            } catch (NumberFormatException e) {
                throw new MatchFailedException("Invalid number specified to top: " + text);
            }
        } else if (first.isPresent()) {
            query.limit(1);
        } else {
            AnnotationValue<First> firstAnnotation = methodElement.getAnnotation(First.class);
            if (firstAnnotation != null) {
                query.limit(firstAnnotation.intValue().orElse(1));
            }
        }
    }

    private void applyPredicate(MethodMatchContext matchContext,
                                PersistentEntityCriteriaBuilder cb,
                                PersistentEntityRoot<Object> root,
                                PersistentEntityQuery<Object> entityQuery) {
        findMatchPart(matches, QueryMatchId.PREDICATE)
            .ifPresentOrElse(text -> applyPredicates(matchContext, text, matchContext.getParametersNotInRole(), root, entityQuery, cb),
                () -> applyPredicates(matchContext, matchContext.getParametersNotInRole(), root, entityQuery, cb));
    }

    private void applyProjection(MethodMatchContext matchContext,
                                 PersistentEntityCriteriaBuilder cb,
                                 PersistentEntityRoot<Object> root,
                                 PersistentEntityCriteriaQuery<Object> criteriaQuery) {
        findMatchPart(matches, QueryMatchId.PROJECTION)
            .ifPresentOrElse(text -> applyProjections(matchContext, text, root, criteriaQuery, cb),
                () -> applyProjections(matchContext, "", root, criteriaQuery, cb));
    }

    private Optional<String> findMatchPart(List<MethodNameParser.Match> matches, QueryMatchId id) {
        return matches.stream()
            .filter(match -> match.id() == id)
            .findFirst()
            .map(MethodNameParser.Match::part);
    }

    private <T> void applyPredicates(MethodMatchContext matchContext,
                                     String querySequence,
                                     List<ParameterElement> parameters,
                                     PersistentEntityRoot<T> root,
                                     PersistentEntityQuery<?> query,
                                     PersistentEntityCriteriaBuilder cb) {
        Predicate predicate = extractPredicates(querySequence, parameters.iterator(), root, cb);
        predicate = interceptPredicate(matchContext, List.of(), root, cb, predicate);
        if (predicate != null) {
            query.where(predicate);
        }
    }

    private <T> void applyPredicates(MethodMatchContext matchContext,
                                     List<ParameterElement> parameters,
                                     PersistentEntityRoot<T> root,
                                     PersistentEntityQuery<?> query,
                                     PersistentEntityCriteriaBuilder cb) {
        Predicate predicate = extractPredicates(parameters, root, cb);
        predicate = interceptPredicate(matchContext, List.of(), root, cb, predicate);
        if (predicate != null) {
            query.where(predicate);
        }
    }

    /**
     * Apply the distinct value.
     *
     * @param query The query
     * @param <T>   The query type
     */
    protected <T> void setDistinct(PersistentEntityCriteriaQuery<T> query) {
        if (query.isDistinct()) {
            throw new MatchFailedException("Distinct already specified!");
        }
        query.distinct(true);
    }

    @Override
    protected MethodMatchInfo build(MethodMatchContext matchContext) {

        MethodMatchSourcePersistentEntityCriteriaBuilderImpl cb = new MethodMatchSourcePersistentEntityCriteriaBuilderImpl(matchContext);

        List<AnnotationValue<Join>> joinSpecs = joinSpecsAtMatchContext(matchContext, true);

        SourcePersistentEntity persistentEntity = matchContext.getRootEntity();

        PersistentEntityCriteriaQuery<Object> criteriaQuery = createQuery(matchContext, cb, joinSpecs);

        FindersUtils.InterceptorMatch interceptorMatch = resolveReturnTypeAndInterceptor(matchContext);
        ClassElement resultType = interceptorMatch.returnType();
        ClassElement interceptorType = interceptorMatch.interceptor();

        boolean optimisticLock = ((AbstractPersistentEntityCriteriaQuery<?>) criteriaQuery).hasVersionRestriction();

        SourcePersistentEntityCriteriaQuery<?> query = (SourcePersistentEntityCriteriaQuery) criteriaQuery;
        MethodResult result = analyzeMethodResult(
            matchContext,
            query.getQueryResultTypeName(),
            persistentEntity.getClassElement(),
            interceptorMatch,
            false
        );

        if (result.isDto() && !result.isRuntimeDtoConversion()) {
            List<SourcePersistentProperty> dtoProjectionProperties = getDtoProjectionProperties(persistentEntity, matchContext.getMethodElement(), resultType);
            if (!dtoProjectionProperties.isEmpty()) {
                Root<?> root = query.getRoots().iterator().next();
                List<Selection<?>> selectionList = dtoProjectionProperties.stream()
                    .map(p -> {
                        if (matchContext.getQueryBuilder() instanceof SqlQueryBuilder) {
                            return root.get(p.getName());
                        } else {
                            return root.get(p.getName()).alias(p.getName());
                        }
                    })
                    .collect(Collectors.toList());
                query.multiselect(
                    selectionList
                );
            }
        }

        final AnnotationMetadata annotationMetadata = matchContext.getMethodElement();
        QueryResult queryResult = criteriaQuery.build(annotationMetadata, matchContext.getQueryBuilder());

        ClassElement genericReturnType = matchContext.getReturnType();
        if (TypeUtils.isReactiveOrFuture(genericReturnType)) {
            genericReturnType = genericReturnType.getFirstTypeArgument().orElse(persistentEntity.getType());
        }
        boolean isReturnsPage = matchContext.isTypeInRole(genericReturnType, TypeRole.PAGE) || matchContext.isTypeInRole(genericReturnType, TypeRole.CURSORED_PAGE);
        QueryResult countQueryResult = null;
        if (isReturnsPage) {
            PersistentEntityCriteriaQuery<Object> countQuery = createDefaultCountQuery(matchContext, cb, joinSpecs);
            countQueryResult = countQuery.build(annotationMetadata, matchContext.getQueryBuilder());
        }

        return new MethodMatchInfo(
            getOperationType(),
            result.resultType(),
            interceptorType
        )
            .dto(result.isDto())
            .optimisticLock(optimisticLock)
            .queryResult(queryResult)
            .countQueryResult(countQueryResult);
    }

    private void applyOrderBy(String orderBy,
                              PersistentEntityRoot<?> root,
                              PersistentEntityQuery<?> query,
                              PersistentEntityCriteriaBuilder cb) {
        String[] orderDefItems = orderBy.split("And");
        List<Order> orders = new ArrayList<>(orderDefItems.length);
        for (String orderDef : orderDefItems) {
            String prop = NameUtils.decapitalize(orderDef);
            if (prop.endsWith("Desc")) {
                String propertyName = prop.substring(0, prop.length() - 4);
                orders.add(cb.desc(findOrderProperty(root, propertyName)));
            } else if (prop.endsWith("Asc")) {
                String propertyName = prop.substring(0, prop.length() - 3);
                orders.add(cb.asc(findOrderProperty(root, propertyName)));
            } else {
                orders.add(cb.asc(findOrderProperty(root, prop)));
            }
        }
        if (!orders.isEmpty()) {
            query.orderBy(orders);
        }
    }

    /**
     * Apply projections.
     *
     * @param matchContext The match context
     * @param projection   The projection
     * @param root         The root
     * @param query        The query
     * @param cb           The critria builder
     * @param <T>          The query type
     */
    private <T> void applyProjections(MethodMatchContext matchContext,
                                      String projection,
                                      PersistentEntityRoot<T> root,
                                      PersistentEntityCriteriaQuery<T> query,
                                      PersistentEntityCriteriaBuilder cb) {
        if (projection.isBlank()) {
            List<AnnotationValue<Projection>> selectAnnotations = matchContext.getMethodElement().getAnnotationValuesByType(Projection.class);
            List<Selection<?>> selections = new ArrayList<>(selectAnnotations.size());
            for (AnnotationValue<Projection> selectAnnotation : selectAnnotations) {
                selectAnnotation.stringValue().ifPresent(select ->  selections.add(findProperty(root, select)));
            }
            if (!selections.isEmpty()) {
                if (selectAnnotations.size() == 1) {
                    query.select((Selection<? extends T>) selections.getFirst());
                } else {
                    query.multiselect(selections);
                }
                return;
            }
        }
        applyProjections(projection, root, query, cb, matchContext.getReturnType().getSimpleName());
    }

    /**
     * Apply projections.
     *
     * @param projectionPart The projection
     * @param root           The root
     * @param query          The query
     * @param cb             The criteria builder
     * @param returnTypeName The returnTypeName
     * @param <T>            The entity type
     */
    protected <T> void applyProjections(String projectionPart,
                                        PersistentEntityRoot<T> root,
                                        PersistentEntityCriteriaQuery<T> query,
                                        PersistentEntityCriteriaBuilder cb,
                                        String returnTypeName) {
        List<Selection<?>> selectionList = findSelections(projectionPart, root, cb, returnTypeName);
        if (selectionList.isEmpty()) {
            return;
        }
        if (selectionList.size() == 1) {
            query.select((Selection<? extends T>) selectionList.iterator().next());
        } else {
            query.multiselect(selectionList);
        }
    }

    @Override
    protected DataMethod.OperationType getOperationType() {
        return DataMethod.OperationType.QUERY;
    }

}
