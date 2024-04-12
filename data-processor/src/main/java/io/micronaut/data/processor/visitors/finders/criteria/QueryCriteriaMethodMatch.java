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

import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.naming.NameUtils;
import io.micronaut.core.util.StringUtils;
import io.micronaut.data.annotation.Join;
import io.micronaut.data.annotation.TypeRole;
import io.micronaut.data.intercept.annotation.DataMethod;
import io.micronaut.data.model.Association;
import io.micronaut.data.model.jpa.criteria.PersistentEntityCriteriaBuilder;
import io.micronaut.data.model.jpa.criteria.PersistentEntityCriteriaQuery;
import io.micronaut.data.model.jpa.criteria.PersistentEntityRoot;
import io.micronaut.data.model.jpa.criteria.PersistentPropertyPath;
import io.micronaut.data.model.jpa.criteria.impl.AbstractPersistentEntityCriteriaQuery;
import io.micronaut.data.model.jpa.criteria.impl.QueryModelPersistentEntityCriteriaQuery;
import io.micronaut.data.model.query.JoinPath;
import io.micronaut.data.model.query.QueryModel;
import io.micronaut.data.model.query.builder.QueryBuilder;
import io.micronaut.data.model.query.builder.QueryResult;
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
import io.micronaut.data.processor.visitors.finders.QueryMatchId;
import io.micronaut.data.processor.visitors.finders.TypeUtils;
import io.micronaut.inject.annotation.AnnotationMetadataHierarchy;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.ParameterElement;
import jakarta.persistence.criteria.Order;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Selection;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
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
     * Apply query match.
     *
     * @param matchContext The match context
     * @param root         The root
     * @param query        The query
     * @param cb           The criteria builder
     * @param <T>          The entity type
     */
    protected <T> void apply(MethodMatchContext matchContext,
                             PersistentEntityRoot<T> root,
                             PersistentEntityCriteriaQuery<T> query,
                             SourcePersistentEntityCriteriaBuilder cb) {
        boolean predicatedApplied = false;
        boolean projectionApplied = false;
        for (MethodNameParser.Match match : matches) {
            if (match.id() == QueryMatchId.PROJECTION) {
                applyProjections(matchContext, match.part(), root, query, cb);
                projectionApplied = true;
            } else if (match.id() == QueryMatchId.PREDICATE) {
                applyPredicates(matchContext, match.part(), matchContext.getParameters(), root, query, cb);
                predicatedApplied = true;
            } else if (match.id() == QueryMatchId.ORDER) {
                applyOrderBy(match.part(), root, query, cb);
            } else if (match.id() == QueryMatchId.FOR_UPDATE) {
                query.forUpdate(true);
            } else if (match.id() == QueryMatchId.LIMIT) {
                String str = match.part();
                try {
                    int max = StringUtils.isNotEmpty(str) ? Integer.parseInt(str) : 1;
                    if (max > -1) {
                        query.max(max);
                    }
                } catch (NumberFormatException e) {
                    throw new MatchFailedException("Invalid number specified to top: " + str);
                }
            } else if (match.id() == QueryMatchId.FIRST) {
                query.max(1);
            } else if (match.id() == QueryMatchId.DISTINCT) {
                applyDistinct(query);
            }
        }
        if (!predicatedApplied) {
            applyPredicates(matchContext, matchContext.getParametersNotInRole(), root, query, cb);
        }
        if (!projectionApplied) {
            applyProjections(matchContext, "", root, query, cb);
        }

        applyJoinSpecs(root, joinSpecsAtMatchContext(matchContext, true));
    }

    private <T> void applyPredicates(MethodMatchContext matchContext,
                                     String querySequence,
                                     ParameterElement[] parameters,
                                     PersistentEntityRoot<T> root,
                                     PersistentEntityCriteriaQuery<T> query,
                                     SourcePersistentEntityCriteriaBuilder cb) {
        Predicate predicate = extractPredicates(querySequence, Arrays.asList(parameters).iterator(), root, cb);
        predicate = interceptPredicate(matchContext, List.of(), root, cb, predicate);
        if (predicate != null) {
            query.where(predicate);
        }
    }

    private <T> void applyPredicates(MethodMatchContext matchContext,
                                     List<ParameterElement> parameters,
                                     PersistentEntityRoot<T> root,
                                     PersistentEntityCriteriaQuery<T> query,
                                     SourcePersistentEntityCriteriaBuilder cb) {
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
    protected <T> void applyDistinct(PersistentEntityCriteriaQuery<T> query) {
        if (query.isDistinct()) {
            throw new MatchFailedException("Distinct already specified!");
        }
        query.distinct(true);
    }

    @Override
    protected MethodMatchInfo build(MethodMatchContext matchContext) {

        MethodMatchSourcePersistentEntityCriteriaBuilderImpl cb = new MethodMatchSourcePersistentEntityCriteriaBuilderImpl(matchContext);

        PersistentEntityCriteriaQuery<Object> criteriaQuery = cb.createQuery();
        apply(matchContext, criteriaQuery.from(matchContext.getRootEntity()), criteriaQuery, cb);

        FindersUtils.InterceptorMatch interceptorMatch = resolveReturnTypeAndInterceptor(matchContext);
        ClassElement resultType = interceptorMatch.returnType();
        ClassElement interceptorType = interceptorMatch.interceptor();

        boolean optimisticLock = ((AbstractPersistentEntityCriteriaQuery<?>) criteriaQuery).hasVersionRestriction();

        SourcePersistentEntityCriteriaQuery<?> query = (SourcePersistentEntityCriteriaQuery) criteriaQuery;
        MethodResult result = analyzeMethodResult(
            matchContext,
            query.getQueryResultTypeName(),
            matchContext.getRootEntity().getClassElement(),
            interceptorMatch,
            false
        );

        if (result.isDto() && !result.isRuntimeDtoConversion()) {
            List<SourcePersistentProperty> dtoProjectionProperties = getDtoProjectionProperties(matchContext.getRootEntity(), resultType);
            if (!dtoProjectionProperties.isEmpty()) {
                Root<?> root = query.getRoots().iterator().next();
                List<Selection<?>> selectionList = dtoProjectionProperties.stream()
                    .map(p -> {
                        if (matchContext.getQueryBuilder().shouldAliasProjections()) {
                            return root.get(p.getName()).alias(p.getName());
                        } else {
                            return root.get(p.getName());
                        }
                    })
                    .collect(Collectors.toList());
                query.multiselect(
                    selectionList
                );
            }
        }

        final AnnotationMetadataHierarchy annotationMetadataHierarchy = new AnnotationMetadataHierarchy(
                matchContext.getRepositoryClass().getAnnotationMetadata(),
                matchContext.getAnnotationMetadata()
        );
        QueryBuilder queryBuilder = matchContext.getQueryBuilder();
        QueryModel queryModel = ((QueryModelPersistentEntityCriteriaQuery) criteriaQuery).getQueryModel();
        QueryResult queryResult = queryBuilder.buildQuery(annotationMetadataHierarchy, queryModel);

        ClassElement genericReturnType = matchContext.getReturnType();
        if (TypeUtils.isReactiveOrFuture(genericReturnType)) {
            genericReturnType = genericReturnType.getFirstTypeArgument().orElse(matchContext.getRootEntity().getType());
        }
        QueryResult countQueryResult = null;
        if (matchContext.isTypeInRole(genericReturnType, TypeRole.PAGE)) {
//                SourcePersistentEntityCriteriaQuery<Object> count = cb.createQuery();
//                count.select(cb.count(query.getRoots().iterator().next()));
//                CommonAbstractCriteria countQueryCriteria = defineQuery(matchContext, matchContext.getRootEntity(), cb);

            QueryModel countQuery = QueryModel.from(queryModel.getPersistentEntity());
            countQuery.projections().count();
            QueryModel.Junction junction = queryModel.getCriteria();
            for (QueryModel.Criterion criterion : junction.getCriteria()) {
                countQuery.add(criterion);
            }
            // Joins are skipped for count query for OneToMany, ManyToMany
            // however skipping joins from criteria could cause issues (in many to many?)
            for (JoinPath joinPath : queryModel.getJoinPaths()) {
                Association association = joinPath.getAssociation();
                if (association != null && !association.getKind().isSingleEnded()) {
                    // skip OneToMany and ManyToMany
                    continue;
                }
                Join.Type joinType = joinPath.getJoinType();
                switch (joinType) {
                    case INNER:
                    case FETCH:
                        joinType = Join.Type.DEFAULT;
                        break;
                    case LEFT_FETCH:
                        joinType = Join.Type.LEFT;
                        break;
                    case RIGHT_FETCH:
                        joinType = Join.Type.RIGHT;
                        break;
                    default:
                        // no-op
                }
                countQuery.join(joinPath.getPath(), joinType, null);
            }
            countQueryResult = queryBuilder.buildQuery(annotationMetadataHierarchy, countQuery);
        }

        return new MethodMatchInfo(
                DataMethod.OperationType.QUERY,
                result.resultType(),
                interceptorType
        )
                .dto(result.isDto())
                .optimisticLock(optimisticLock)
                .queryResult(queryResult)
                .countQueryResult(countQueryResult);
    }

    private <T> void applyOrderBy(String orderBy,
                                  PersistentEntityRoot<T> root,
                                  PersistentEntityCriteriaQuery<T> query,
                                  PersistentEntityCriteriaBuilder cb) {
        List<Order> orders = new ArrayList<>();
        String[] orderDefItems = orderBy.split("And");
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

    private <T> PersistentPropertyPath<?> findOrderProperty(PersistentEntityRoot<T> root, String propertyName) {
        if (root.getPersistentEntity().getPropertyByName(propertyName) != null) {
            return root.get(propertyName);
        }
        // Look at association paths
        PersistentPropertyPath<?> property = findProperty(root, propertyName);
        if (property != null) {
            return property;
        }
        throw new MatchFailedException("Cannot order by non-existent property: " + propertyName);
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
                                        SourcePersistentEntityCriteriaBuilder cb) {
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
