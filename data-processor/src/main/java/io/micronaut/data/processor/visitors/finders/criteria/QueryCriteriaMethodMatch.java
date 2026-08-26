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
import io.micronaut.data.model.jpa.criteria.PersistentEntityFrom;
import io.micronaut.data.model.jpa.criteria.PersistentEntityQuery;
import io.micronaut.data.model.jpa.criteria.PersistentEntityRoot;
import io.micronaut.data.model.jpa.criteria.PersistentEntitySubquery;
import io.micronaut.data.model.jpa.criteria.impl.AbstractPersistentEntityCriteriaQuery;
import io.micronaut.data.model.jpa.criteria.impl.AbstractPersistentEntityQuery;
import io.micronaut.data.model.query.builder.QueryResult;
import io.micronaut.data.model.query.builder.sql.AbstractSqlLikeQueryBuilder;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.model.query.builder.sql.SqlQueryBuilder;
import io.micronaut.data.model.query.builder.sql.VectorScoringDialectSupport;
import io.micronaut.data.model.vector.Vector;
import io.micronaut.data.model.vector.search.SearchResults;
import io.micronaut.data.processor.model.SourcePersistentEntity;
import io.micronaut.data.processor.model.SourcePersistentProperty;
import io.micronaut.data.processor.model.criteria.SourcePersistentEntityCriteriaBuilder;
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
import io.micronaut.data.processor.visitors.finders.Restrictions;
import io.micronaut.data.processor.visitors.finders.TypeUtils;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.Element;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.ast.ParameterElement;
import io.micronaut.inject.processing.ProcessingException;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.From;
import jakarta.persistence.criteria.Order;
import jakarta.persistence.criteria.ParameterExpression;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Selection;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Query criteria method match.
 *
 * @author Denis Stepanov
 * @since 3.2
 */
@Experimental
public class QueryCriteriaMethodMatch extends AbstractCriteriaMethodMatch {

    private static final Pattern LOGICAL_OPERATOR_PATTERN = Pattern.compile("And|Or");

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
        // Predicates, projections, and ordering can introduce joins in addition to explicit @Join specifications.
        PersistentEntityCriteriaQuery<Object> criteriaQuery = createDefaultQuery(matchContext, cb, joinSpecs);
        if (isPageable && isPageableWithJoins(persistentEntity, matchContext, criteriaQuery)) {
            int pageableParameterIndex = List.of(matchContext.getParameters()).indexOf(paginationParameter);
            PersistentEntityRoot<?> analyzedRoot = (PersistentEntityRoot<?>) criteriaQuery.getRoots().iterator().next();
            criteriaQuery = createQueryWithJoinsAndPagination(matchContext, cb, joinSpecs, analyzedRoot, pageableParameterIndex);
        } else {
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

    private boolean isPageableWithJoins(SourcePersistentEntity persistentEntity,
                                        MethodMatchContext matchContext,
                                        PersistentEntityCriteriaQuery<Object> criteriaQuery) {
        return requiresPaginationSubquery((PersistentEntityRoot<?>) criteriaQuery.getRoots().iterator().next())
            && matchContext.getQueryBuilder() instanceof AbstractSqlLikeQueryBuilder sqlQueryBuilder
            // MySQL doesn't support subquery with limits
            && (!(sqlQueryBuilder instanceof SqlQueryBuilder queryBuilder) || queryBuilder.getDialect() != Dialect.MYSQL)
            && !persistentEntity.hasCompositeIdentity()
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
        applyVectorScoreOrderIfNeeded(matchContext, cb, root, query);
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
     * @param analyzedRoot The root containing every join introduced by the default query
     * @param pageableParameterIndex The pageable parameter index
     * @return A new query
     */
    private PersistentEntityCriteriaQuery<Object> createQueryWithJoinsAndPagination(MethodMatchContext matchContext,
                                                                                    PersistentEntityCriteriaBuilder cb,
                                                                                    List<AnnotationValue<Join>> joinSpecs,
                                                                                    PersistentEntityRoot<?> analyzedRoot,
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

        applyPaginationJoins(analyzedRoot, paginationRoot);
        applyJoinSpecs(filteredRoot, joinSpecs);
        applyJoinSpecs(mainRoot, joinSpecs);

        // Sort last query
        AbstractPersistentEntityQuery<?, ?> mainEntityQuery = (AbstractPersistentEntityQuery<?, ?>) mainQuery;
        mainEntityQuery.getParametersInRole().put(pageableParameterIndex, TypeRole.SORT);

        return mainQuery;
    }

    private void applyPaginationJoins(PersistentEntityFrom<?, ?> analyzedFrom,
                                      PersistentEntityFrom<?, ?> paginationFrom) {
        for (var analyzedJoin : analyzedFrom.getPersistentJoins()) {
            if (analyzedJoin.getAssociation().isForeignKey()) {
                continue;
            }
            String associationName = analyzedJoin.getAssociation().getName();
            String alias = analyzedJoin.getAlias();
            Join.Type joinType = analyzedJoin.getAssociationJoinType();
            PersistentEntityFrom<?, ?> paginationJoin;
            if (alias == null) {
                paginationJoin = paginationFrom.join(
                    associationName,
                    joinType == null ? Join.Type.DEFAULT : joinType
                );
            } else {
                paginationJoin = paginationFrom.join(
                    associationName,
                    joinType == null ? Join.Type.DEFAULT : joinType,
                    alias
                );
            }
            applyPaginationJoins(analyzedJoin, paginationJoin);
        }
    }

    private boolean requiresPaginationSubquery(PersistentEntityFrom<?, ?> from) {
        for (var join : from.getPersistentJoins()) {
            if (join.getAssociation().isForeignKey() || requiresPaginationSubquery(join)) {
                return true;
            }
        }
        return false;
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
        applyPredicate(matchContext, cb, root, query);

        String projectionPart = findMatchPart(matches, QueryMatchId.PROJECTION).orElse(null);
        Expression<?> countExpression = root;
        if (StringUtils.isNotEmpty(projectionPart)) {
            countExpression = getProperty(root, Objects.requireNonNull(projectionPart));
        }

        applyJoinSpecs(root, joinSpecs);

        boolean distinct = !joinSpecs.isEmpty()
            || requiresPaginationSubquery(root)
            || findMatchPart(matches, QueryMatchId.DISTINCT).isPresent();
        query.select(distinct ? cb.countDistinct(countExpression) : cb.count(countExpression));

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

        ClassElement declaredReturnType = unwrapReactiveReturnType(matchContext.getReturnType());
        applySearchResultsProjectionIfNeeded(matchContext, cb, query, declaredReturnType);
        applyDtoProjectionIfNeeded(matchContext, query, result, persistentEntity, resultType);

        final AnnotationMetadata annotationMetadata = matchContext.getMethodElement();
        QueryResult queryResult = criteriaQuery.build(annotationMetadata, matchContext.getQueryBuilder());

        ClassElement genericReturnType = matchContext.getReturnType();
        if (TypeUtils.isReactiveOrFuture(genericReturnType)) {
            genericReturnType = genericReturnType.getFirstTypeArgument().orElse(persistentEntity.getType());
        }
        QueryResult countQueryResult = buildCountQueryResultIfRequired(matchContext, cb, joinSpecs, annotationMetadata, genericReturnType);

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

    private static ClassElement unwrapReactiveReturnType(ClassElement returnType) {
        if (!TypeUtils.isReactiveOrFuture(returnType)) {
            return returnType;
        }
        return returnType.getFirstTypeArgument().orElse(returnType);
    }

    private void applySearchResultsProjectionIfNeeded(MethodMatchContext matchContext,
                                                      SourcePersistentEntityCriteriaBuilder cb,
                                                      SourcePersistentEntityCriteriaQuery<?> query,
                                                      ClassElement declaredReturnType) {
        if (!declaredReturnType.getName().equals(SearchResults.class.getName())) {
            return;
        }
        List<MethodNameParser.Match> predicateMatches = matches.stream().filter(m -> m.id() == QueryMatchId.PREDICATE).toList();
        if (predicateMatches.isEmpty()) {
            throw new MatchFailedException("SearchResults query must include a Near, Within, or Between predicate");
        }
        VectorPredicate vectorPredicate = resolveVectorPredicate(predicateMatches);
        if (vectorPredicate == null) {
            throw new MatchFailedException("Unable to resolve vector property for SearchResults query");
        }

        Root<?> root = query.getRoots().iterator().next();
        ParameterElement vectorElement = resolveVectorPredicateParameter(matchContext, vectorPredicate);
        String vectorPropertyName = vectorPredicate.propertyName();
        io.micronaut.data.model.jpa.criteria.PersistentPropertyPath<Object> vectorPropertyExpression = findProperty((PersistentEntityRoot<Object>) root, vectorPropertyName);
        if (vectorPropertyExpression == null) {
            throw new MatchFailedException("Unable to resolve vector property path for SearchResults query: " + vectorPropertyName);
        }
        io.micronaut.data.model.PersistentPropertyPath propertyPath = io.micronaut.data.model.PersistentPropertyPath.of(
            vectorPropertyExpression.getAssociations(),
            vectorPropertyExpression.getProperty()
        );
        ParameterExpression<Vector> vectorParameter = cb.parameter(vectorElement, propertyPath);
        Expression<Double> scoreExpr = cb.function(VectorScoringDialectSupport.SCORE_FUNCTION, Double.class, vectorPropertyExpression, vectorParameter);
        query.multiselect(root, scoreExpr.alias("mn_score"));
    }

    private void applyVectorScoreOrderIfNeeded(MethodMatchContext matchContext,
                                               PersistentEntityCriteriaBuilder cb,
                                               PersistentEntityRoot<Object> root,
                                               PersistentEntityCriteriaQuery<?> query) {
        if (matchContext.hasParameterInRole(TypeRole.SORT) || matchContext.hasParameterInRole(TypeRole.PAGEABLE)) {
            return;
        }
        List<MethodNameParser.Match> predicateMatches = matches.stream().filter(m -> m.id() == QueryMatchId.PREDICATE).toList();
        if (predicateMatches.isEmpty()) {
            return;
        }
        VectorPredicate vectorPredicate = resolveVectorPredicate(predicateMatches);
        if (vectorPredicate == null) {
            return;
        }
        String vectorPropertyName = vectorPredicate.propertyName();
        io.micronaut.data.model.jpa.criteria.PersistentPropertyPath<Object> vectorPropertyExpression = findProperty(root, vectorPropertyName);
        if (vectorPropertyExpression == null || !vectorPropertyExpression.getProperty().isAssignable(Vector.class)) {
            return;
        }
        ParameterElement vectorElement = resolveVectorPredicateParameter(matchContext, vectorPredicate);
        io.micronaut.data.model.PersistentPropertyPath propertyPath = io.micronaut.data.model.PersistentPropertyPath.of(
            vectorPropertyExpression.getAssociations(),
            vectorPropertyExpression.getProperty()
        );
        SourcePersistentEntityCriteriaBuilder scb = (SourcePersistentEntityCriteriaBuilder) cb;
        ParameterExpression<Vector> vectorParameter = scb.parameter(vectorElement, propertyPath);
        Expression<Double> scoreExpr = cb.function(VectorScoringDialectSupport.SCORE_FUNCTION, Double.class, vectorPropertyExpression, vectorParameter);
        query.orderBy(cb.asc(scoreExpr));
    }

    private ParameterElement resolveVectorPredicateParameter(MethodMatchContext matchContext, VectorPredicate vectorPredicate) {
        List<ParameterElement> parameters = matchContext.getParametersNotInRole();
        int parameterIndex = vectorPredicate.parameterIndex();
        if (parameterIndex >= parameters.size()) {
            throw new ProcessingException(matchContext.getMethodElement(), "Vector search predicate requires a Vector parameter");
        }
        ParameterElement parameter = parameters.get(parameterIndex);
        if (!parameter.getType().isAssignable(Vector.class)) {
            throw new ProcessingException(matchContext.getMethodElement(), "Vector search predicate requires a Vector parameter");
        }
        return parameter;
    }

    @SuppressWarnings("StringSplitter")
    private @Nullable VectorPredicate resolveVectorPredicate(List<MethodNameParser.Match> predicateMatches) {
        String predicate = predicateMatches.getFirst().part();
        int parameterIndex = 0;
        for (String part : LOGICAL_OPERATOR_PATTERN.split(predicate)) {
            if (part.endsWith("Near")) {
                return new VectorPredicate(NameUtils.decapitalize(part.substring(0, part.length() - 4)), parameterIndex);
            }
            if (part.endsWith("Within")) {
                return new VectorPredicate(NameUtils.decapitalize(part.substring(0, part.length() - 6)), parameterIndex);
            }
            if (part.endsWith("Between")) {
                return new VectorPredicate(NameUtils.decapitalize(part.substring(0, part.length() - 7)), parameterIndex);
            }
            parameterIndex += requiredParameters(part);
        }
        return null;
    }

    private int requiredParameters(String predicatePart) {
        Map.Entry<String, Restrictions.PropertyRestriction> bestMatch = null;
        for (Map.Entry<String, Restrictions.PropertyRestriction> entry : Restrictions.PROPERTY_RESTRICTIONS_MAP.entrySet()) {
            if (predicatePart.endsWith(entry.getKey())
                && (bestMatch == null || entry.getKey().length() > bestMatch.getKey().length())) {
                bestMatch = entry;
            }
        }
        return bestMatch == null ? 1 : bestMatch.getValue().getRequiredParameters();
    }

    private void applyDtoProjectionIfNeeded(MethodMatchContext matchContext,
                                            SourcePersistentEntityCriteriaQuery<?> query,
                                            MethodResult result,
                                            SourcePersistentEntity persistentEntity,
                                            ClassElement resultType) {
        if (!result.isDto() || result.isRuntimeDtoConversion()) {
            return;
        }
        List<SourcePersistentProperty> dtoProjectionProperties = getDtoProjectionProperties(persistentEntity, matchContext.getMethodElement(), resultType);
        if (dtoProjectionProperties.isEmpty()) {
            return;
        }
        Root<?> root = query.getRoots().iterator().next();
        List<Selection<?>> selectionList = dtoProjectionProperties.stream()
            .map(p -> {
                if (matchContext.getQueryBuilder() instanceof SqlQueryBuilder) {
                    return root.get(p.getName());
                }
                return root.get(p.getName()).alias(p.getName());
            })
            .collect(Collectors.toList());
        query.multiselect(selectionList);
    }

    private @Nullable QueryResult buildCountQueryResultIfRequired(MethodMatchContext matchContext,
                                                                  PersistentEntityCriteriaBuilder cb,
                                                                  List<AnnotationValue<Join>> joinSpecs,
                                                                  AnnotationMetadata annotationMetadata,
                                                                  ClassElement genericReturnType) {
        boolean isReturnsPage = matchContext.isTypeInRole(genericReturnType, TypeRole.PAGE)
            || matchContext.isTypeInRole(genericReturnType, TypeRole.CURSORED_PAGE);
        if (!isReturnsPage) {
            return null;
        }
        PersistentEntityCriteriaQuery<Object> countQuery = createDefaultCountQuery(matchContext, cb, joinSpecs);
        return countQuery.build(annotationMetadata, matchContext.getQueryBuilder());
    }

    @SuppressWarnings("StringSplitter")
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

    private record VectorPredicate(String propertyName, int parameterIndex) {
    }

}
