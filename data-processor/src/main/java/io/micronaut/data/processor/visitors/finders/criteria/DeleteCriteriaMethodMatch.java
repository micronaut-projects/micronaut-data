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
import io.micronaut.data.intercept.annotation.DataMethod;
import io.micronaut.data.model.PersistentPropertyPath;
import io.micronaut.data.model.jpa.criteria.PersistentEntityCriteriaBuilder;
import io.micronaut.data.model.jpa.criteria.PersistentEntityCriteriaDelete;
import io.micronaut.data.model.jpa.criteria.PersistentEntityRoot;
import io.micronaut.data.model.jpa.criteria.impl.AbstractPersistentEntityCriteriaDelete;
import io.micronaut.data.model.jpa.criteria.impl.QueryResultPersistentEntityCriteriaQuery;
import io.micronaut.data.model.query.builder.QueryBuilder;
import io.micronaut.data.model.query.builder.QueryResult;
import io.micronaut.data.processor.model.SourcePersistentEntity;
import io.micronaut.data.processor.model.SourcePersistentProperty;
import io.micronaut.data.processor.model.criteria.SourcePersistentEntityCriteriaBuilder;
import io.micronaut.data.processor.model.criteria.impl.MethodMatchSourcePersistentEntityCriteriaBuilderImpl;
import io.micronaut.data.processor.visitors.MatchFailedException;
import io.micronaut.data.processor.visitors.MethodMatchContext;
import io.micronaut.data.processor.visitors.finders.AbstractCriteriaMethodMatch;
import io.micronaut.data.processor.visitors.finders.FindersUtils;
import io.micronaut.data.processor.visitors.finders.MethodMatchInfo;
import io.micronaut.data.processor.visitors.finders.MethodNameParser;
import io.micronaut.data.processor.visitors.finders.QueryMatchId;
import io.micronaut.inject.annotation.AnnotationMetadataHierarchy;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.ParameterElement;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Selection;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Delete criteria method match.
 *
 * @author Denis Stepanov
 * @since 3.2
 */
@Experimental
public class DeleteCriteriaMethodMatch extends AbstractCriteriaMethodMatch {

    private final boolean isReturning;

    /**
     * Default constructor.
     *
     * @param matches     The matches
     * @param isReturning Is returning
     */
    public DeleteCriteriaMethodMatch(List<MethodNameParser.Match> matches, boolean isReturning) {
        super(matches);
        this.isReturning = isReturning;
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
                             PersistentEntityCriteriaDelete<T> query,
                             SourcePersistentEntityCriteriaBuilder cb) {

        boolean predicatedApplied = false;
        for (MethodNameParser.Match match : matches) {
            if (match.id() == QueryMatchId.PREDICATE) {
                applyPredicates(matchContext, match.part(), matchContext.getParameters(), root, query, cb);
                predicatedApplied = true;
            }
            if (match.id() == QueryMatchId.RETURNING) {
                applyProjections(match.part(), root, query, cb);
            }
        }
        if (!predicatedApplied) {
            applyPredicates(matchContext, matchContext.getParametersNotInRole(), root, query, cb);
        }

        applyJoinSpecs(root, joinSpecsAtMatchContext(matchContext, true));
    }

    private <T> void applyPredicates(MethodMatchContext matchContext,
                                     List<ParameterElement> parameters,
                                     PersistentEntityRoot<T> root,
                                     PersistentEntityCriteriaDelete<T> query,
                                     SourcePersistentEntityCriteriaBuilder cb) {
        ParameterElement entityParameter = getEntityParameter();
        if (entityParameter == null) {
            entityParameter = getEntitiesParameter();
        }
        Predicate predicate;
        if (entityParameter != null) {
            final SourcePersistentEntity rootEntity = (SourcePersistentEntity) root.getPersistentEntity();
            if (rootEntity.getVersion() != null) {
                predicate = cb.and(
                    cb.equal(root.id(), cb.entityPropertyParameter(entityParameter, new PersistentPropertyPath(rootEntity.getIdentity()))),
                    cb.equal(root.version(), cb.entityPropertyParameter(entityParameter, new PersistentPropertyPath(rootEntity.getVersion())))
                );
            } else {
                boolean generateInIdList = getEntitiesParameter() != null
                    && !rootEntity.hasCompositeIdentity()
                    && !rootEntity.getIdentity().isEmbedded();
                if (generateInIdList) {
                    predicate = root.id().in(cb.entityPropertyParameter(entityParameter, null));
                } else {
                    predicate = cb.equal(root.id(), cb.entityPropertyParameter(entityParameter, null));
                }
            }
        } else {
            predicate = extractPredicates(parameters, root, cb);
        }
        predicate = interceptPredicate(matchContext, List.of(), root, cb, predicate);
        if (predicate != null) {
            query.where(predicate);
        }
    }

    /**
     * Apply predicates.
     *
     * @param matchContext  The matchContext
     * @param querySequence The query sequence
     * @param parameters    The parameters
     * @param root          The root
     * @param query         The query
     * @param cb            The criteria builder
     * @param <T>           The entity type
     */
    private <T> void applyPredicates(MethodMatchContext matchContext,
                                     String querySequence,
                                     ParameterElement[] parameters,
                                     PersistentEntityRoot<T> root,
                                     PersistentEntityCriteriaDelete<T> query,
                                     SourcePersistentEntityCriteriaBuilder cb) {
        Iterator<ParameterElement> parametersIterator = Arrays.asList(parameters).iterator();
        Predicate predicate = extractPredicates(querySequence, parametersIterator, root, cb);
        List<ParameterElement> remainingParameters = new ArrayList<>(parameters.length);
        while (parametersIterator.hasNext()) {
            remainingParameters.add(parametersIterator.next());
        }
        predicate = interceptPredicate(matchContext, remainingParameters, root, cb, predicate);
        if (predicate != null) {
            query.where(predicate);
        }
    }

    /**
     * Apply projections.
     *
     * @param projection The querySequence
     * @param root       The root
     * @param query      The query
     * @param cb         The criteria builder
     * @param <T>        The entity type
     */
    protected <T> void applyProjections(String projection,
                                        PersistentEntityRoot<T> root,
                                        PersistentEntityCriteriaDelete<T> query,
                                        PersistentEntityCriteriaBuilder cb) {
        if (!isReturning) {
            return;
        }
        List<Selection<?>> selections = findSelections(projection, root, cb, null);
        if (selections.isEmpty()) {
            query.returning(root);
        } else if (selections.size() == 1) {
            query.returning((Selection<? extends T>) selections.get(0));
        } else {
            throw new MatchFailedException("Multi-selection is not supported");
        }
    }

    @Override
    protected MethodMatchInfo build(MethodMatchContext matchContext) {

        MethodMatchSourcePersistentEntityCriteriaBuilderImpl cb = new MethodMatchSourcePersistentEntityCriteriaBuilderImpl(matchContext);

        PersistentEntityCriteriaDelete<Object> criteriaQuery = cb.createCriteriaDelete(null);
        PersistentEntityRoot<Object> root = criteriaQuery.from(matchContext.getRootEntity());

        apply(matchContext, root, criteriaQuery, cb);

        FindersUtils.InterceptorMatch interceptorMatch = resolveReturnTypeAndInterceptor(matchContext);
        ClassElement resultType = interceptorMatch.returnType();
        ClassElement interceptorType = interceptorMatch.interceptor();

        boolean optimisticLock = ((AbstractPersistentEntityCriteriaDelete<?>) criteriaQuery).hasVersionRestriction();

        final AnnotationMetadataHierarchy annotationMetadataHierarchy = new AnnotationMetadataHierarchy(
            matchContext.getRepositoryClass().getAnnotationMetadata(),
            matchContext.getAnnotationMetadata()
        );

        MethodResult result = analyzeMethodResult(
            matchContext,
            resultType,
            interceptorMatch,
            true
        );

        if (result.isDto() && !result.isRuntimeDtoConversion()) {
            List<SourcePersistentProperty> dtoProjectionProperties = getDtoProjectionProperties(matchContext, matchContext.getRootEntity(), resultType);
            if (!dtoProjectionProperties.isEmpty()) {
                List<Selection<?>> selectionList = dtoProjectionProperties.stream()
                    .map(p -> {
                        if (matchContext.getQueryBuilder().shouldAliasProjections()) {
                            return root.get(p.getName()).alias(p.getName());
                        } else {
                            return root.get(p.getName());
                        }
                    })
                    .collect(Collectors.toList());
                criteriaQuery.returningMulti(
                    selectionList
                );
            }
        }

        QueryBuilder queryBuilder = matchContext.getQueryBuilder();
        QueryResult queryResult = ((QueryResultPersistentEntityCriteriaQuery) criteriaQuery).buildQuery(annotationMetadataHierarchy, queryBuilder);

        return new MethodMatchInfo(
            getOperationType(),
            resultType,
            interceptorType
        )
            .optimisticLock(optimisticLock)
            .queryResult(queryResult);
    }

    @Override
    protected DataMethod.OperationType getOperationType() {
        if (isReturning) {
            return DataMethod.OperationType.DELETE_RETURNING;
        }
        return DataMethod.OperationType.DELETE;
    }
}
